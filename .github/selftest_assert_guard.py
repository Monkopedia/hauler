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


def write(root: Path, task: str, files: dict) -> None:
    d = root / RESULTS / task
    d.mkdir(parents=True, exist_ok=True)
    for name, body in files.items():
        (d / name).write_text(body, encoding="utf-8")


CASES = []


def case(name, expect, rc=1):
    def deco(fn):
        CASES.append({"name": name, "expect": expect, "rc": rc, "setup": fn})
        return fn
    return deco


@case("no results directory", ["no results directory at", "NOT a pass"])
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


@case("tests= present but not an integer", ["is not an integer"])
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


@case("good task THEN phantom -- the v3 unwind shape",
      ["3 tests executed", "no results directory at"])
def _(root):
    write(root, "goodTask", {"a.xml": SUITE_OK})
    return ["goodTask", "phantomTask"]


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

    driven, failures = 0, []
    for c in CASES:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            args = c["setup"](root)
            p = subprocess.run(
                [sys.executable, str(GUARD), *args],
                cwd=root, capture_output=True, text=True,
                encoding="utf-8", errors="replace",
            )
        out = p.stdout + p.stderr
        driven += 1
        problems = []
        if p.returncode != c["rc"]:
            problems.append(f"exit {p.returncode}, expected {c['rc']}")
        for want in c["expect"]:
            if want not in out:
                problems.append(f"missing text: {want!r}")
        # A red for the WRONG reason is the failure this file exists to catch.
        if "crashed:" in out:
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
            print(f"ok    {c['name']}  (exit {p.returncode}, message asserted)")

    # A state that never ran and a state that ran correctly are otherwise
    # indistinguishable in this output. Assert the denominator.
    print(f"\n{driven} of {len(CASES)} states driven; {len(failures)} failed.")
    if driven != len(CASES):
        print(f"::error::self-test drove only {driven} of {len(CASES)} states.")
        print("::error::States not driven are NOT states that passed.")
        return 1
    if failures:
        print(f"::error::{len(failures)} refusal branch(es) did not behave as specified "
              f"on {sys.platform}.")
        return 1
    print(f"All {len(CASES)} states behaved as specified on {sys.platform}.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print(f"::error::selftest_assert_guard.py crashed: {type(e).__name__}: {e}")
        print("::error::A crash is not a pass.")
        sys.exit(1)
