#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Counts the javadoc warnings of the published modules (doclint all, as in the release profile); fails on any.
# Usage: scripts/check-javadoc.sh
# Requires: bash, mvn (3.9+), a JDK with javadoc on the PATH or in JAVA_HOME.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
MODULES=(uom-core uom)
JAVADOC=${JAVA_HOME:+$JAVA_HOME/bin/}javadoc

fail() { echo "check-javadoc: FAIL: $*" >&2; exit 1; }

log=$(mktemp)
trap 'rm -f "$log"' EXIT
# clean: the plugin skips an unchanged module; debug keeps its argument files so that the rerun below
# reports past javadoc's default limit of 100 warnings
mvn -B -f "$ROOT/pom.xml" -Prelease -DskipTests -Dgpg.skip=true -Djavadoc.failOnWarnings=false -Ddebug=true \
  -pl "$(IFS=,; echo "${MODULES[*]}")" -am clean package > "$log" 2>&1 || { cat "$log" >&2; fail "release build failed"; }

total=0
for m in "${MODULES[@]}"; do
  dir=$ROOT/$m/target/reports/apidocs
  [[ -f $dir/options ]] || fail "$m: no javadoc options file in $dir"
  out=$(cd "$dir" && "$JAVADOC" @options @packages @argfile -Xmaxwarns 100000 2>&1) || { echo "$out" >&2; fail "$m: javadoc failed"; }
  n=$(grep -c ': warning: ' <<< "$out" || true)
  grep ': warning: ' <<< "$out" >&2 || true
  echo "check-javadoc: $m: $n warnings"
  total=$((total + n))
done
[[ $total -eq 0 ]] || fail "$total javadoc warnings"
echo "check-javadoc: OK"
