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
#   a count with >9 digits      RED  "implausibly large"    <- would WRAP silently
#   total not a number          IMPOSSIBLE by construction — see 10# below
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
#       fail-OPEN behaviour.
#   v3  `$((total + n))` reads a LEADING ZERO as octal. tests="008" is not valid
#       octal, so the arithmetic aborted the whole script mid-loop and it
#       exited 0 — with a second task's missing results directory never checked.
#       Fail-OPEN again, and the quieter half is worse: tests="017" printed a
#       green "15". The `case` filter accepts it because it IS all digits; base
#       is a separate question from character class. Hence `10#` below.
# Every one of those passed a review or a control set before it was found.
set -uo pipefail

# ⚠️ C LOCALE IS LOAD-BEARING. The digit filter below is `case *[!0-9]*`, and
# `[!0-9]` is a COLLATION range: under some locales it does not exclude
# non-ASCII digits, and bash 3.2 — which is what the apple runner runs — has no
# `globasciiranges` to force ASCII semantics. A Unicode digit slipping through
# reaches `$(( ))` and detonates the same fatal-unwind as v3 below.
export LC_ALL=C

RESULTS_DIR="hauler/build/test-results"
status=0

# ⚠️ COMPLETENESS TRAP. v3 proved that a fatal arithmetic expansion unwinds out
# of BOTH loops and exits 0, leaving every later task UNEXAMINED and reported as
# a pass. No amount of per-branch care prevents that, because the script never
# reaches its own checks. So the count of tasks actually examined is asserted on
# the way out, from a trap that fires however we leave.
#   Tasks not examined are NOT tasks that passed.
TOTAL_TASKS=$#
checked=0
# shellcheck disable=SC2329  # invoked indirectly via `trap ... EXIT`
on_exit() {
  exit_rc=$?
  if [ "$checked" -ne "$TOTAL_TASKS" ]; then
    echo "::error::assert-tests-executed.sh examined only $checked of $TOTAL_TASKS task(s) before exiting."
    echo "::error::The run did not complete. Tasks not examined are NOT tasks that passed."
    exit 1
  fi
  exit "$exit_rc"
}
trap on_exit EXIT

if [ "$#" -eq 0 ]; then
  echo "::error::assert-tests-executed.sh called with no task names — nothing was asserted."
  echo "::error::An assertion over an empty set is not a pass."
  exit 1
fi

for task in "$@"; do
  checked=$((checked + 1))
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
  oversize=0
  total=0
  for f in "$dir"/*.xml; do
    [ -e "$f" ] || continue
    files=$((files + 1))
    # head -1: Gradle writes exactly one <testsuite> per file, and it precedes
    # <system-out>. Without this, a test that LOGS a testsuite-shaped line gets
    # summed out of a CDATA block — reproduced: a real tests="0" reported 999.
    # ⚠️ TWO GREEDY-REGEX FAIL-OPENS LIVED HERE. BRE `.*` is greedy, so on a
    # single line it matched the LAST candidate, not the opening tag:
    #   <testsuite tests="0" othertests="777"/>            reported 777
    #   <testsuite tests="3">...CDATA <testsuite tests="999"/>...   reported 999
    # `head -1` only helped when the decoy was on a later LINE. Two defences:
    #   s/>.*//          keep only the opening tag, killing same-line decoys
    #   [[:space:]]tests= require an attribute boundary, killing `othertests`
    #   NOTE the literal has NO trailing space: requiring `<testsuite ` and then
    #   a separate [[:space:]] rejected `<testsuite tests="7" name="z"/>`, where
    #   tests is the FIRST attribute. Caught by control, not by reading.
    n=$(sed -n 's/>.*//; s/.*<testsuite[^>]*[[:space:]]tests="\([0-9][0-9]*\)".*/\1/p' "$f" | head -1)
    case "$n" in
      "" | *[!0-9]*) continue ;;
    esac
    # ⚠️ bash arithmetic is 64-bit and WRAPS SILENTLY:
    # tests="99999999999999999999" became 7766279631452241919. A plausible
    # wrong number is worse than zero, because zero at least looks wrong.
    # No suite has a billion tests; refuse rather than wrap.
    if [ "${#n}" -gt 9 ]; then
      oversize=$((oversize + 1))
      continue
    fi
    parsed=$((parsed + 1))
    # ⚠️ `10#` IS LOAD-BEARING. Without it bash reads a leading zero as octal:
    # tests="008" aborts the script mid-loop (exit 0, later tasks unchecked)
    # and tests="017" silently becomes 15. Both are fail-OPEN.
    total=$((total + 10#$n))
  done

  if [ "$oversize" -gt 0 ]; then
    echo "::error::$task: $oversize file(s) report an implausibly large tests=\"N\" (>9 digits)."
    echo "::error::Refusing rather than wrapping — bash arithmetic is 64-bit and wraps SILENTLY."
    status=1
    continue
  fi

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
