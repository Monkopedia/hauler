#!/usr/bin/env python3
"""Drive every refusal branch of assert_tests_executed.py and assert its MESSAGE.

WHY THIS EXISTS (hauler #55). A red job cannot distinguish the branch firing
correctly from the interpreter, the path layer or the encoder dying one line
earlier. Both are red. Both look like the guard working. This repo has shipped
that exact confusion four times -- right colour, wrong cause -- so the only
useful assertion is on the TEXT the branch itself prints.

WHY IT RUNS ON THREE RUNNERS. The happy path is proven on ubuntu, macOS and
Windows (matching counts on all five legs). The refusal paths are proven on
ubuntu only. They are the branches that touch the FILESYSTEM, and Windows is
where that differs: Git Bash hands out /c/Users/... style paths and Python
resolves them through a different layer. `no results directory` in particular
could refuse for the wrong reason and look like a correct refusal.

It also asserts NO case reports a crash. The guard's own messages contain a
non-ASCII em dash; a console codepage that cannot encode it would raise inside
the print, hit the top-level handler, and still exit 1 -- a correct-looking red
carrying the wrong text. That is a Windows-shaped failure and it is invisible to
an exit-code check.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

GUARD = Path(__file__).resolve().parent / "assert_tests_executed.py"
RESULTS = Path("hauler/build/test-results")

SUITE_OK = '<testsuite name="s" tests="3" failures="0"></testsuite>'
SUITE_ZERO = '<testsuite name="s" tests="0" failures="0"></testsuite>'
SUITE_TORN = '<testsuite name="s" tests="3"'  # truncated: unparseable
SUITE_NOATTR = '<testsuite name="s" failures="0"></testsuite>'

# A literal, so that deleting a case is a RED rather than a smaller denominator.
EXPECTED_STATES = 11


def write(root: Path, task: str, files: dict) -> None:
    d = root / RESULTS / task
    d.mkdir(parents=True, exist_ok=True)
    for name, body in files.items():
        (d / name).write_text(body, encoding="utf-8")


CASES = []


def case(name, expect, rc=1, mutate=None):
    def deco(fn):
        CASES.append({"name": name, "expect": expect, "rc": rc,
                      "setup": fn, "mutate": mutate})
        return fn
    return deco


@case("no results directory",
      # The em dash is deliberate: it is the only non-ASCII character the guard
      # emits, and without it in an assertion a mojibake'd message still
      # matched every ASCII substring.
      ["no results directory at", "\u2014 cannot determine whether it ran", "NOT a pass"])
def _(root):
    (root / RESULTS).mkdir(parents=True, exist_ok=True)
    return ["ghostTask"]


@case("directory but no XML", ["contains NO XML", "did not report at all"])
def _(root):
    (root / RESULTS / "emptyTask").mkdir(parents=True, exist_ok=True)
    return ["emptyTask"]


@case("all XML unparseable", ["NONE parseable", "PARSER failure"])
def _(root):
    write(root, "tornTask", {"a.xml": SUITE_TORN, "b.xml": SUITE_TORN})
    return ["tornTask"]


@case("partial parse", ["only 1 of 2", "PARTIAL PARSE"])
def _(root):
    write(root, "partialTask", {"a.xml": SUITE_OK, "b.xml": SUITE_TORN})
    return ["partialTask"]


@case("tests= present but not an integer", ["is not a plain non-negative integer"])
def _(root):
    write(root, "bogusTask", {"a.xml": '<testsuite name="s" tests="0x10"></testsuite>'})
    return ["bogusTask"]


@case("no tests= attribute at all", ["no <testsuite tests=...> attribute"])
def _(root):
    write(root, "noattrTask", {"a.xml": SUITE_NOATTR})
    return ["noattrTask"]


@case("parsed cleanly, zero tests", ["report ZERO tests executed"])
def _(root):
    write(root, "zeroTask", {"a.xml": SUITE_ZERO})
    return ["zeroTask"]


@case("no task names given", ["called with no task names", "empty set is not a pass"])
def _(root):
    return []


@case("phantom THEN good task -- the v3 unwind shape",
      ["no results directory at", "3 tests executed"])
def _(root):
    write(root, "goodTask", {"a.xml": SUITE_OK})
    # FAILING TASK FIRST, deliberately. With the good task first, a guard that
    # aborts the whole run at the first failure emits both messages and exits 1
    # -- indistinguishable from one that examines every task. Verified: that
    # mutation passed this case in its original order. Only the phantom-first
    # order proves later tasks are still examined.
    return ["phantomTask", "goodTask"]


@case("guard CRASHES -- proves the crash detector can fire",
      ["crashed:", "A crash is not a pass"], rc=1, mutate="raise")
def _(root):
    # THE DETECTOR HAD NEVER FIRED. `"crashed:" in out` is this harness's
    # central negative assertion, and nothing drove the guard's top-level
    # handler -- so renaming that marker left every case green. Nothing the
    # guard can be FED reaches it: ET.ParseError and OSError are both caught
    # inside check(), a directory named *.xml is filtered by is_file(), and a
    # NUL in a task name cannot survive execve.
    #
    # So this case runs a COPY of the real guard with a `raise` injected into
    # check(). That proves two things a green run otherwise assumes: the
    # guard's own handler converts an unexpected exception into a refusal
    # rather than a pass, and this harness notices when it does not.
    write(root, "goodTask", {"a.xml": SUITE_OK})
    return ["goodTask"]


@case("happy path", ["3 tests executed"], rc=0)
def _(root):
    write(root, "goodTask", {"a.xml": SUITE_OK})
    return ["goodTask"]


def main() -> int:
    print(f"guard under test: {GUARD}")
    print(f"python: {sys.version.split()[0]}  platform: {sys.platform}")
    if not GUARD.is_file():
        print(f"::error::self-test cannot find the guard at {GUARD}")
        return 1

    driven, failures = [], []
    for c in CASES:
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as td:
            root = Path(td)
            args = c["setup"](root)
            guard = GUARD
            if c["mutate"] == "raise":
                guard = root / "mutated_guard.py"
                src = GUARD.read_text(encoding="utf-8")
                marker = "    d = RESULTS / task\n"
                assert src.count(marker) == 1, "mutation anchor moved"
                guard.write_text(
                    src.replace(marker, marker + '    raise RuntimeError("injected")\n'),
                    encoding="utf-8")
            try:
                p = subprocess.run(
                    [sys.executable, str(guard), *args],
                    cwd=root, capture_output=True, text=True,
                    # STRICT, not "replace". The guard emits a non-ASCII em
                    # dash; a runner whose stdout encoding mangles it (Windows
                    # cp1252 encodes U+2014 as 0x97) produced bytes that
                    # "replace" silently turned into U+FFFD while every ASCII
                    # assertion still matched -- green, on the exact scenario
                    # this file exists to test. Strict raises instead.
                    encoding="utf-8", errors="strict",
                )
                out = p.stdout + p.stderr
                rc = p.returncode
            except UnicodeDecodeError as e:
                out, rc = f"UNDECODABLE OUTPUT: {e}", -1
        driven.append(c["name"])
        problems = []
        if rc != c["rc"]:
            problems.append(f"exit {rc}, expected {c['rc']}")
        for want in c["expect"]:
            if want not in out:
                problems.append(f"missing text: {want!r}")
        # The guard prints refusals as GitHub annotations. Without this, a
        # traceback that merely ECHOES the source line containing the message
        # literal satisfied "message asserted" for a guard that printed nothing.
        if c["rc"] != 0 and "::error::" not in out:
            problems.append("no ::error:: annotation -- message may be a traceback echo")
        # A red for the WRONG reason is the failure this file exists to catch --
        # except in the case that deliberately induces one.
        if c["mutate"] != "raise" and "crashed:" in out:
            problems.append("guard CRASHED -- red for the wrong reason")
        if problems:
            failures.append((c["name"], problems, out.strip()))
            print(f"FAIL  {c['name']}")
            for pr in problems:
                print(f"        {pr}")
            print("      --- actual output ---")
            for line in out.strip().splitlines():
                print(f"      | {line}")
        else:
            print(f"ok    {c['name']}  (exit {rc}, message asserted)")

    # Against a LITERAL constant, not len(CASES). Comparing a counter to the
    # length of the list it iterates is not a check -- both move together, so
    # deleting a case left the old version reporting "9 of 9 ... All 9 states
    # behaved as specified" and exit 0.
    print(f"\n{len(driven)} of {EXPECTED_STATES} states driven; {len(failures)} failed.")
    if len(driven) != EXPECTED_STATES:
        missing = set(c["name"] for c in CASES) - set(driven)
        print(f"::error::self-test drove {len(driven)} of {EXPECTED_STATES} expected states.")
        print(f"::error::Not driven: {', '.join(sorted(missing)) or '(cases were removed from the file)'}")
        print("::error::States not driven are NOT states that passed.")
        return 1
    if failures:
        print(f"::error::{len(failures)} refusal branch(es) did not behave as specified "
              f"on {sys.platform}.")
        return 1
    print(f"All {EXPECTED_STATES} states behaved as specified on {sys.platform}.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print(f"::error::selftest_assert_guard.py crashed: {type(e).__name__}: {e}")
        print("::error::A crash is not a pass.")
        sys.exit(1)
