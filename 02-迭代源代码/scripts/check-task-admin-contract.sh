#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/task/ScheduledTaskController.java"

grep -q '@RequestMapping("/api/system/task")' "$CONTROLLER"
grep -q '@PostMapping("/check-expire")' "$CONTROLLER"
grep -q 'SecurityUtils.isAdmin()' "$CONTROLLER"
grep -q '仅系统管理员可触发该任务' "$CONTROLLER"
echo 'PASS: customer expiry task keeps an explicit administrator-only guard.'
