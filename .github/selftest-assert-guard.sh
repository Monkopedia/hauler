#!/usr/bin/env bash
# TEMPORARY — belongs to the throwaway PR for hauler #53. Do not merge.
#
# Drives every RED branch of assert-tests-executed.sh on the runner it is
# invoked from, and asserts the emitted MESSAGE, not the exit code.
#
# ⚠️ WHY THE MESSAGE. A red job cannot distinguish "the branch fired correctly"
# from "the interpreter died one line earlier". Both are red. On macOS that is
# not hypothetical: /bin/bash is 3.2.57, and expanding an empty array under
# `set -u` is an "unbound variable" error before bash 4.4 — which would abort
# one line ABOVE the message the branch exists to print.
set -uo pipefail

G=./.github/assert-tests-executed.sh
D=hauler/build/test-results/zzSelfTest
fails=0

echo "interpreter: ${BASH_VERSION:-<not bash>}   ($(command -v bash))"
bash --version | head -1
echo

expect() {                       # expect <label> <want-rc> <want-substring>
  local label=$1 want_rc=$2 want=$3 out rc
  out=$("$G" zzSelfTest 2>&1); rc=$?
  if [ "$rc" -ne "$want_rc" ]; then
    echo "FAIL  $label: rc=$rc want=$want_rc"; echo "      out: $out"; fails=$((fails + 1)); return
  fi
  case "$out" in
    *"$want"*) echo "ok    $label  (rc=$rc)  <- $want" ;;
    *) echo "FAIL  $label: rc correct but WRONG MESSAGE"; echo "      want substring: $want"; echo "      got: $out"; fails=$((fails + 1)) ;;
  esac
}

rm -rf "$D"
# 1. no results directory
out=$("$G" zzSelfTest 2>&1); rc=$?
case "$rc:$out" in
  1:*"cannot determine whether it ran"*) echo "ok    no-directory  (rc=1)" ;;
  *) echo "FAIL  no-directory: rc=$rc out=$out"; fails=$((fails + 1)) ;;
esac

# 2. directory, no XML  <- the bash 3.2 empty-glob branch
mkdir -p "$D"
expect "no-XML" 1 "did not report at all"

# 3. XML present, nothing parseable
printf '<testsuite name="x"/>\n' > "$D/a.xml"
expect "parser-failure" 1 "NONE parseable"

# 4. partial parse
printf '<testsuite name="y" tests="5"/>\n' > "$D/b.xml"
expect "partial-parse" 1 "PARTIAL PARSE"

# 5. implausibly large
rm -f "$D/a.xml"; printf '<testsuite name="y" tests="9999999999"/>\n' > "$D/b.xml"
expect "oversize" 1 "implausibly large"

# 6. zero tests
printf '<testsuite name="y" tests="0"/>\n' > "$D/b.xml"
expect "zero-tests" 1 "ZERO tests executed"

# 7. octal-looking count must not wrap or abort
printf '<testsuite name="y" tests="017"/>\n' > "$D/b.xml"
expect "leading-zero" 0 "17 tests executed"

# 8. green
printf '<testsuite name="y" tests="42"/>\n' > "$D/b.xml"
expect "green" 0 "42 tests executed"

# 9. no arguments
out=$("$G" 2>&1); rc=$?
case "$rc:$out" in
  1:*"nothing was asserted"*) echo "ok    no-args  (rc=1)" ;;
  *) echo "FAIL  no-args: rc=$rc out=$out"; fails=$((fails + 1)) ;;
esac

rm -rf "$D"
echo
if [ "$fails" -ne 0 ]; then
  echo "::error::guard self-test: $fails state(s) wrong on this runner."
  exit 1
fi
echo "guard self-test: all states correct on this runner"
