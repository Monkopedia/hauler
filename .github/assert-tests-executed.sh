#!/usr/bin/env bash
# Assert that each named Gradle test task actually EXECUTED tests.
#
# ⚠️ WHY THIS EXISTS. A task existing, being invoked, and executing are three
# different facts, and hauler has the canonical example: Kotlin DISABLES
# macosX64Test/iosX64Test at configuration time on an arm64 host. It prints a
# warning and the job stays green. `BUILD SUCCESSFUL` is therefore not evidence
# that anything ran — the count is.
#
# ⚠️ AND IT MUST FAIL CLOSED. This asserts a NEGATIVE ("no suite ran zero
# tests"), which is the shape that can be satisfied by an ABSENCE OF
# INFORMATION rather than by a fact (hauler #52). So the three states are kept
# distinct and only one of them passes:
#
#   no results directory / no XML   -> RED, "could not determine"  (not "fine")
#   XML present, tests=0            -> RED, "executed zero"
#   XML present, tests>0            -> green, and the count is printed
#
# A version of this script that cannot see the counts does NOT pass.
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

  # Distinguish "no XML" from "XML says zero". Both are RED, but they mean
  # different things and the message has to say which — a guard whose failure
  # text is wrong is halfway back to the silence it replaced.
  shopt -s nullglob
  xml=("$dir"/*.xml)
  shopt -u nullglob
  if [ ${#xml[@]} -eq 0 ]; then
    echo "::error::$task: results directory exists but contains NO XML — the task produced no report."
    echo "::error::Distinct from 'ran zero tests': this is 'did not report at all'."
    status=1
    continue
  fi

  # sed, not grep: a grep matching nothing exits 1, and under a pipeline that
  # reads as a script error rather than as "zero tests".
  total=$(cat "${xml[@]}" | sed -n 's/.*<testsuite [^>]*tests="\([0-9]\+\)".*/\1/p' | paste -sd+ | bc 2>/dev/null)
  total=${total:-0}

  if [ "$total" -eq 0 ]; then
    echo "::error::$task: XML present but reports ZERO tests executed."
    echo "::error::Do not 'fix' this by removing the assertion — find out why the suite is empty."
    status=1
    continue
  fi

  echo "$task: $total tests executed"
done

exit $status
