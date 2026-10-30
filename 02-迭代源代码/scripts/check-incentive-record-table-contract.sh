#!/usr/bin/env bash
# Contract for the incentive upload record table and record mutation boundary.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

INDEX_VIEW="$ROOT/aigicrm-ui/src/views/incentive/index.vue"
RECORD_TABLE="$ROOT/aigicrm-ui/src/views/incentive/RecordTable.vue"
RECORD_TABLE_SPEC="$ROOT/aigicrm-ui/src/views/incentive/RecordTable.spec.js"
SUB_CATEGORY_PANEL="$ROOT/aigicrm-ui/src/views/incentive/SubCategoryPanel.vue"
FILTER_BAR="$ROOT/aigicrm-ui/src/views/incentive/IncentiveFilterBar.vue"
FILE_SLOT_PAGER="$ROOT/aigicrm-ui/src/views/incentive/FileSlotPager.vue"
UPLOAD_DIALOG="$ROOT/aigicrm-ui/src/views/incentive/UploadDialog.vue"
UPLOAD_COMPOSABLE="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.js"
UPLOAD_COMPOSABLE_SPEC="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.spec.js"
PAGINATION_UTIL="$ROOT/aigicrm-ui/src/utils/pagination.js"
API="$ROOT/aigicrm-ui/src/api/incentive.js"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java"
ARCH_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/architecture/PermissionMatrixTest.java"
API_DOC="$ROOT/docs/api/api-list.md"
FEATURE_AUDIT="$ROOT/docs/unified-feature-audit.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required incentive record-table file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in \
  "$INDEX_VIEW" "$RECORD_TABLE" "$RECORD_TABLE_SPEC" "$SUB_CATEGORY_PANEL" "$FILTER_BAR" "$FILE_SLOT_PAGER" "$UPLOAD_DIALOG" "$UPLOAD_COMPOSABLE" "$UPLOAD_COMPOSABLE_SPEC" \
  "$PAGINATION_UTIL" "$API" "$CONTROLLER" "$ARCH_TEST" "$API_DOC" "$FEATURE_AUDIT"; do
  assert_file "$file"
done

# 【2026-10-30 同槽翻页】上传弹窗三个槽区(单槽/三槽/跟踪记录)统一走单卡轮播组件:
# 只渲染当前文件+箭头+页码; 整排平铺卡片形态禁止回潮
assert_contains "$UPLOAD_DIALOG" "FileSlotPager slot-key=\"pre\""
assert_contains "$UPLOAD_DIALOG" ":slot-key=\"slot.key\""
assert_contains "$UPLOAD_DIALOG" "track-mode"
assert_contains "$FILE_SLOT_PAGER" "pager-count"
assert_contains "$FILE_SLOT_PAGER" "handleRemoveSlotFile(slotKey, idx)"
# 弹窗内不得再出现整排平铺卡片(该形态已收敛进 FileSlotPager 单卡轮播)
grep -Fq 'class="file-card has"' "$UPLOAD_DIALOG" && fail "上传弹窗平铺文件卡回潮(应统一走 FileSlotPager 单卡翻页)"

# 【2026-10-29 左面板联动+筛选扩展】点左侧子分类=右侧表格按记录 categoryId 过滤(记录挂二级,数值归一);
# 筛选栏新增行业(字典)/时间范围(闭区间,前10位字典序); 切大类Tab自动清选中防跨大类串数据
assert_contains "$UPLOAD_COMPOSABLE" "toggleCategoryFilter"
assert_contains "$UPLOAD_COMPOSABLE" "selectedCategoryId.value === id ? null : id"
assert_contains "$UPLOAD_COMPOSABLE" "Number(r.categoryId) === Number(selectedCategoryId.value)"
assert_contains "$UPLOAD_COMPOSABLE" "filterDateRange.value.length === 2"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "左侧分类联动"
assert_contains "$SUB_CATEGORY_PANEL" "toggleCategoryFilter(child.id)"
assert_contains "$SUB_CATEGORY_PANEL" "active: selectedCategoryId === child.id"
assert_contains "$SUB_CATEGORY_PANEL" "@click.stop=\"startUpload(child)\""
assert_contains "$FILTER_BAR" "filterIndustry"
assert_contains "$FILTER_BAR" "filterDateRange"
assert_contains "$FILTER_BAR" "value-format=\"YYYY-MM-DD\""

assert_contains "$INDEX_VIEW" "provide(IncentiveUploadKey, upload)"
assert_contains "$INDEX_VIEW" "<RecordTable />"

assert_contains "$RECORD_TABLE" "injectIncentiveUpload"
assert_contains "$RECORD_TABLE" "pageSizes(pageSize)"
assert_contains "$RECORD_TABLE" "layout=\"sizes, prev, pager, next, jumper\""
assert_contains "$RECORD_TABLE" "onSizeChange"
assert_contains "$RECORD_TABLE" "pageNum.value = 1"
assert_contains "$RECORD_TABLE" "isRecordOwner(row)"
assert_contains "$RECORD_TABLE" "toggleShare(row, v)"
assert_contains "$RECORD_TABLE" "startReplace(row)"
assert_contains "$RECORD_TABLE" "handleDeleteRecord(row)"
assert_contains "$RECORD_TABLE" "userStore.isAdmin() || canEdit || row.userId === userStore.userId"
assert_contains "$RECORD_TABLE" "userStore.isAdmin() || canDelete || row.userId === userStore.userId"

assert_contains "$RECORD_TABLE_SPEC" "pageSizes"
assert_contains "$RECORD_TABLE_SPEC" "arrayContaining([30, 50, 100])"
assert_contains "$RECORD_TABLE_SPEC" "onSizeChange(50)"

assert_contains "$UPLOAD_COMPOSABLE" "pageSize = ref(30)"
assert_contains "$UPLOAD_COMPOSABLE" "pagedRecords"
assert_contains "$UPLOAD_COMPOSABLE" "isRecordOwner"
assert_contains "$UPLOAD_COMPOSABLE" "toggleShare"
assert_contains "$UPLOAD_COMPOSABLE" "updateRecord(row.id"
assert_contains "$UPLOAD_COMPOSABLE" "confirmUpload"
assert_contains "$UPLOAD_COMPOSABLE" "periodQueryValue(selectedPeriod.value)"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "toggleShare 非本人拒绝"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "confirmUpload 追加模式"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "周期口径"

assert_contains "$PAGINATION_UTIL" "export function pageSizes"

assert_contains "$API" "getRecords(params)"
assert_contains "$API" "addRecord(data)"
assert_contains "$API" "updateRecord(id, data)"
assert_contains "$API" "deleteRecord(id)"
assert_contains "$API" "/incentive/records"

assert_contains "$CONTROLLER" "@PutMapping(\"/records/{id}\")"
assert_contains "$CONTROLLER" "@DeleteMapping(\"/records/{id}\")"
assert_contains "$CONTROLLER" "SecurityUser user = SecurityUtils.getCurrentUser()"
# 【2026-10-11 钉随重构】2c1c5c62 把 updateRecord/removeFile 两段复制粘贴编辑守卫收敛为
# 共享 assertCanEditRecord(防单边改动致两接口口径漂移)——钉随重锚: 共享方法存在+快速通道语义+两调用点
assert_contains "$CONTROLLER" "private void assertCanEditRecord(Long id)"
assert_contains "$CONTROLLER" "if (SecurityUtils.isAdmin() || incentiveService.canEdit()) return"
assert_contains "$CONTROLLER" "assertCanEditRecord(id);"
assert_contains "$CONTROLLER" "assertCanEditRecord(id);"
assert_contains "$CONTROLLER" "throw new BusinessException(403, \"无权编辑他人记录\")"
assert_contains "$CONTROLLER" "if (!isAdmin && !incentiveService.canDelete())"
assert_contains "$CONTROLLER" "throw new BusinessException(403, \"无权删除他人记录\")"

assert_contains "$ARCH_TEST" "incentiveRecordMutationEndpoints_keepInternalOwnershipAndGrantGuards"
# 【PR-01 N-03 2026-09-18】updateRecord/deleteRecord 恢复方法级 incentive:view 底线注解，
# 归属仍由方法内守卫（admin ∨ can_edit/can_delete ∨ 本人）判定；契约从"无注解"改为断言新设计。
assert_contains "$ARCH_TEST" "assertPreAuthorize(controller.getDeclaredMethod(\"updateRecord\""
assert_contains "$ARCH_TEST" "assertPreAuthorize(controller.getDeclaredMethod(\"deleteRecord\""

assert_contains "$API_DOC" "| PUT | \`/api/incentive/records/{id}\` | \`hasAuthority('incentive:view')\` | updateRecord | IncentiveController.java |"
assert_contains "$API_DOC" "| DELETE | \`/api/incentive/records/{id}\` | \`hasAuthority('incentive:view')\` | deleteRecord | IncentiveController.java |"
assert_contains "$FEATURE_AUDIT" "| \`updateRecord\` | \`PUT\` | \`/incentive/records/\${id}\` |"
assert_contains "$FEATURE_AUDIT" "| \`deleteRecord\` | \`DELETE\` | \`/incentive/records/\${id}\` |"

echo "PASS: incentive record table pagination, sharing, mutation API, and ownership guards are wired."
