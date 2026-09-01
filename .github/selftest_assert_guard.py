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

# This file ECHOES the guard's output, em dash included, so it needs the same
# treatment as the guard: on cp1252 it printed the correct finding and then died
# with UnicodeEncodeError while reporting it -- the bug it was written to catch,
# in the reporter.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):  # pragma: no cover
        pass

GUARD = Path(__file__).resolve().parent / "assert_tests_executed.py"
RESULTS = Path("hauler/build/test-results")

SUITE_OK = '<testsuite name="s" tests="3" failures="0"></testsuite>'
SUITE_ZERO = '<testsuite name="s" tests="0" failures="0"></testsuite>'
SUITE_TORN = '<testsuite name="s" tests="3"'  # truncated: unparseable
SUITE_NOATTR = '<testsuite name="s" failures="0"></testsuite>'
# EVERY fixture above is a single bare <testsuite> root. That is why a guard with
# skip-deduction removed, and one that counts only the first suite, both passed
# this harness green -- the shapes those defects live in were never fed to it.
# A refusal-test whose fixtures cannot express a defect proves the guard is
# UNCHANGED, not that it is correct.
SUITES_MULTI = ('<testsuites><testsuite name="a" tests="4"/>'
                '<testsuite name="b" tests="6"/></testsuites>')
SUITES_ONE_NOATTR = ('<testsuites><testsuite name="a" tests="4"/>'
                     '<testsuite name="b"/></testsuites>')
SUITE_SKIPPED_SOME = '<testsuite name="s" tests="181" skipped="3"/>'
SUITE_SKIPPED_ALL = '<testsuite name="s" tests="5" skipped="5"/>'
SUITE_NEGATIVE = '<testsuite name="s" tests="-1"/>'
# The last two refusal branches with no case. Both let a broken guard exit 0:
# with `skipped > ran` removed the guard printed "-5 tests executed" and passed,
# and a NEGATIVE total is the specific hazard _count's own docstring records.
SUITE_SKIP_EXCEEDS = '<testsuite name="s" tests="5" skipped="10"/>'
SUITE_IMPLAUSIBLE = '<testsuite name="s" tests="99999999999"/>'

# A literal, so that deleting a case is a RED rather than a smaller denominator.
EXPECTED_STATES = 18


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
      ["no results directory at", " 3 tests executed"])
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


@case("multi-suite file is SUMMED, not truncated to the first",
      [" 10 tests executed"], rc=0)
def _(root):
    write(root, "multiTask", {"a.xml": SUITES_MULTI})
    return ["multiTask"]


@case("skipped testcases are DEDUCTED from tests=",
      [" 178 tests executed"], rc=0)
def _(root):
    write(root, "skipTask", {"a.xml": SUITE_SKIPPED_SOME})
    return ["skipTask"]


@case("a suite where EVERY test is skipped is not a pass",
      ["report ZERO tests executed"])
def _(root):
    write(root, "allSkipTask", {"a.xml": SUITE_SKIPPED_ALL})
    return ["allSkipTask"]


@case("a sibling suite with no tests= poisons the file",
      ["no tests= attribute", "subtotal"])
def _(root):
    write(root, "dropTask", {"a.xml": SUITES_ONE_NOATTR})
    return ["dropTask"]


@case("negative tests= is refused, not summed",
      ["is not a plain non-negative integer"])
def _(root):
    write(root, "negTask", {"a.xml": SUITE_NEGATIVE})
    return ["negTask"]


# CONVENTION, stated because two adjacent cases briefly disagreed: expect strings
# assert MESSAGE CONTENT, never rendering. The trailing "\n" this case carried for
# one commit came from err()'s print(), not from the guard's string -- so it pinned
# the assertion to "bad entries are printed one per line" and would have broken on
# an unrelated change to how `bad` is joined. The substring risk it guarded against
# (matching a hypothetical "tests=50") is unreachable: SUITE_SKIP_EXCEEDS pins
# tests="5", so `ran` is always 5. It bought no discrimination and cost coupling.
# Count expects ARE anchored with a leading space -- there the risk is real, because
# totals are computed and " 3 tests executed" genuinely can appear inside
# "103 tests executed".
@case("skipped greater than tests is refused, not summed as negative",
      ["skipped=10 exceeds tests=5"])
def _(root):
    write(root, "skipExceedsTask", {"a.xml": SUITE_SKIP_EXCEEDS})
    return ["skipExceedsTask"]


@case("an implausible count is refused rather than reported",
      ["is implausible; refusing"])
def _(root):
    write(root, "hugeTask", {"a.xml": SUITE_IMPLAUSIBLE})
    return ["hugeTask"]


@case("happy path", [" 3 tests executed"], rc=0)
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
            # BYTES, decoded here rather than by subprocess. With text=True the
            # decode happens on subprocess's reader THREAD: a UnicodeDecodeError
            # there kills the thread, leaves stdout as None, and surfaces as
            # "TypeError: NoneType + str" -- a headline that says nothing about
            # encoding, four lines below the real finding. Decoding at the call
            # site turns the same event into a reportable result.
            p = subprocess.run([sys.executable, str(guard), *args],
                               cwd=root, capture_output=True)
            rc = p.returncode
            raw = p.stdout + p.stderr
            mojibake = None
            try:
                out = raw.decode("utf-8", "strict")
            except UnicodeDecodeError as e:
                mojibake = e
                out = raw.decode("utf-8", "replace")
        driven.append(c["name"])
        problems = []
        if mojibake is not None:
            problems.append(
                f"guard emitted non-UTF-8 output ({mojibake.reason} at byte "
                f"{mojibake.start}) -- its messages are MANGLED on this runner, "
                "which is silent in a green run")
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
