#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MATRIX="$ROOT/docs/function-permission-matrix.md"
BLUEPRINT="$ROOT/docs/system-functional-blueprint.md"
APP_PAGE="$ROOT/aigicrm-miniprogram/pages/app/index.js"
APP_WXML="$ROOT/aigicrm-miniprogram/pages/app/index.wxml"
TODO="$ROOT/docs/full-stack-unified-development-todolist.md"
MOBILE_TODO="$ROOT/docs/mobile-pc-unified-todolist.md"
MINIPROGRAM_PACKAGE="$ROOT/scripts/miniprogram/package.json"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_line() {
  local expected="$1"
  grep -Fq "$expected" "$MATRIX" || fail "function permission matrix line is stale: $expected"
}
require_blueprint_line() {
  local expected="$1"
  grep -Fq "$expected" "$BLUEPRINT" || fail "system functional blueprint mobile status is stale: $expected"
}

require_line '| 工作台 | Daily stats | `GET /api/statistics/daily-stats` | `statistics:view` or `dashboard:view` | Role/org scope | Read/statistics | Yes | Implemented |'
require_line '| 工作台 | Weekly stats | `GET /api/statistics/weekly-stats` | `statistics:view` or `dashboard:view` | Role/org scope | Read/statistics | Yes | Implemented |'
require_line '| 工作台 | Indicator stats | `GET /api/statistics/indicator-stats` | `statistics:view` | Role/org scope | Read/statistics | Yes | Implemented |'
require_line '| Customer create/edit | No mobile admin | Implemented scoped | Implemented scoped | Implemented own/scoped | Implemented scoped | No | No |'
require_line '| File upload/share/delete | No mobile admin | Upload/share/delete implemented by granted row action | Upload/delete implemented by granted row action | Upload own/share/delete if granted | Upload/share/delete if granted | Yes | Yes |'
require_line '| Incentive upload/edit/delete | No mobile admin | Upload/edit/delete implemented by owner or grant | Upload/edit/delete if granted | Upload own/edit own or granted, delete own or granted | Optional upload/edit/delete by grant | Yes | Yes |'
require_line '| Public sea claim/edit/delete | No mobile admin | Claim/edit/delete implemented by backend row grant | Claim/edit/delete if granted | Claim/edit/delete if granted | Claim/edit/delete if granted | Yes | Yes |'
require_line '| Mobile `aigicrm-miniprogram` | Lightweight operational client: login, home, scoped customer create/edit/read, customer share/log/indicator/contact/finance create, file preview/upload/share/folder management, incentive query/upload/edit/read-state/stats, approval action/submission, public sea read and claim approval |'
require_line '| Mobile Shell | `pages/app/index` | Authenticated and per-entry permission gates from `/api/auth/info` | No extra business API; navigates to registered mobile pages | Mobile counterpart of the PC left menu/application navigation; hides unauthorized app entries |'
require_line 'Mobile customer detail now uses non-tab detail subpages for share, logs, contacts, finance, timeline and six-indicator workflows. Keep customer id propagation covered by navigation and customer-form contracts.'
if grep -Fq 'Mobile customer detail is currently list/detail-card level' "$MATRIX"; then
  fail "function permission matrix still contains stale mobile customer-detail limitation"
fi
grep -Fq '| Mobile operational scope | Mobile supports field use with shared backend identity and scope: read, search, preview/download, upload where granted, mark-read, approve/reject, customer follow-up/contact/finance/indicator updates, public-sea claim/edit/delete where backend row grants allow, and statistics/ranking views. |' "$BLUEPRINT" \
  || fail "system functional blueprint mobile operating principle is stale"
grep -Fq '| Dashboard | `dashboard:view` | Approval widget uses approval permissions | Mobile dashboard/statistics cards implemented |' "$BLUEPRINT" \
  || fail "system functional blueprint dashboard mobile phase is stale"
grep -Fq '| Approval | `approval:view` | `approval:approve/reject` | Mobile list/detail/approve/reject and business-flow submission implemented |' "$BLUEPRINT" \
  || fail "system functional blueprint approval mobile phase is stale"
grep -Fq '| Legacy incentive alias | `pages/cases/index`, `pages/cases/detail/index` | `incentive:view` | Compatibility alias | Historical links redirect to 四项激励 workbench/detail; they must not reintroduce a separate case concept or independent data source. |' "$BLUEPRINT" \
  || fail "system functional blueprint legacy cases alias boundary is stale"
grep -Fq '| Mobile high-risk boundary needs live QA | Mobile now has selected destructive actions: customer delete, contact delete, finance delete, file delete, incentive record delete and public-sea row delete. Each must stay behind explicit permissions, row/detail confirmation and backend service guards; imports/exports, system admin, file ACL mutation, incentive category config, sea import/config and score entry remain PC-only. | Run WeChat DevTools/manual QA with granted and denied accounts, then compare PC/mobile behavior against the same backend data. |' "$BLUEPRINT" \
  || fail "system functional blueprint mobile high-risk boundary is stale"
grep -Fq 'Run WeChat DevTools/manual QA for login, customer, file, incentive, approval, public sea, statistics and ranking with both granted and denied accounts.' "$BLUEPRINT" \
  || fail "system functional blueprint mobile QA next step is stale"
grep -Fq 'Compare PC/mobile behavior against the same backend data for implemented mobile mutations: customer/contact/finance delete, file delete, incentive record delete and public-sea row delete.' "$BLUEPRINT" \
  || fail "system functional blueprint implemented mobile mutation QA is stale"
grep -Fq 'Keep imports/exports, system management, file ACL mutation, incentive category config, public-sea import/config, score entry/config/export, role/position/scope config and batch operations PC-only unless separately accepted with release-level QA evidence.' "$BLUEPRINT" \
  || fail "system functional blueprint PC-only high-risk boundary is stale"
grep -Fq 'Maintain the landed DevTools automator precheck for mobile PC parity (`npm run verify:mobile-parity` in `scripts/miniprogram`), with `npm run verify:incentive-attachments` available for focused incentive upload/list/detail/timeline attachment retesting. Real-account submission, true download/save behavior and same-backend PC/mobile reconciliation still require live DevTools or phone QA.' "$BLUEPRINT" \
  || fail "system functional blueprint DevTools automator status is stale"
grep -Fq '"verify:mobile-parity"' "$MINIPROGRAM_PACKAGE" \
  || fail "miniprogram automator package must expose verify:mobile-parity"
grep -Fq '"verify:incentive-attachments"' "$MINIPROGRAM_PACKAGE" \
  || fail "miniprogram automator package must expose verify:incentive-attachments"
if grep -Fq 'Add simulator automation only after live DevTools scenarios are stable enough to encode.' "$BLUEPRINT"; then
  fail "system functional blueprint still says simulator automation is only future work"
fi
if grep -Fq 'lightweight follow-up later' "$BLUEPRINT"; then
  fail "system functional blueprint still says mobile follow-up is later"
fi
if grep -Fq '| Score | `score:view`, `score:entry` | `score:manage` | Phase 3 |' "$BLUEPRINT"; then
  fail "system functional blueprint still contains stale duplicate score phase"
fi
if grep -Fq 'Mobile high-risk actions not released' "$BLUEPRINT"; then
  fail "system functional blueprint still says mobile high-risk actions are unreleased"
fi
if grep -Fq 'Split mobile customer detail into a non-tab page before adding deep links' "$BLUEPRINT"; then
  fail "system functional blueprint still says mobile customer detail split is pending"
fi
if grep -Fq 'Decide whether to explicitly accept any remaining high-risk mobile mutations' "$BLUEPRINT"; then
  fail "system functional blueprint still treats implemented mobile high-risk mutations as undecided"
fi
grep -Fq 'Mobile public sea is no longer read-only: list/statistics/visible-pool, claim approval submission, permitted edit route, claim records and permitted row delete are implemented; import/config stay PC-only.' "$TODO" \
  || fail "mobile public sea todo boundary is stale"
grep -Fq 'Delete file. Mobile action is gated by `file:delete` and folder/file ACL; ACL mutation and download-setting changes stay PC-only.' "$TODO" \
  || fail "mobile file delete todo boundary is stale"
grep -Fq 'Permitted edit and row delete are implemented through backend `canEdit/canDelete` flags. Import/config stay PC-only; `scripts/check-mobile-sea-boundary.sh` verifies the boundary.' "$TODO" \
  || fail "mobile sea mutation todo boundary is stale"
grep -Fq 'Add indicator tab (relationship score and six-indicator view/save in mobile customer detail, guarded by `customer:view` / `customer:edit`).' "$TODO" \
  || fail "full-stack todo customer indicator status is stale"
grep -Fq '| Mobile public sea operations | Implemented | `pages/sea`, `api/sea.js`, backend `canClaim/canEdit/canDelete` |' "$TODO" \
  || fail "full-stack todo summary still describes stale mobile sea read-only status"
grep -Fq 'Verify public sea list/statistics and permitted claim/edit/delete actions match PC filters and backend row grants.' "$TODO" \
  || fail "full-stack integration QA still describes sea as read-only"
grep -Fq 'Record every PC/Mini Program integration check with the `PC/小程序同数据对账记录模板` in `docs/mobile-manual-qa-checklist.md`; evidence must include backend environment, account role/permission set, PC evidence, mobile evidence, interface/permission result, conclusion, and residual differences.' "$TODO" \
  || fail "full-stack integration QA must require same-data reconciliation evidence template"
grep -Fq 'Add claim/edit/delete actions guarded by backend `canClaim/canEdit/canDelete` and service-layer data-scope checks; import/config remain PC-only.' "$MOBILE_TODO" \
  || fail "mobile-pc unified todo sea implementation status is stale"
grep -Fq 'Confirm mobile only displays and executes row actions according to backend `canClaim`, `canEdit`, and `canDelete` flags; denied rows must hide actions or fail through backend guards.' "$MOBILE_TODO" \
  || fail "mobile-pc unified todo sea manual QA boundary is stale"
grep -Fq 'confirm the mobile file center list/preview matches the PC file center for the same account and folder/keyword filters.' "$MOBILE_TODO" \
  || fail "mobile-pc unified todo file manual QA still uses stale customer-file scope"
grep -Fq 'Use the `PC/小程序同数据对账记录模板` in `docs/mobile-manual-qa-checklist.md` for every live PC/mobile comparison; each row must include backend environment, account role/permission set, PC evidence, mobile evidence, interface/permission result, conclusion, and residual differences.' "$MOBILE_TODO" \
  || fail "mobile-pc unified todo must require same-data reconciliation evidence template"
if grep -Fq 'Keep mobile public sea read-only in first release' "$TODO"; then
  fail "full-stack todo still says mobile public sea is read-only"
fi
if grep -Fq 'Mobile public sea read-only' "$TODO"; then
  fail "full-stack todo summary still says mobile public sea is read-only"
fi
if grep -Fq 'Verify public sea read-only list matches PC filters' "$TODO"; then
  fail "full-stack integration QA still says mobile public sea is read-only"
fi
if grep -Fq 'Add mobile read-only sea pool list first' "$MOBILE_TODO"; then
  fail "mobile-pc unified todo still says mobile public sea is read-only"
fi
if grep -Fq 'does not execute those actions in this phase' "$MOBILE_TODO"; then
  fail "mobile-pc unified todo still says sea row actions are not executable"
fi
if grep -Fq 'mobile customer-file list matches PC customer-file list' "$MOBILE_TODO"; then
  fail "mobile-pc unified todo still references removed customer-file relation QA"
fi
if grep -Fq 'Keep delete and ACL management PC-only' "$TODO"; then
  fail "full-stack todo still says mobile file delete is PC-only"
fi
if grep -Fq 'Keep import/config/delete PC-only' "$TODO"; then
  fail "full-stack todo still says mobile sea delete is PC-only"
fi
if grep -Fq 'read-only six-indicator summary' "$TODO"; then
  fail "full-stack todo still says mobile customer indicators are read-only"
fi
grep -Fq "requireLogin()" "$APP_PAGE" || fail "mobile app launcher must require login"
grep -Fq "hasPermission(item.permission)" "$APP_PAGE" || fail "mobile app launcher must filter entries by auth permissions"
grep -Fq "openApp(e)" "$APP_PAGE" || fail "mobile app launcher navigation handler is missing"
grep -Fq "bindtap=\"openApp\"" "$APP_WXML" || fail "mobile app launcher UI action is missing"
require_line '| Customer | `pages/customer/index` | `customer:view`, plus action permissions | `/api/customer/list`, `/api/customer/{id}`, `/api/customer/list/counts`, customer form/share/log/contact/finance/indicator APIs | List/detail/counts, scoped create/edit/delete, share/release, logs, contacts create/edit/delete, finance create/delete, relationship score and six indicators are implemented; import/export stay PC-first |'
require_line '| File | `pages/files/index` | `file:view`, plus action permissions | `/api/file/list`, `/api/file/download-token`, `/api/file/upload`, folder/share/storage/shared-to-me/ACL read APIs | Unified file center; preview/download/upload/share/replace/delete/folder management/storage/shared-to-me/folder ACL read are implemented; ACL mutation stays PC-first |'
require_line '| Incentive | `pages/incentive/index` | `incentive:view`, optional `incentive:add`, `incentive:read-stats`, `incentive:read-ranking:view` | `/api/incentive/customer-records`, `/records`, `/records/{id}/read`, `/read-stats`, `/read-ranking`, `/category-stats`, `/score/customer/{id}` | Query/detail/upload/edit/delete, file preview/download, read-state, read stats/ranking, category stats and customer score are implemented; category config stays PC-first |'
require_line '| Public Sea | `pages/sea/index` | `sea:view`, optional `sea:claim` and row edit/delete grant | `/api/sea/list`, `/statistics`, `/configs/visible`, `/claim-records`, shared approval submit and customer edit route | List/stats/pool selector, claim approval submission, claim records, permitted edit route and row delete are implemented; import/config stay PC-first |'
require_line '| Group Switch | Switch current group | `POST /api/auth/switch-group` | Authenticated token | User allowed groups | Session mutation | Yes | Implemented |'
require_line '| Password Recovery | Lookup/send-code/reset | `/api/auth/forgot-password/*` | Public or token/code verified | User account | Account recovery | Yes or backend available | Implemented |'
require_line '| 新增客户 | Create customer | `POST /api/customer/add` | `customer:add` | Current user'\''s allowed org/group | Create | Yes | Implemented |'
require_line '| 编辑客户 | Edit customer | `PUT /api/customer/{id}` | `customer:edit` | Backend edit access check | Update | Yes | Implemented |'
require_line '| 客户详情 | View follow-up logs | `GET /api/customer/{id}/logs` | `customer:view` | Same customer visibility | Read | Yes | Implemented |'
require_line '| 客户详情 | Add follow-up log | `POST /api/customer/{id}/logs` | `customer:edit` | Same customer edit boundary | Create | Yes | Implemented |'
require_line '| 客户详情 | Add invoice | `POST /api/customer/{id}/invoices` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Create | Yes | Implemented |'
require_line '| 客户详情 | Delete invoice | `DELETE /api/customer/{id}/invoices/{invoiceId}` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Delete | Yes | Implemented |'
require_line '| 客户详情 | View six indicators | `GET /api/customer/{id}/indicator` | `customer:view` | Same customer visibility | Read | Yes | Implemented |'
require_line '| 客户详情 | Save six indicators | `POST /api/customer/{id}/indicator` | `customer:edit` | Same customer edit boundary | Update | Yes | Implemented |'
require_line '| 客户详情 | Update relationship | `PUT /api/customer/{id}/relationship` | `customer:edit` | Same customer edit boundary | Update | Yes | Implemented |'
require_line '| 客户详情 | View shares | `GET /api/customer/{id}/shares` | `customer:view` | Same customer visibility | Read | Yes | Implemented |'
require_line '| 客户详情 | Add/remove share | `POST /api/customer/{id}/shares`, `DELETE /api/customer/{id}/shares/{userId}` | `customer:edit` | Same customer edit boundary | Share mutation | Yes | Implemented |'
require_line '| 客户详情 | Release/return to sea | `POST /api/customer/{id}/release` | `customer:edit` | Same customer edit boundary and sea config match | Status mutation | Yes | Implemented |'
require_line '| 客户联系人 | Add contact | `POST /api/customer/{id}/contacts` | `customer:edit` | Same customer edit boundary | Create | Yes | Implemented |'
require_line '| 客户联系人 | Edit/delete contacts | `PUT/DELETE /api/customer/{id}/contacts...` | `customer:edit` | Same customer edit boundary | Update/Delete | Yes | Implemented |'
require_line '| 客户回款 | Add payment | `POST /api/customer/{id}/payments` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Create | Yes | Implemented |'
require_line '| 客户回款 | Delete payment | `DELETE /api/customer/{id}/payments/{paymentId}` | `customer:edit` + `finance:view` | Same customer edit boundary and finance visibility | Delete | Yes | Implemented |'
require_line '| 客户校验 | Check duplicate name | `GET /api/customer/check-name` | `customer:view` | Backend check rules | Read/validate | Yes | Implemented |'
require_line '| 用户简表 | User selector | `GET /api/customer/users/simple` | `customer:view` | Backend user scope | Read | Yes | Implemented |'
require_line '| 文件管理 | Folder tree | `GET /api/file/folders` | `file:view` | Backend folder ACL and visible folders | Read | Yes | Implemented |'
require_line '| 文件管理 | Upload file | `POST /api/file/upload` | `file:upload` | Folder upload permission and service ACL | Upload/Create | Yes | Implemented |'
require_line '| 文件管理 | Create folder | `POST /api/file/folders` | `file:folder:manage` | Parent folder permission | Create | Yes | Implemented |'
require_line '| 文件管理 | Rename/update folder | `PUT /api/file/folders/{id}` | `file:folder:manage` | Folder owner/ACL | Update | Yes | Implemented |'
require_line '| 文件管理 | Delete folder | `DELETE /api/file/folders/{id}` | `file:folder:manage` | Folder owner/ACL | Delete | Yes | Implemented |'
require_line '| 文件管理 | Delete file | `DELETE /api/file/{id}` | `file:delete` | File owner/ACL | Delete | Yes | Implemented |'
require_line '| 文件管理 | Replace file | `POST /api/file/{id}/replace` | `file:edit` | File owner/ACL | Replace | Yes | Implemented |'
require_line '| 文件管理 | Path download | `GET /api/file/download/**` | `file:view` | File path ACL | Download | Yes | Implemented |'
require_line '| 文件分享 | Share file | `POST /api/file/{id}/share` | `file:share` | File owner/share permission | Share mutation | Yes | Implemented |'
require_line '| 文件分享 | Shared to me | `GET /api/file/shared-to-me`, `/count` | `file:view` | Files shared to current user | Read | Yes | Implemented |'
require_line '| 文件夹 ACL | View ACL | `GET /api/file/folders/{id}/acl` | `file:acl:manage` | Folder ACL manager | Read | Yes | Implemented |'
require_line '| 四项激励 | Category list | `GET /api/incentive/categories` | `incentive:view` | Tenant/group | Read | Yes | Implemented |'
require_line '| 四项激励 | Record list | `GET /api/incentive/records` | `incentive:view` | Upload owner, share visibility, role scope | Read | Yes | Implemented |'
require_line '| 四项激励 | Upload/add record | `POST /api/incentive/records` | `incentive:add` | Customer access and uploader identity | Create | Yes | Implemented |'
require_line '| 四项激励 | Edit own/permitted record | `PUT /api/incentive/records/{id}` | Method allows service-layer ownership/permitted edit | Owner/permitted editor | Update | Yes | Implemented |'
require_line '| 四项激励 | Delete own/permitted record | `DELETE /api/incentive/records/{id}` | Service-layer ownership/permitted delete | Owner/permitted editor/admin | Delete | Yes | Implemented |'
require_line '| 激励查询 | Customer score | `GET /api/incentive/score/customer/{customerId}` | `incentive:view` | Same incentive/customer visibility | Read/statistics | Yes | Implemented |'
require_line '| 激励统计 | Category stats | `GET /api/incentive/category-stats` | `incentive:view` | Role/share/customer visibility | Read/statistics | Yes | Implemented |'
require_line '| 激励配置 | User selector | `GET /api/incentive/users` | `incentive:view` | Backend user scope | Read | Yes | Implemented |'
require_line '| 提醒管理 | Expiring customer list | `GET /api/reminder/expiring-list` | `reminder:view` | Customer data scope | Read | Yes | Implemented |'
require_line '| 提醒管理 | Reminder list | `GET /api/reminder/list` | `reminder:view` | Current user/customer scope | Read | Yes | Implemented |'
require_line '| 提醒管理 | Pending count | `GET /api/reminder/pending-count` | `reminder:view` | Current user scope | Read/statistics | Yes | Implemented |'
require_line '| 提醒管理 | Process reminder | `POST /api/reminder/{id}/process` | `reminder:view` | Current user/reminder scope | Status mutation | Yes | Implemented |'
require_line '| 公海池 | Claim customer | Approval submission through shared approval flow | Backend service checks pool claim permission and `sea:claim` compatibility | Pool-level claim permission | Submit approval/status mutation | Yes | Implemented |'
require_line '| 公海池 | List/search/filter | `GET /api/sea/list` | `sea:view` | Enabled visible pools, dept subtree filter, customer keyword/industry filter | Read | Yes | Implemented |'
require_line '| 公海池 | Edit sea customer | Customer edit route | `customer:edit` plus pool edit permission | Customer/pool edit permission | Update | Yes | Implemented |'
require_line '| 捞取记录 | My claim records | `GET /api/sea/claim-records` | `sea:view` | Current applicant only | Read | Yes | Implemented |'
require_line '| 审批管理 | Submit approval | `POST /api/approval/submit` | `approval:view` | Customer/action-specific service validation | Create | Yes | Implemented |'
require_line '| 统计分析 | Analysis | `GET /api/statistics/analysis` | `statistics:view` | Role/org/group scope | Read/statistics | Yes | Implemented |'
require_line '| 统计分析 | Team activities | `GET /api/statistics/team-activities` | `statistics:view` | Role/org/group scope | Read/statistics | Yes | Implemented |'
require_line '| 学分排行 | Ranking | `GET /api/score/ranking` | `score:view` | Role/org/group scope | Read/statistics | Yes | Implemented |'
require_blueprint_line '| Mobile status | Login, `/info`, group switch, forgot-password and password reset are implemented. WeChat binding has a reserved status page only; OAuth/openid binding is not connected. |'
require_blueprint_line '| Mobile status | Read list/detail/counts, create/edit/delete, share/release, logs, contacts create/edit/delete, finance read/create/delete, relationship score and six-indicator save are implemented with scoped permissions. Import/export stay PC-first. |'
require_blueprint_line '| Mobile status | File list, preview/download, upload, share, replace, file delete, folder create/rename/delete, storage stats, shared-to-me and folder ACL read are implemented. ACL mutation and download-setting changes stay PC-first. |'
require_blueprint_line '| Mobile status | Category tree, record list, upload, own/permitted edit/delete, customer score, scoreboard/ranking, category stats, user selector, customer-record list, file preview/download, mark-read and read-stats are implemented. Category config stays PC-first. |'
require_blueprint_line '| Mobile status | Expiring list, reminder list, pending count and reminder process are implemented. Batch ensure-expiring remains PC/backend-first. |'
require_blueprint_line '| Mobile status | List/stats/pool selector, claim approval submission, claim records, permitted customer edit route and permitted row delete are implemented. Import and sea config remain PC-only. |'
require_blueprint_line '| Mobile status | List/statistics/detail, approve/reject and business-flow approval submission from customer release / sea claim are implemented. Approval config remains PC-only. |'

echo 'PASS: mobile feature matrix implemented/planned statuses are aligned.'
