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
# rather than by a fact (hauler #52). Four states, all distinct, only one green:
#
#   no results directory        RED  "cannot determine whether it ran"
#   directory but no XML        RED  "did not report at all"
#   XML present, unparseable    RED  "parser failure"      <- NOT "zero tests"
#   XML present, tests=0        RED  "executed zero"
#   XML present, tests>0        green, count printed
#
# The fourth state exists because the first version of this script did NOT have
# it: `total=${total:-0}` turned a failed extraction into the number 0, and
# **543 genuinely executed tests were reported as ZERO**, pointing the reader at
# an empty suite when the real fault was in this file. A default value on a
# failed measurement is a lie with a plausible face.
set -uo pipefail

RESULTS_DIR="hauler/build/test-results"
status=0

for task in "$@"; do
  dir="$RESULTS_DIR/$task"

  if [ ! -d "$dir" ]; then
    echo "::error::$task: no results directory at $dir — cannot determine whether it ran."
    echo "::error::This is NOT a pass. A suite whose count cannot be read is a suite that did not report."
    status=1
    continue
  fi

  shopt -s nullglob
  xml=("$dir"/*.xml)
  shopt -u nullglob
  if [ ${#xml[@]} -eq 0 ]; then
    echo "::error::$task: results directory exists but contains NO XML — the task produced no report."
    echo "::error::Distinct from 'ran zero tests': this is 'did not report at all'."
    status=1
    continue
  fi

  # ⚠️ PORTABILITY, and it is not cosmetic: this runs on macOS (BSD) and on Git
  # for Windows. The first version used `sed \+` (GNU-only), `paste -sd+` (BSD
  # paste will not read stdin without `-`) and `bc` (absent on Git for Windows).
  # THREE breakages in one pipeline, and `paste` errored first — so fixing only
  # that would have left two live defects under an error that had "gone away".
  # awk replaces all three at once and is POSIX everywhere. BRE `[0-9][0-9]*`.
  counts=$(cat "${xml[@]}" | sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p')

  if [ -z "$counts" ]; then
    echo "::error::$task: XML present but no tests=\"N\" attribute could be parsed."
    echo "::error::This is a PARSER failure, not a zero-test run. Fix this script, not the suite."
    status=1
    continue
  fi

  total=$(printf '%s\n' "$counts" | awk '{s+=$1} END {print s+0}')

  if [ "$total" -eq 0 ]; then
    echo "::error::$task: XML parsed cleanly and reports ZERO tests executed."
    echo "::error::Do not 'fix' this by removing the assertion — find out why the suite is empty."
    status=1
    continue
  fi

  echo "$task: $total tests executed"
done

exit $status
