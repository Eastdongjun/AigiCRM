#!/usr/bin/env bash
# Validate the mobile approval submission contract without live credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/approval.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/approval/index.js"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java"
SEA_API="$ROOT/aigicrm-miniprogram/api/sea.js"
SEA_PAGE="$ROOT/aigicrm-miniprogram/pages/sea/index.js"
SEA_CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq "url: '/api/approval/submit'" "$API" || fail 'mobile API wrapper does not target approval submit'
grep -Fq "method: 'POST'" "$API" || fail 'mobile approval submit must use POST'
grep -Fq "submitApproval({ type: 'customer_release', customerId:" "$PAGE" || fail 'mobile page does not submit customer release approval'
grep -Fq "actionAfterApprove: 'release'" "$PAGE" || fail 'mobile release action contract is missing'
grep -Fq "@PostMapping(\"/submit\")" "$CONTROLLER" || fail 'backend submit route is missing'
grep -Fq "approval:view" "$CONTROLLER" || fail 'backend submit permission contract is missing'
grep -Fq 'params.get("actionAfterApprove")' "$CONTROLLER" || fail 'backend actionAfterApprove mapping is missing'
grep -Fq "if (getApp().globalData.demoMode)" "$PAGE" || fail 'demo approval write guard is missing'
grep -Fq "const status = action === 'approve' ? 'approved' : 'rejected'" "$PAGE" || fail 'demo approval state update is missing'
grep -Fq "if (getApp().globalData.demoMode)" "$ROOT/aigicrm-miniprogram/pages/approval/detail/index.js" || fail 'demo approval detail write guard is missing'
grep -Fq "'/api/approval/' + id + '/approve'" "$ROOT/aigicrm-miniprogram/api/approval.js" || fail 'mobile approve route is missing'
grep -Fq "'/api/approval/' + id + '/reject'" "$ROOT/aigicrm-miniprogram/api/approval.js" || fail 'mobile reject route is missing'
grep -Fq 'node.setAction("approve")' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java" || fail 'approve audit action persistence is missing'
grep -Fq 'node.setAction("reject")' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java" || fail 'reject audit action persistence is missing'
grep -Fq 'node.setComment(comment)' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java" || fail 'approval audit comment persistence is missing'
grep -Fq 'node.setOperatedAt(LocalDateTime.now())' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java" || fail 'approval audit timestamp persistence is missing'

grep -Fq "url: '/api/sea/claim-records'" "$SEA_API" || fail 'mobile sea API does not target claim records'
grep -Fq "type: 'claim'" "$SEA_PAGE" || fail 'mobile sea page does not submit claim approval'
grep -Fq "actionAfterApprove: '客户归属→本人'" "$SEA_PAGE" || fail 'mobile sea claim post-approval action is missing'
grep -Fq '@GetMapping("/claim-records")' "$SEA_CONTROLLER" || fail 'backend claim records route is missing'
grep -Fq 'BizApproval::getApprovalType' "$SEA_CONTROLLER" || fail 'backend claim records must filter approval type'

echo 'PASS: mobile approval submission contract is aligned.'
