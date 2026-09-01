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

# These messages contain a non-ASCII em dash, and Windows' default stdout
# encoding is cp1252, which encodes it as the single byte 0x97. That is not a
# crash -- it is silent mojibake in the GitHub annotation, and it went
# unnoticed on every green Windows run until a self-test decoded strictly.
# Reconfigure rather than removing the character: a guard whose refusals are
# garbled on one platform is a guard that reads as broken exactly when it is
# working.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):  # pragma: no cover - very old/odd streams
        pass


def err(msg: str) -> None:
    print(f"::error::{msg}")


MAX_PLAUSIBLE = 10_000_000


def _count(fname: str, raw: str, attr: str, bad: list):
    """Parse an XML count attribute, or record why it is unusable and return None.

    Deliberately stricter than int(): int() accepts a leading sign, so
    tests="-1" printed "-1 tests executed" and exited 0, and tests="-5" beside
    tests="5" summed to zero and redded with the WRONG message. It also accepts
    "1_8_1" and full-width digits, neither of which Gradle emits.
    """
    txt = raw.strip()
    if not txt.isascii() or not txt.isdigit():
        bad.append(f"{fname}: {attr}={raw!r} is not a plain non-negative integer")
        return None
    n = int(txt, 10)
    if n > MAX_PLAUSIBLE:
        bad.append(f"{fname}: {attr}={n} is implausible; refusing rather than reporting it")
        return None
    return n


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
        # EVERY suite in the file, not just the first. Gradle writes one suite
        # per file, but a <testsuites> wrapper is legal JUnit XML and taking
        # only the first counted 4 of 10 -- a subtotal wearing the face of a
        # total, which is the defect this script exists to refuse.
        # Direct children only: nested suites would be counted twice.
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        n = None
        missing_attr = False
        for s in suites:
            raw = s.get("tests")
            if raw is None:
                # A suite with no `tests=` inside a <testsuites> wrapper was
                # SILENTLY SKIPPED and its siblings summed as though complete --
                # 4 reported as the total for a file whose real total is unknown.
                # That is the subtotal-as-total defect again, in the very fix
                # written to close it: I summed the suites I could read and said
                # nothing about the one I could not.
                missing_attr = True
                continue
            ran = _count(f.name, raw, "tests", bad)
            if ran is None:
                n = None
                break
            # `tests` INCLUDES skipped testcases. A suite where everything is
            # @Ignore'd reports tests=181 skipped=181 and would print
            # "181 tests executed" -- the exact green-badge-over-nothing this
            # file exists to catch, read off the very attribute the docstring
            # calls the evidence.
            skip_raw = s.get("skipped", "0")
            skipped = _count(f.name, skip_raw, "skipped", bad)
            if skipped is None:
                n = None
                break
            if skipped > ran:
                bad.append(f"{f.name}: skipped={skipped} exceeds tests={ran}")
                n = None
                break
            n = (n or 0) + ran - skipped
        if missing_attr and n is not None:
            bad.append(f"{f.name}: a <testsuite> in this file has no tests= attribute; "
                       f"{n} would be a subtotal")
            n = None
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
    # In a FINALLY, because the only way to examine fewer tasks than were asked
    # for is to leave the loop by exception -- and a plain `if` after the loop
    # never runs in that case. The previous version put this check after the
    # loop, where `examined` could not differ from len(argv) by construction:
    # a completeness gate that could not fire, in the file whose subject is
    # gates that cannot fire.
    ok, examined = True, []
    try:
        for task in argv:
            ok &= check(task)
            examined.append(task)
    finally:
        if len(examined) != len(argv):
            missing = ", ".join(t for t in argv if t not in examined)
            err(f"examined only {len(examined)} of {len(argv)} task(s); never checked: {missing}")
            err("The run did not complete. Tasks not examined are NOT tasks that passed.")
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Exception as e:  # never let an unexpected failure read as success
        err(f"assert_tests_executed.py crashed: {type(e).__name__}: {e}")
        err("A crash is not a pass.")
        sys.exit(1)
