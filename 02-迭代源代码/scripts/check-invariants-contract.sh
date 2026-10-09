#!/bin/bash
# ============================================================
# Business invariants contract (INV-10 guidance reachability) - called by gate.sh
#
# Background: 2026-10-09 dead-end guidance incident - rejection message told users
# to use a feature ("ting yong") that does not exist. This script turns
# "guidance text must point to a real, reachable feature" into an automated check.
# Invariant list: docs/business/invariants.md (INV-10 / INV-11).
#
#   [A] Backend rejection messages guidance terms (qing shi yong "X" etc.,
#       incl. Java escaped quotes) -> X must exist in PC (aigicrm-ui) or MP sources.
#   [B] MP lines mentioning PC-only entry (dian nao duan) with feature term
#       in CJK brackets -> term must exist in aigicrm-ui sources.
# ============================================================
set -uo pipefail
export LC_ALL=en_US.UTF-8
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$SCRIPT_DIR/../aigicrm-backend/src/main/java"
UI="$SCRIPT_DIR/../aigicrm-ui/src"
MP="$SCRIPT_DIR/../aigicrm-miniprogram"
FAIL=0

WHITELIST=(
  'application/json|HTTP Content-Type protocol name, not a UI feature'
)

term_exists_in_frontends() {
  grep -rqF "$1" "$UI" --include='*.vue' --include='*.js' 2>/dev/null && return 0
  grep -rqF "$1" "$MP/pages" "$MP/utils" "$MP/api" --include='*.wxml' --include='*.js' 2>/dev/null && return 0
  return 1
}

is_whitelisted() {
  local entry
  for entry in "${WHITELIST[@]}"; do
    if [ "${entry%%|*}" = "$1" ]; then
      echo "    (whitelisted: ${entry#*|})"
      return 0
    fi
  done
  return 1
}

echo "--- INV-10/A: backend guidance terms reachability ---"
# Extract quoted terms after guidance verbs; three quote forms:
# Java escaped \"X\" / CJK corner brackets / CJK double quotes
TERMS=$({
  # NOTE: BSD grep ERE breaks on backslash inside a bracket class; use [^"] not [^"\]
  grep -rhoE '请(使用|改用)\\"[^"]+"' "$BACKEND" --include='*.java' 2>/dev/null | sed -E 's/^[^"]*"//; s/.?"$//'
  grep -rhoE '请(使用|改用)「[^」]+」' "$BACKEND" --include='*.java' 2>/dev/null | sed -E 's/^[^「]*「//; s/」$//'
  grep -rhoE '请(使用|改用)“[^”]+”' "$BACKEND" --include='*.java' 2>/dev/null | sed -E 's/^[^“]*“//; s/”$//'
} | sort -u)

FOUND=0
while IFS= read -r term; do
  [ -z "$term" ] && continue
  FOUND=1
  if is_whitelisted "$term"; then continue; fi
  if term_exists_in_frontends "$term"; then
    echo "  [OK] guidance term exists on PC/MP: ${term}"
  else
    echo "  [FAIL] guidance term not found on PC nor MP (dead end, INV-10): ${term}"
    FAIL=1
  fi
done <<< "$TERMS"
[ "$FOUND" -eq 0 ] && echo "  (no quoted guidance terms, skip)"

echo "--- INV-10/B: MP cross-end guidance (PC feature must exist) ---"
MP_TERMS=$(grep -rh '电脑端' "$MP/pages" --include='*.wxml' --include='*.js' 2>/dev/null \
  | grep -oE '「[^」]{2,12}」|“[^”]{2,12}”' \
  | sed -E 's/^.//; s/.$//' | sort -u)

FOUND=0
while IFS= read -r term; do
  [ -z "$term" ] && continue
  FOUND=1
  if grep -rqF "$term" "$UI" --include='*.vue' --include='*.js' 2>/dev/null; then
    echo "  [OK] cross-end term exists on PC: ${term}"
  else
    echo "  [FAIL] cross-end term points to missing PC feature (INV-10): ${term}"
    FAIL=1
  fi
done <<< "$MP_TERMS"
[ "$FOUND" -eq 0 ] && echo "  (no cross-end guidance, skip)"

if [ "$FAIL" -ne 0 ]; then
  echo "FAIL: invariants contract violated"
  exit 1
fi
echo "PASS: invariants contract (INV-10 guidance reachability)"
