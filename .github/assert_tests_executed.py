#!/usr/bin/env python3
"""Assert that each named Gradle test task actually EXECUTED tests.

A task existing, being invoked, and executing are three different facts.
hauler has the canonical example: Kotlin DISABLES macosX64Test/iosX64Test at
configuration time on an arm64 host -- it prints a warning and the job stays
green. `BUILD SUCCESSFUL` is not evidence that anything ran; the count is.

This replaces a 179-line bash implementation that shipped SEVEN defects, four
of them fail-open, every one found only after the previous fix. Six of the
seven were properties of hand-rolling XML parsing in shell, and are gone here
by construction rather than by guard:

    greedy BRE `.*` taking the last match     -> a real parser
    a <testsuite> line logged inside CDATA    -> text nodes are not elements
    `[!0-9]` collation admitting non-ASCII    -> int() is not a glob
    `$((08))` octal-fatal, unwinding the run  -> no shell arithmetic
    64-bit silent wraparound                  -> Python ints do not wrap
    empty-glob under `set -u` on bash 3.2     -> no globs

What remains is the one thing that is genuinely this script's job: deciding
what counts as "did not report" and saying so distinctly.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESULTS = Path("hauler/build/test-results")


def err(msg: str) -> None:
    print(f"::error::{msg}")


def check(task: str) -> bool:
    """True if the task demonstrably executed tests. Every other outcome is
    an error WITH ITS OWN MESSAGE -- a red that misdescribes its cause costs
    the next reader the same hours the silence would have."""
    d = RESULTS / task
    if not d.is_dir():
        err(f"{task}: no results directory at {d} — cannot determine whether it ran.")
        err("This is NOT a pass. A suite whose count cannot be read is a suite that did not report.")
        return False

    files = sorted(p for p in d.glob("*.xml") if p.is_file())
    if not files:
        err(f"{task}: results directory exists but contains NO XML — the task produced no report.")
        err("Distinct from 'ran zero tests': this is 'did not report at all'.")
        return False

    total, parsed, bad = 0, 0, []
    for f in files:
        try:
            root = ET.parse(f).getroot()
        except (ET.ParseError, OSError) as e:
            bad.append(f"{f.name}: {e}")
            continue
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        n = None
        for s in suites:
            if s.get("tests") is not None:
                try:
                    n = int(s.get("tests"), 10)
                except ValueError:
                    bad.append(f"{f.name}: tests={s.get('tests')!r} is not an integer")
                    n = None
                break
        if n is None:
            if not bad or not bad[-1].startswith(f.name):
                bad.append(f"{f.name}: no <testsuite tests=...> attribute")
            continue
        parsed += 1
        total += n

    # A SUBTOTAL IS NOT A TOTAL. If some files parsed and others did not, the
    # sum wears the face of a complete measurement. Refuse it.
    if bad:
        if parsed == 0:
            err(f"{task}: {len(files)} XML file(s) present, NONE parseable for a tests count.")
            err("This is a PARSER failure, not a zero-test run. Fix this script, not the suite.")
        else:
            err(f"{task}: only {parsed} of {len(files)} XML file(s) yielded a count.")
            err(f"PARTIAL PARSE. {total} would be a subtotal, not a total; do not report it as one.")
        for b in bad[:5]:
            err(f"  {b}")
        return False

    if total == 0:
        err(f"{task}: {len(files)} file(s) parsed cleanly and report ZERO tests executed.")
        err("Do not 'fix' this by removing the assertion — find out why the suite is empty.")
        return False

    print(f"{task}: {total} tests executed (from {len(files)} result file(s))")
    return True


def main(argv: list) -> int:
    if not argv:
        err("assert_tests_executed.py called with no task names — nothing was asserted.")
        err("An assertion over an empty set is not a pass.")
        return 1
    # Count what was examined rather than inferring it: an unhandled failure
    # must not leave later tasks silently reported as passing.
    ok, examined = True, 0
    for task in argv:
        ok &= check(task)
        examined += 1
    if examined != len(argv):
        err(f"examined only {examined} of {len(argv)} task(s). Tasks not examined are NOT tasks that passed.")
        return 1
    return 0 if ok else 1


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Exception as e:  # never let an unexpected failure read as success
        err(f"assert_tests_executed.py crashed: {type(e).__name__}: {e}")
        err("A crash is not a pass.")
        sys.exit(1)
