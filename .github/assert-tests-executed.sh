#!/usr/bin/env bash
# Assert that each named Gradle test task actually EXECUTED tests.
#
# ⚠️ WHY THIS EXISTS. A task existing, being invoked, and executing are three
# different facts, and hauler has the canonical example: Kotlin DISABLES
# macosX64Test/iosX64Test at configuration time on an arm64 host. It prints a
# warning and the job stays green. `BUILD SUCCESSFUL` is not evidence that
# anything ran — the count is.
#
# ⚠️ IT MUST FAIL CLOSED, AND SAY WHY. This asserts a NEGATIVE ("no suite ran
# zero tests"), the shape that can be satisfied by an ABSENCE OF INFORMATION
# rather than by a fact (#52). Every state below is distinct and only one is
# green — a red that misdescribes its cause costs the next reader the same
# hours the silence would have.
#
#   no task names given         RED  "nothing was asserted"
#   no results directory        RED  "cannot determine whether it ran"
#   directory but no XML        RED  "did not report at all"
#   some files unparseable      RED  "partial parse"        <- NOT a subtotal
#   no file parseable           RED  "parser failure"       <- NOT "zero tests"
#   total not a number          RED  "derivation failed"    <- NOT green
#   total == 0                  RED  "executed zero"
#   total > 0                   green, count printed
#
# ⚠️ THIS FILE HAS SHIPPED FOUR DEFECTS OF ITS OWN. Read before editing:
#   v1  `sed \+` (GNU-only) + `paste -sd+` (BSD paste needs `-`) + `bc` (absent
#       on Git for Windows). paste errored first and MASKED the other two.
#   v1  `total=${total:-0}` turned a FAILED MEASUREMENT into the number 0.
#       543 real tests reported as zero, pointing the reader at an empty suite.
#   v1  "directory contains no XML" reported as "tests=0" — right colour,
#       wrong cause.
#   v2  `[ "$total" -eq 0 ]` exits 2 on a NON-NUMERIC operand, so the `if` was
#       false and control fell through to the success line: a failed awk
#       printed a green with no number in it and exited 0. Fail-closed contract,
#       fail-OPEN behaviour. Hence the explicit numeric check below.
# Every one of those passed a review or a control set before it was found.
set -uo pipefail

RESULTS_DIR="hauler/build/test-results"
status=0

if [ "$#" -eq 0 ]; then
  echo "::error::assert-tests-executed.sh called with no task names — nothing was asserted."
  echo "::error::An assertion over an empty set is not a pass."
  exit 1
fi

for task in "$@"; do
  dir="$RESULTS_DIR/$task"

  if [ ! -d "$dir" ]; then
    echo "::error::$task: no results directory at $dir — cannot determine whether it ran."
    echo "::error::This is NOT a pass. A suite whose count cannot be read is a suite that did not report."
    status=1
    continue
  fi

  # ⚠️ No arrays and no `nullglob`: expanding an empty array under `set -u`
  # errors as "unbound variable" before bash 4.4, and the `apple` leg runs
  # macOS bash 3.2.57. That would abort one line ABOVE the branch that exists
  # to report it — red for the wrong reason, which this file treats as a defect.
  # Nobody who has looked has a bash 3.2 to test on, so the construct is
  # removed rather than argued about.
  files=0
  parsed=0
  total=0
  for f in "$dir"/*.xml; do
    [ -e "$f" ] || continue
    files=$((files + 1))
    # head -1: Gradle writes exactly one <testsuite> per file, and it precedes
    # <system-out>. Without this, a test that LOGS a testsuite-shaped line gets
    # summed out of a CDATA block — reproduced: a real tests="0" reported 999.
    n=$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "$f" | head -1)
    case "$n" in
      "" | *[!0-9]*) continue ;;
    esac
    parsed=$((parsed + 1))
    total=$((total + n))
  done

  if [ "$files" -eq 0 ]; then
    echo "::error::$task: results directory exists but contains NO XML — the task produced no report."
    echo "::error::Distinct from 'ran zero tests': this is 'did not report at all'."
    status=1
    continue
  fi

  if [ "$parsed" -eq 0 ]; then
    echo "::error::$task: $files XML file(s) present, NONE parseable for tests=\"N\"."
    echo "::error::This is a PARSER failure, not a zero-test run. Fix this script, not the suite."
    status=1
    continue
  fi

  # ⚠️ A SUBTOTAL IS NOT A TOTAL. If some files parsed and others did not, the
  # sum is a partial measurement wearing the face of a complete one — the exact
  # defect this script exists to prevent, inside the script. Refuse it.
  if [ "$parsed" -ne "$files" ]; then
    echo "::error::$task: only $parsed of $files XML file(s) yielded a tests=\"N\" count."
    echo "::error::PARTIAL PARSE. The $total below is a subtotal, not a total; do not report it as one."
    status=1
    continue
  fi

  if [ "$total" -eq 0 ]; then
    echo "::error::$task: $files file(s) parsed cleanly and report ZERO tests executed."
    echo "::error::Do not 'fix' this by removing the assertion — find out why the suite is empty."
    status=1
    continue
  fi

  echo "$task: $total tests executed (from $files result file(s))"
done

exit $status
