#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
page="$ROOT/aigicrm-miniprogram/pages/incentive/index.js"
detail_page="$ROOT/aigicrm-miniprogram/pages/incentive/detail/index.js"
detail_wxml="$ROOT/aigicrm-miniprogram/pages/incentive/detail/index.wxml"
detail_json="$ROOT/aigicrm-miniprogram/pages/incentive/detail/index.json"
api="$ROOT/aigicrm-miniprogram/api/incentive.js"
upload_page="$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.js"
upload_wxml="$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.wxml"
upload_json="$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.json"
timeline_page="$ROOT/aigicrm-miniprogram/pages/customer/timeline/index.js"
timeline_wxml="$ROOT/aigicrm-miniprogram/pages/customer/timeline/index.wxml"
wxml="$ROOT/aigicrm-miniprogram/pages/incentive/index.wxml"
style="$ROOT/aigicrm-miniprogram/pages/incentive/index.wxss"
json="$ROOT/aigicrm-miniprogram/pages/incentive/index.json"
app_json="$ROOT/aigicrm-miniprogram/app.json"
app_wxss="$ROOT/aigicrm-miniprogram/app.wxss"
# 【2026-10-05】pages/cases/* 兼容壳整树已在 1b3c865b 随死页面清理删除(含 app.json 注册),
# 读取这些文件的 legacy_cases_* 变量与检查一并移除; 保留"app.json 不得回潮注册 cases"等负向防回潮检查
attachment_util="$ROOT/aigicrm-miniprogram/utils/attachment.js"
profile_page="$ROOT/aigicrm-miniprogram/pages/profile/index.js"
profile_wxml="$ROOT/aigicrm-miniprogram/pages/profile/index.wxml"
manifest="$ROOT/docs/unified-feature-manifest.md"
permission_controller="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java"
incentive_controller="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java"
incentive_service="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java"
automator_package="$ROOT/scripts/miniprogram/package.json"
automator_script="$ROOT/scripts/miniprogram/verify-incentive-attachments.mjs"
matrix="$ROOT/docs/function-permission-matrix.md"
grep -q "hasPermission('incentive:view')" "$page" || { echo "FAIL: incentive view gate is missing" >&2; exit 1; }
grep -q "hasPermission('incentive:add')" "$page" || { echo "FAIL: incentive upload gate is missing" >&2; exit 1; }
grep -q "没有四项激励查看权限" "$page" || { echo "FAIL: incentive view denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项激励查看权限" "$detail_page" || { echo "FAIL: incentive detail view denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项激励删除权限" "$page" || { echo "FAIL: incentive list delete denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项激励追加权限" "$page" || { echo "FAIL: incentive list append denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项激励删除权限" "$detail_page" || { echo "FAIL: incentive detail delete denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项激励上传权限" "$upload_page" || { echo "FAIL: incentive upload denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励上传失败" "$upload_page" || { echo "FAIL: incentive upload failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励详情加载失败" "$detail_page" || { echo "FAIL: incentive detail load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项激励记录" "$detail_page" || { echo "FAIL: incentive detail delete modal copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项激励记录失败" "$detail_page" || { echo "FAIL: incentive detail delete failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励加载失败" "$page" || { echo "FAIL: incentive list load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励分类加载失败" "$page" || { echo "FAIL: incentive category load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励统计加载失败" "$page" || { echo "FAIL: incentive stats load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项激励记录" "$page" || { echo "FAIL: incentive list delete modal copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项激励记录失败" "$page" || { echo "FAIL: incentive list delete failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项激励记录加载失败" "$upload_page" || { echo "FAIL: incentive upload edit-record load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q '"navigationBarTitleText":"四项激励"' "$json" || { echo "FAIL: incentive index title must use four-incentive naming" >&2; exit 1; }
grep -q '"navigationBarTitleText":"四项激励详情"' "$detail_json" || { echo "FAIL: incentive detail title must use four-incentive naming" >&2; exit 1; }
grep -q '<view class="hero-title">四项激励详情</view>' "$detail_wxml" || { echo "FAIL: incentive detail hero title must use four-incentive naming" >&2; exit 1; }
grep -q '暂无四项激励详情' "$detail_wxml" || { echo "FAIL: incentive detail empty state must use four-incentive naming" >&2; exit 1; }
grep -q '暂无四项激励记录' "$wxml" || { echo "FAIL: incentive list empty state must use four-incentive naming" >&2; exit 1; }
grep -q "typeText: '四项激励'" "$timeline_page" || { echo "FAIL: customer timeline incentive type label must use four-incentive naming" >&2; exit 1; }
grep -q '"navigationBarTitleText":"四项激励上传"' "$upload_json" || { echo "FAIL: incentive upload title must use four-incentive naming" >&2; exit 1; }
grep -q '<view class="hero-title">四项激励上传</view>' "$upload_wxml" || { echo "FAIL: incentive upload hero title must use four-incentive naming" >&2; exit 1; }
grep -q 'categoryTree' "$page" || { echo "FAIL: incentive mobile category tree is missing" >&2; exit 1; }
grep -q 'getCategoryStats' "$page" || { echo "FAIL: incentive mobile category stats is missing" >&2; exit 1; }
grep -q 'getRecords' "$page" || { echo "FAIL: incentive mobile category stats detail does not load shared records" >&2; exit 1; }
grep -q 'getScoreboard' "$page" || { echo "FAIL: incentive mobile scoreboard is missing" >&2; exit 1; }
grep -q "getRanking({ type: 'dept' })" "$page" || { echo "FAIL: incentive mobile dept ranking is missing" >&2; exit 1; }
grep -q 'getReadRanking' "$page" || { echo "FAIL: incentive mobile read ranking is missing" >&2; exit 1; }
grep -q 'getGrantCheck' "$api" || { echo "FAIL: incentive grant check API is missing" >&2; exit 1; }
grep -q '/api/incentive/permission/check' "$api" || { echo "FAIL: incentive grant check endpoint is stale" >&2; exit 1; }
grep -q '@GetMapping("/customer-records")' "$incentive_controller" || { echo "FAIL: mobile incentive customer-records backend route is missing" >&2; exit 1; }
grep -q 'getCustomerRecords(industry' "$incentive_controller" || { echo "FAIL: mobile incentive customer-records route does not call service detail query" >&2; exit 1; }
grep -q 'fillFileNames(records)' "$incentive_service" || { echo "FAIL: incentive service does not hydrate file names for mobile attachments" >&2; exit 1; }
grep -q 'r.setFilePreName(joinFileNames' "$incentive_service" || { echo "FAIL: incentive service does not hydrate pre-install file names" >&2; exit 1; }
grep -q 'r.setFileMidName(joinFileNames' "$incentive_service" || { echo "FAIL: incentive service does not hydrate mid-install file names" >&2; exit 1; }
grep -q 'r.setFilePostName(joinFileNames' "$incentive_service" || { echo "FAIL: incentive service does not hydrate post-install file names" >&2; exit 1; }
grep -q 'r.setFileTrackName(joinFileNames' "$incentive_service" || { echo "FAIL: incentive service does not hydrate follow-up file names" >&2; exit 1; }
grep -q 'verify:incentive-attachments' "$automator_package" || { echo "FAIL: incentive DevTools automator verification script is not exposed" >&2; exit 1; }
grep -q 'WECHAT_AUTOMATOR_WS' "$automator_script" || { echo "FAIL: incentive DevTools automator script cannot target a configurable websocket" >&2; exit 1; }
grep -q 'verifyUploadPage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not verify upload attachments" >&2; exit 1; }
grep -q 'verifyDetailPage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not verify detail attachments" >&2; exit 1; }
grep -q 'verifyListPage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not verify list attachments" >&2; exit 1; }
grep -q 'verifyReadStatsPage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not verify read stats/ranking UI" >&2; exit 1; }
grep -q 'verifyTimelinePage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not verify customer timeline attachments" >&2; exit 1; }
grep -q '__aigicrmAttachmentActionsPatched' "$automator_script" || { echo "FAIL: incentive DevTools automator does not patch native attachment actions" >&2; exit 1; }
grep -q 'attachmentActionCalls' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert native attachment action calls" >&2; exit 1; }
grep -q 'selected-file-thumb' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload thumbnail rendering" >&2; exit 1; }
grep -q 'upload page selected image must call previewImage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-image preview action" >&2; exit 1; }
grep -q 'upload page selected image download must call saveFile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-image download action" >&2; exit 1; }
grep -q 'upload page selected document must call openDocument' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-document preview action" >&2; exit 1; }
grep -q 'upload page selected document download must call saveFile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-document download action" >&2; exit 1; }
grep -q 'file-tile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert detail attachment tiles" >&2; exit 1; }
grep -q 'wall-tile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert list attachment tiles" >&2; exit 1; }  # 【#4 2026-10-12】列表佐证改照片墙重锚
grep -q 'timeline-file-row' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert customer timeline attachment rows" >&2; exit 1; }
grep -q 'timeline-attachments.png' "$automator_script" || { echo "FAIL: incentive DevTools automator does not capture customer timeline attachment evidence" >&2; exit 1; }
grep -q 'read-stats-ranking.png' "$automator_script" || { echo "FAIL: incentive DevTools automator does not capture read stats/ranking evidence" >&2; exit 1; }
grep -q "switchTab', eventWithDataset({ key: 'reads' })" "$automator_script" || { echo "FAIL: incentive DevTools automator does not switch to read stats/ranking tab" >&2; exit 1; }
grep -q "read page expected stats and ranking panels" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert read stats/ranking panels" >&2; exit 1; }
grep -q "assertAttachmentTags(files, \\['安装前', '安装中', '安装后', '跟踪', '佐证'\\], 'detail page')" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert all PC attachment slots on detail page" >&2; exit 1; }
grep -q "assertAttachmentTags(wallFiles, \\['安装前', '安装中', '安装后', '跟踪', '佐证'\\], 'list page')" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert all PC attachment slots on list page" >&2; exit 1; }
grep -q "assertAttachmentTags(files, \\['安装前', '安装中', '安装后', '跟踪', '佐证'\\], 'timeline page')" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert all PC attachment slots on timeline page" >&2; exit 1; }
grep -q 'calls.downloadFile.length === 3' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert image preview, document preview, and document download fetches" >&2; exit 1; }
grep -q 'calls.previewImage.length === 1' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert previewImage calls" >&2; exit 1; }
grep -q 'calls.openDocument.length === 1' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert document open calls" >&2; exit 1; }
grep -q 'calls.saveFile.length === 1' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert document download save calls" >&2; exit 1; }
grep -q '@GetMapping("/check")' "$permission_controller" || { echo "FAIL: incentive grant self-check backend route is missing" >&2; exit 1; }
grep -q '无权限注解（登录即可自检）' "$permission_controller" || { echo "FAIL: incentive grant self-check contract must remain authenticated self-check" >&2; exit 1; }
grep -q 'result.put("canDelete", incentiveService.canDelete())' "$permission_controller" || { echo "FAIL: incentive grant self-check does not expose canDelete from service contract" >&2; exit 1; }
grep -q 'result.put("canEdit", incentiveService.canEdit())' "$permission_controller" || { echo "FAIL: incentive grant self-check does not expose canEdit from service contract" >&2; exit 1; }
! grep -q '/api/incentive/permission/grant' "$api" || { echo "FAIL: mobile incentive API must not expose PC-only grant mutation" >&2; exit 1; }
grep -Fq '| 激励授权 | Permission check | `GET /api/incentive/permission/check` | Authenticated self-check; global login guard plus service grant table | Current user only | Read/validate | Yes | Implemented |' "$matrix" || { echo "FAIL: incentive permission self-check matrix status is stale" >&2; exit 1; }
grep -q 'deleteRecord' "$api" || { echo "FAIL: incentive record delete API is missing" >&2; exit 1; }
grep -q "/api/incentive/records/' + id" "$api" || { echo "FAIL: incentive record delete endpoint is stale" >&2; exit 1; }
grep -q 'getScoreByCustomer' "$api" || { echo "FAIL: incentive mobile customer score API is missing" >&2; exit 1; }
grep -q '/api/incentive/score/customer/' "$api" || { echo "FAIL: incentive mobile customer score endpoint is stale" >&2; exit 1; }
grep -q 'getUsers' "$api" || { echo "FAIL: incentive mobile user selector API is missing" >&2; exit 1; }
grep -q '/api/incentive/users' "$api" || { echo "FAIL: incentive mobile user selector endpoint is stale" >&2; exit 1; }
grep -q 'getUsers' "$page" || { echo "FAIL: incentive mobile user selector is not loaded" >&2; exit 1; }
grep -q 'salesperson' "$page" || { echo "FAIL: incentive mobile user filter does not call customer-records salesperson filter" >&2; exit 1; }
grep -q 'buildRecordParams' "$page" || { echo "FAIL: incentive mobile PC-parity query params builder is missing" >&2; exit 1; }
grep -q 'params.customerName = keyword' "$page" || { echo "FAIL: incentive mobile keyword search does not query customerName like PC" >&2; exit 1; }
grep -q 'params.keyword = keyword' "$page" || { echo "FAIL: incentive mobile keyword search does not query product/keyword like PC" >&2; exit 1; }
grep -q 'params.industry = industry' "$page" || { echo "FAIL: incentive mobile industry filter is not sent to customer-records" >&2; exit 1; }
grep -q 'params.devType = devType' "$page" || { echo "FAIL: incentive mobile devType filter is not sent to customer-records" >&2; exit 1; }
grep -q 'params.product = product' "$page" || { echo "FAIL: incentive mobile product filter is not sent to customer-records" >&2; exit 1; }
grep -q 'getDepts' "$page" || { echo "FAIL: incentive mobile org filter does not load system departments" >&2; exit 1; }
grep -q 'applyOrgs' "$page" || { echo "FAIL: incentive mobile org tree is not flattened for picker use" >&2; exit 1; }
grep -q 'params.orgIds = String(org.id)' "$page" || { echo "FAIL: incentive mobile org filter is not sent as orgIds like PC" >&2; exit 1; }
grep -q 'params.startDate = this.data.startDate' "$page" || { echo "FAIL: incentive mobile startDate filter is not sent to customer-records" >&2; exit 1; }
grep -q 'params.endDate = this.data.endDate' "$page" || { echo "FAIL: incentive mobile endDate filter is not sent to customer-records" >&2; exit 1; }
grep -q "order: ''" "$page" || { echo "FAIL: incentive mobile score ordering must default to PC-like unsorted state" >&2; exit 1; }
grep -q 'selectedOrderIndex: 0' "$page" || { echo "FAIL: incentive mobile score order picker default index is missing" >&2; exit 1; }
grep -q "if (this.data.order === 'asc')" "$page" || { echo "FAIL: incentive mobile score ascending order branch is missing" >&2; exit 1; }
grep -q "if (this.data.order === 'desc')" "$page" || { echo "FAIL: incentive mobile score descending order branch is missing" >&2; exit 1; }
grep -q "orderOptions" "$wxml" || { echo "FAIL: incentive mobile score order picker does not bind stable options" >&2; exit 1; }
grep -q 'resetRecordFilters' "$page" || { echo "FAIL: incentive mobile PC-parity query filters cannot be reset" >&2; exit 1; }
grep -q 'buildRecordView' "$page" || { echo "FAIL: incentive mobile customer record WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildChildRecordView' "$page" || { echo "FAIL: incentive mobile child record WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildFileGroupView' "$page" || { echo "FAIL: incentive mobile file group counts must be normalized in JS" >&2; exit 1; }
grep -q 'groupIndex' "$page" || { echo "FAIL: incentive mobile file group indexes must be normalized in JS" >&2; exit 1; }
grep -q 'fileIndex' "$page" || { echo "FAIL: incentive mobile file indexes must be normalized in JS" >&2; exit 1; }
grep -q 'buildIncentiveView' "$page" || { echo "FAIL: incentive mobile filter/tab WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'refreshIncentiveView' "$page" || { echo "FAIL: incentive mobile normalized filter/tab state refresh is missing" >&2; exit 1; }
grep -q 'refreshLoading' "$page" || { echo "FAIL: incentive mobile refresh loading state must be normalized in JS" >&2; exit 1; }
grep -q 'searchLoading' "$page" || { echo "FAIL: incentive mobile search loading state must be normalized in JS" >&2; exit 1; }
grep -q 'showLoading' "$page" || { echo "FAIL: incentive mobile loading visibility must be normalized in JS" >&2; exit 1; }
grep -q 'loadingText' "$page" || { echo "FAIL: incentive mobile loading text must be normalized in JS" >&2; exit 1; }
grep -q 'incentiveView.refreshLoading' "$wxml" || { echo "FAIL: incentive mobile refresh button must use normalized loading state" >&2; exit 1; }
grep -q 'incentiveView.searchLoading' "$wxml" || { echo "FAIL: incentive mobile search button must use normalized loading state" >&2; exit 1; }
grep -q 'incentiveView.showLoading' "$wxml" || { echo "FAIL: incentive mobile loading card must use normalized visibility" >&2; exit 1; }
! grep -q '{{loading}}' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw loading state directly" >&2; exit 1; }
grep -q 'buildCategoryTreeView' "$page" || { echo "FAIL: incentive mobile category tree WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildStatDetailView' "$page" || { echo "FAIL: incentive mobile stats detail WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildReadStatsView' "$page" || { echo "FAIL: incentive mobile read stats WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildReadRankingView' "$page" || { echo "FAIL: incentive mobile read ranking WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'toggleCustomerRecords' "$page" || { echo "FAIL: incentive mobile customer detail list cannot expand all records" >&2; exit 1; }
grep -q 'showChildDetail' "$page" || { echo "FAIL: incentive mobile customer detail list cannot open child record detail" >&2; exit 1; }
grep -q 'markRecordRead(index)' "$page" || { echo "FAIL: incentive mobile list detail open must mark records read" >&2; exit 1; }
grep -q 'markChildRecordRead(e.currentTarget.dataset.recordIndex, e.currentTarget.dataset.childIndex)' "$page" || { echo "FAIL: incentive mobile child detail open must mark child record read" >&2; exit 1; }
grep -q 'appendChildRecord' "$page" || { echo "FAIL: incentive mobile customer detail list cannot append child records" >&2; exit 1; }
grep -q 'removeChildRecord' "$page" || { echo "FAIL: incentive mobile customer detail list cannot delete child records" >&2; exit 1; }
grep -q 'findChildRecordFile' "$page" || { echo "FAIL: incentive mobile customer detail list cannot preview/download child record files" >&2; exit 1; }
grep -q '客户维度明细' "$wxml" || { echo "FAIL: incentive mobile customer detail list is missing" >&2; exit 1; }
grep -q 'readStatusClass' "$page" || { echo "FAIL: incentive mobile list must expose read status like PC" >&2; exit 1; }
grep -q 'readStatusText' "$wxml" || { echo "FAIL: incentive mobile list read status UI is missing" >&2; exit 1; }
grep -q 'inline-read-status' "$page" || { echo "FAIL: incentive mobile child records must expose read status like PC" >&2; exit 1; }
grep -q '.tag-read' "$app_wxss" || { echo "FAIL: incentive mobile read status style is missing" >&2; exit 1; }
grep -q '搜索客户 / 产品 / 关键字' "$wxml" || { echo "FAIL: incentive mobile keyword query UI is missing" >&2; exit 1; }
grep -q 'chooseIndustry' "$wxml" || { echo "FAIL: incentive mobile industry query UI is missing" >&2; exit 1; }
grep -q 'chooseDevType' "$wxml" || { echo "FAIL: incentive mobile devType query UI is missing" >&2; exit 1; }
grep -q 'chooseProduct' "$wxml" || { echo "FAIL: incentive mobile product query UI is missing" >&2; exit 1; }
grep -q 'chooseOrg' "$wxml" || { echo "FAIL: incentive mobile org query UI is missing" >&2; exit 1; }
grep -q '全部组织' "$page" || { echo "FAIL: incentive mobile org query default copy is missing" >&2; exit 1; }
grep -q 'chooseStartDate' "$wxml" || { echo "FAIL: incentive mobile start date query UI is missing" >&2; exit 1; }
grep -q 'chooseEndDate' "$wxml" || { echo "FAIL: incentive mobile end date query UI is missing" >&2; exit 1; }
grep -q '全部记录' "$page" || { echo "FAIL: incentive mobile customer detail list all-records entry is missing" >&2; exit 1; }
grep -q 'customer-record-list' "$wxml" || { echo "FAIL: incentive mobile customer detail list expanded timeline UI is missing" >&2; exit 1; }
grep -q 'data-child-index' "$wxml" || { echo "FAIL: incentive mobile customer detail list child record actions lack record index" >&2; exit 1; }
! grep -Eq "item\\.files\\.length|item\\.fileGroups &&|item\\.records &&|item\\.expanded \\?|child\\.categoryName \\|\\||child\\.period \\|\\||child\\.score \\|\\||child\\.preInstallQuestion \\|\\||child\\.fileGroups &&|group\\.files\\.length" "$wxml" || { echo "FAIL: incentive mobile records WXML must not keep record/file fallback branching logic" >&2; exit 1; }
! grep -Eq "wx:for-index=\"groupIndex\"|wx:for-index=\"fileIndex\"" "$wxml" || { echo "FAIL: incentive mobile attachment WXML must not derive file action indexes in template" >&2; exit 1; }
# 【F25 2026-10-13】照片墙循环必须显式命名内层索引: 内层默认 index 遮蔽外层 record index 会让
# data-record-index 错取文件下标(H2 同款回归), 显式 wallGroupIdx/wallFileIdx 是防线, 真实点击钉在
# verify-incentive-attachments.mjs(tap 瓦片按钮经 WXML 绑定断言 previewImage 收到正确文件)。
grep -q 'wx:for-index="wallGroupIdx"' "$wxml" || { echo "FAIL: incentive wall group loops must declare wx:for-index (inner-index shadowing guard)" >&2; exit 1; }
grep -q 'wx:for-index="wallFileIdx"' "$wxml" || { echo "FAIL: incentive wall file loops must declare wx:for-index (inner-index shadowing guard)" >&2; exit 1; }
# 【F24 2026-10-13】setData 瘦身负向钉(1MB 真机预算): 视图不得回携签名缩略图 URL,
# buildRecordView 必须持续剥离 files/fileGroups 原始大数组(改由 thumb.wxs 渲染层拼 URL)
! grep -Eq "thumbUrl[[:space:]]*[:=]" "$page" || { echo "FAIL: incentive view must not rebuild signed thumbUrl into data (setData 1MB budget)" >&2; exit 1; }
grep -q "files: _omitFiles, fileGroups: _omitGroups" "$page" || { echo "FAIL: buildRecordView must keep stripping files/fileGroups from view data" >&2; exit 1; }
! grep -Eq "activeTab ===|industries\\[selectedIndustryIndex\\]|devTypes\\[selectedDevTypeIndex\\]|productOptions\\[selectedProductIndex\\]|orgOptions\\[selectedOrgIndex\\]|userNames\\[selectedUserIndex\\]|orderOptions\\[selectedOrderIndex\\]|readPeriodOptions\\[selectedReadPeriodIndex\\]|startDate \\|\\||endDate \\|\\|" "$wxml" || { echo "FAIL: incentive mobile filters/tabs WXML must not keep array-index fallback logic" >&2; exit 1; }
! grep -Eq "categoryTree\\.length|categoryStats\\.length|scoreboard\\.length|deptRanking\\.length|readStats\\.length|readRanking\\.length|statDetail\\.records\\.length|statDetail\\.parentName \\|\\||index \\+ 1|item\\.realName \\|\\||item\\.readCount \\|\\||item\\.count \\|\\||detail\\.fileGroupsView\\.length" "$wxml" || { echo "FAIL: incentive mobile stats/ranking/read WXML must not keep fallback/count logic" >&2; exit 1; }
grep -q '全部人员' "$page" || { echo "FAIL: incentive mobile user selector UI is missing" >&2; exit 1; }
grep -q '分类树' "$wxml" || { echo "FAIL: incentive mobile category tree UI is missing" >&2; exit 1; }
grep -q '统计月份' "$wxml" || { echo "FAIL: incentive mobile read period selector is missing" >&2; exit 1; }
grep -q 'chooseReadPeriod' "$wxml" || { echo "FAIL: incentive mobile read period selector handler is missing" >&2; exit 1; }
grep -q 'getReadStats({ period: this.data.readPeriod })' "$page" || { echo "FAIL: incentive mobile read stats do not query the selected period" >&2; exit 1; }
grep -q 'getReadRanking({ period: this.data.readPeriod })' "$page" || { echo "FAIL: incentive mobile read ranking does not query the selected period" >&2; exit 1; }
grep -q 'chooseReadPeriod' "$page" || { echo "FAIL: incentive mobile read period state handler is missing" >&2; exit 1; }
grep -q 'read-period-row' "$style" || { echo "FAIL: incentive mobile read period selector style is missing" >&2; exit 1; }
grep -q '阅读排行' "$wxml" || { echo "FAIL: incentive mobile read ranking UI is missing" >&2; exit 1; }
grep -q '客户积分榜' "$wxml" || { echo "FAIL: incentive mobile scoreboard UI is missing" >&2; exit 1; }
grep -q '部门排行' "$wxml" || { echo "FAIL: incentive mobile dept ranking UI is missing" >&2; exit 1; }
grep -q 'openStatDetail' "$page" || { echo "FAIL: incentive mobile category stats cannot drill into PC stats detail" >&2; exit 1; }
grep -q 'statDetail.records' "$page" || { echo "FAIL: incentive mobile category stats detail state is missing" >&2; exit 1; }
grep -q 'normalizeStatDetailRecord' "$page" || { echo "FAIL: incentive mobile stats detail does not normalize PC detail rows" >&2; exit 1; }
grep -q 'previewStatDetailAttachment' "$page" || { echo "FAIL: incentive mobile stats detail attachment preview is missing" >&2; exit 1; }
grep -q 'downloadStatDetailAttachment' "$page" || { echo "FAIL: incentive mobile stats detail attachment download is missing" >&2; exit 1; }
grep -q 'getDownloadUrl' "$page" || { echo "FAIL: incentive mobile list/stat attachments must use normalized download URLs" >&2; exit 1; }
grep -q '统计明细加载失败' "$page" || { echo "FAIL: incentive mobile stats detail failure feedback is missing" >&2; exit 1; }
grep -q 'stat-drill-row' "$wxml" || { echo "FAIL: incentive mobile category stats rows are not drillable" >&2; exit 1; }
grep -q 'stats-detail-panel' "$wxml" || { echo "FAIL: incentive mobile stats detail panel UI is missing" >&2; exit 1; }
grep -q 'stats-detail-customer' "$wxml" || { echo "FAIL: incentive mobile stats detail customer column is missing" >&2; exit 1; }
grep -q 'stats-detail-sub' "$wxml" || { echo "FAIL: incentive mobile stats detail uploader/time metadata is missing" >&2; exit 1; }
grep -q 'previewStatDetailAttachment' "$wxml" || { echo "FAIL: incentive mobile stats detail preview UI is missing" >&2; exit 1; }
grep -q 'downloadStatDetailAttachment' "$wxml" || { echo "FAIL: incentive mobile stats detail download UI is missing" >&2; exit 1; }
grep -q 'previewAttachment' "$page" || { echo "FAIL: incentive mobile list attachment preview action is missing" >&2; exit 1; }
grep -q 'downloadAttachment' "$page" || { echo "FAIL: incentive mobile list attachment download action is missing" >&2; exit 1; }
grep -q 'buildIncentiveFileGroups' "$page" || { echo "FAIL: incentive mobile list attachments are not grouped by PC slot semantics" >&2; exit 1; }
grep -q 'fileGroups: latest ? buildIncentiveFileGroups' "$page" || { echo "FAIL: incentive mobile latest record does not expose PC slot groups" >&2; exit 1; }
grep -q 'fileGroups: buildIncentiveFileGroups(item)' "$page" || { echo "FAIL: incentive mobile child/stat records do not expose PC slot groups" >&2; exit 1; }
grep -q 'record.fileGroups' "$page" || { echo "FAIL: incentive mobile attachment actions cannot target grouped PC slots" >&2; exit 1; }
grep -q 'canDeleteRecords' "$page" || { echo "FAIL: incentive mobile list delete permission state is missing" >&2; exit 1; }
grep -q 'getGrantCheck' "$page" || { echo "FAIL: incentive mobile list does not load delete grant" >&2; exit 1; }
grep -q 'canEditRecords' "$page" || { echo "FAIL: incentive mobile list edit grant state is missing" >&2; exit 1; }
grep -q 'showAppendAction' "$page" || { echo "FAIL: incentive mobile list append visibility must be normalized in JS" >&2; exit 1; }
grep -q 'showDeleteAction' "$page" || { echo "FAIL: incentive mobile list delete visibility must be normalized in JS" >&2; exit 1; }
grep -q 'appendRecord' "$page" || { echo "FAIL: incentive mobile list record append action is missing" >&2; exit 1; }
grep -q "pages/incentive/upload/index?id=" "$page" || { echo "FAIL: incentive mobile list append action does not open upload edit mode" >&2; exit 1; }
grep -q 'removeRecord' "$page" || { echo "FAIL: incentive mobile list record delete action is missing" >&2; exit 1; }
grep -q 'deleteRecord(record.id)' "$page" || { echo "FAIL: incentive mobile list record delete does not call delete API" >&2; exit 1; }
grep -q 'openCustomer(e)' "$page" || { echo "FAIL: incentive mobile list customer archive action is missing" >&2; exit 1; }
grep -Fq '/pages/customer/detail/index?id=${record.customerId}&from=incentive' "$page" || { echo "FAIL: incentive mobile list customer archive route is stale" >&2; exit 1; }
# 【E5 去重 2026-10-05】列表页附件「下载/预览/打开/保存」已收口 utils/attachment.js, 页面经
# fetchRemoteAttachment 接线; 原页面内 wx.downloadFile/wx.previewImage/wx.openDocument/
# getFileSystemManager 字面量检查改为「页面接线 + 共享模块具备能力」两段式(detail/upload 页同理)
grep -q 'fetchRemoteAttachment' "$page" || { echo "FAIL: incentive mobile list attachments cannot download from server" >&2; exit 1; }
grep -q 'wx.downloadFile' "$attachment_util" || { echo "FAIL: incentive mobile list attachments cannot download from server" >&2; exit 1; }
grep -q 'wx.previewImage' "$attachment_util" || { echo "FAIL: incentive mobile list images cannot preview" >&2; exit 1; }
grep -q 'wx.openDocument' "$attachment_util" || { echo "FAIL: incentive mobile list documents cannot open" >&2; exit 1; }
grep -q 'getFileSystemManager' "$attachment_util" || { echo "FAIL: incentive mobile list attachments cannot save locally" >&2; exit 1; }
grep -q 'previewLoading' "$page" || { echo "FAIL: incentive mobile list attachment loading state must be normalized in JS" >&2; exit 1; }
grep -q 'record-file-wall' "$wxml" || { echo "FAIL: incentive mobile list attachment UI is missing" >&2; exit 1; }  # 【#4 2026-10-12】佐证照片墙重锚
grep -q 'record-file-group' "$wxml" || { echo "FAIL: incentive mobile list attachment UI does not render PC slot groups" >&2; exit 1; }
grep -q 'data-group-index' "$wxml" || { echo "FAIL: incentive mobile list grouped attachment actions lack slot index" >&2; exit 1; }
grep -q 'catchtap="previewAttachment"' "$wxml" || { echo "FAIL: incentive mobile list preview UI is missing" >&2; exit 1; }
grep -q 'catchtap="downloadAttachment"' "$wxml" || { echo "FAIL: incentive mobile list download UI is missing" >&2; exit 1; }
grep -q 'incentiveView.previewLoading' "$wxml" || { echo "FAIL: incentive mobile list attachment UI must use normalized preview loading" >&2; exit 1; }
! grep -q 'loading="{{previewing}}"' "$wxml" || { echo "FAIL: incentive mobile list WXML must not bind raw previewing state directly" >&2; exit 1; }
grep -q 'catchtap="appendRecord"' "$wxml" || { echo "FAIL: incentive mobile list append UI is missing" >&2; exit 1; }
grep -q 'catchtap="removeRecord"' "$wxml" || { echo "FAIL: incentive mobile list delete UI is missing" >&2; exit 1; }
grep -q 'item.showAppendAction' "$wxml" || { echo "FAIL: incentive mobile list append UI must use normalized action visibility" >&2; exit 1; }
grep -q 'item.showDeleteAction' "$wxml" || { echo "FAIL: incentive mobile list delete UI must use normalized action visibility" >&2; exit 1; }
grep -q 'child.showAppendAction' "$wxml" || { echo "FAIL: incentive mobile child append UI must use normalized action visibility" >&2; exit 1; }
grep -q 'child.showDeleteAction' "$wxml" || { echo "FAIL: incentive mobile child delete UI must use normalized action visibility" >&2; exit 1; }
! grep -q 'wx:if="{{item.canEdit}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw item edit permission directly" >&2; exit 1; }
! grep -q 'wx:if="{{item.canDelete}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw item delete permission directly" >&2; exit 1; }
! grep -q 'wx:if="{{child.canEdit}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw child edit permission directly" >&2; exit 1; }
! grep -q 'wx:if="{{child.canDelete}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw child delete permission directly" >&2; exit 1; }
grep -q 'showReadStatsPanel' "$page" || { echo "FAIL: incentive mobile read stats panel visibility must be normalized in JS" >&2; exit 1; }
grep -q 'showReadRankingPanel' "$page" || { echo "FAIL: incentive mobile read ranking panel visibility must be normalized in JS" >&2; exit 1; }
grep -q 'incentiveView.showReadStatsPanel' "$wxml" || { echo "FAIL: incentive mobile read stats panel must use normalized visibility" >&2; exit 1; }
grep -q 'incentiveView.showReadRankingPanel' "$wxml" || { echo "FAIL: incentive mobile read ranking panel must use normalized visibility" >&2; exit 1; }
! grep -q 'wx:if="{{canViewReadStats}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw read stats permission directly" >&2; exit 1; }
! grep -q 'wx:if="{{canViewReadRanking}}"' "$wxml" || { echo "FAIL: incentive mobile WXML must not bind raw read ranking permission directly" >&2; exit 1; }
grep -q 'catchtap="openCustomer"' "$wxml" || { echo "FAIL: incentive mobile list customer archive UI is missing" >&2; exit 1; }
grep -q '客户档案' "$wxml" || { echo "FAIL: incentive mobile list customer archive copy is missing" >&2; exit 1; }
grep -q 'filePreId' "$upload_page" || { echo "FAIL: incentive upload pre-file slot is missing" >&2; exit 1; }
grep -q 'fileMidId' "$upload_page" || { echo "FAIL: incentive upload mid-file slot is missing" >&2; exit 1; }
grep -q 'filePostId' "$upload_page" || { echo "FAIL: incentive upload post-file slot is missing" >&2; exit 1; }
grep -q 'fileTrackId' "$upload_page" || { echo "FAIL: incentive upload track-file slot is missing" >&2; exit 1; }
grep -q 'applyCategorySlotMode' "$upload_page" || { echo "FAIL: incentive upload slots do not switch by PC category semantics" >&2; exit 1; }
grep -q "singleSlotLabel(code)" "$upload_page" || { echo "FAIL: incentive upload single-slot labels do not follow PC category semantics" >&2; exit 1; }
grep -q "slot.key === 'filePreId'" "$upload_page" || { echo "FAIL: incentive upload single-slot mode must write the PC filePreId slot" >&2; exit 1; }
grep -q 'MAX_FILES_PER_SLOT = 5' "$upload_page" || { echo "FAIL: incentive upload per-slot multi-file limit does not match PC" >&2; exit 1; }
grep -q "slot.files || \\[\\]" "$upload_page" || { echo "FAIL: incentive upload slots no longer preserve multiple files per PC slot" >&2; exit 1; }
grep -q 'slotCountText' "$upload_page" || { echo "FAIL: incentive upload slot count labels are missing" >&2; exit 1; }
# 【PR-13 2026-09-19】flatMap 增加了 slotIndex 参数(逐文件状态写回定位),断言同步
grep -q 'flatMap((slot, slotIndex)' "$upload_page" || { echo "FAIL: incentive upload submit must flatten multi-file PC slots" >&2; exit 1; }
grep -Fq 'if (slot.hidden) return []' "$upload_page" || { echo "FAIL: incentive upload submit must ignore hidden non-PC slots" >&2; exit 1; }
grep -q 'idsBySlot' "$upload_page" || { echo "FAIL: incentive upload submit must group multiple uploaded files by PC slot" >&2; exit 1; }
grep -q 'loadEditRecord' "$upload_page" || { echo "FAIL: incentive upload edit mode does not load existing PC record" >&2; exit 1; }
grep -q 'fillEditRecord' "$upload_page" || { echo "FAIL: incentive upload edit mode does not hydrate existing PC fields" >&2; exit 1; }
grep -q 'existingFiles' "$upload_page" || { echo "FAIL: incentive upload edit mode does not preserve existing slot files" >&2; exit 1; }
grep -q 'fileUrl' "$upload_page" || { echo "FAIL: incentive upload edit mode does not preserve path-backed existing files" >&2; exit 1; }
grep -q 'isDownloadReference' "$upload_page" || { echo "FAIL: incentive upload edit mode must reject MIME-only fake download references" >&2; exit 1; }
grep -q 'updateRecord(this.data.editId, payload)' "$upload_page" || { echo "FAIL: incentive upload edit mode does not call updateRecord PUT" >&2; exit 1; }
grep -q 'fetchExistingFile' "$upload_page" || { echo "FAIL: incentive upload edit mode cannot preview/download existing server files" >&2; exit 1; }
grep -q 'getDownloadUrl' "$upload_page" || { echo "FAIL: incentive upload edit mode must use normalized file download URLs" >&2; exit 1; }
grep -q 'buildPeriodOptions' "$upload_page" || { echo "FAIL: incentive upload period options are missing" >&2; exit 1; }
grep -q 'selectedPeriodIndex' "$upload_page" || { echo "FAIL: incentive upload selected period state is missing" >&2; exit 1; }
grep -q 'choosePeriod' "$upload_page" || { echo "FAIL: incentive upload period picker handler is missing" >&2; exit 1; }
grep -q 'period: selectedPeriod' "$upload_page" || { echo "FAIL: incentive upload create payload does not use selected period" >&2; exit 1; }
grep -q 'canToggleShare' "$upload_page" || { echo "FAIL: incentive upload edit mode must track PC share-toggle owner boundary" >&2; exit 1; }
grep -q 'delete payload.categoryId' "$upload_page" || { echo "FAIL: incentive upload edit mode must preserve original PC category" >&2; exit 1; }
grep -q 'delete payload.period' "$upload_page" || { echo "FAIL: incentive upload edit mode must preserve original PC period" >&2; exit 1; }
grep -q 'delete payload.shared' "$upload_page" || { echo "FAIL: incentive upload edit mode must preserve shared state when editor is not owner" >&2; exit 1; }
grep -q 'industry:' "$upload_page" || { echo "FAIL: incentive upload industry field is missing" >&2; exit 1; }
grep -q 'relatedProducts:' "$upload_page" || { echo "FAIL: incentive upload relatedProducts field is missing" >&2; exit 1; }
grep -q 'specificProduct:' "$upload_page" || { echo "FAIL: incentive upload specificProduct field is missing" >&2; exit 1; }
grep -q 'preInstallQuestion:' "$upload_page" || { echo "FAIL: incentive upload preInstallQuestion field is missing" >&2; exit 1; }
grep -q 'shared:' "$upload_page" || { echo "FAIL: incentive upload shared field is missing" >&2; exit 1; }
grep -q 'relatedSubDisabled' "$upload_page" || { echo "FAIL: incentive upload product subcategory disabled state must match PC relatedCat dependency" >&2; exit 1; }
grep -q 'relatedSubPickerClass' "$upload_page" || { echo "FAIL: incentive upload product subcategory disabled style must be normalized in JS" >&2; exit 1; }
grep -q 'previewSelectedFile' "$upload_page" || { echo "FAIL: incentive upload selected-file preview action is missing" >&2; exit 1; }
grep -q 'downloadSelectedFile' "$upload_page" || { echo "FAIL: incentive upload selected-file save action is missing" >&2; exit 1; }
grep -q 'removeSelectedFile' "$upload_page" || { echo "FAIL: incentive upload selected-file remove action is missing" >&2; exit 1; }
grep -q 'onProgressUpdate' "$ROOT/aigicrm-miniprogram/api/file.js" || { echo "FAIL: mobile file upload API must expose native upload progress" >&2; exit 1; }
grep -q 'uploadProgressFile' "$upload_page" || { echo "FAIL: incentive upload progress file name state is missing" >&2; exit 1; }
grep -q 'uploadProgressWidth' "$upload_page" || { echo "FAIL: incentive upload progress width must be normalized in JS" >&2; exit 1; }
grep -q 'showUploadProgress' "$upload_page" || { echo "FAIL: incentive upload progress visibility must be normalized in JS" >&2; exit 1; }
grep -q 'fileActionLoading' "$upload_page" || { echo "FAIL: incentive upload selected-file action loading state must be normalized in JS" >&2; exit 1; }
grep -q 'cancelUpload' "$upload_page" || { echo "FAIL: incentive upload cancel action is missing" >&2; exit 1; }
grep -q "cancelText: data.editId ? '取消追加' : '取消上传'" "$upload_page" || { echo "FAIL: incentive upload cancel copy does not distinguish append/upload" >&2; exit 1; }
grep -q 'cancelDisabled' "$upload_page" || { echo "FAIL: incentive upload cancel state is not normalized in JS" >&2; exit 1; }
grep -q 'setUploadData' "$upload_page" || { echo "FAIL: incentive upload state helper must refresh normalized upload view" >&2; exit 1; }
grep -q 'submitLoading' "$upload_page" || { echo "FAIL: incentive upload submit loading state must be normalized in JS" >&2; exit 1; }
grep -q 'canSubmitUpload' "$upload_page" || { echo "FAIL: incentive upload submit availability must follow PC canSubmit semantics" >&2; exit 1; }
grep -q 'submitDisabled' "$upload_page" || { echo "FAIL: incentive upload submit disabled state must be normalized in JS" >&2; exit 1; }
grep -q 'submitText' "$upload_page" || { echo "FAIL: incentive upload submit text must be normalized in JS" >&2; exit 1; }
grep -q 'incentiveDetailLabel' "$upload_page" || { echo "FAIL: incentive upload detail label must follow PC category semantics" >&2; exit 1; }
grep -q 'incentiveRemarkLabel' "$upload_page" || { echo "FAIL: incentive upload remark label must follow PC category semantics" >&2; exit 1; }
grep -q "code === 'plan'" "$upload_page" || { echo "FAIL: incentive upload detail label must handle PC plan category semantics" >&2; exit 1; }
grep -q "code === 'visit' || code === 'exchange'" "$upload_page" || { echo "FAIL: incentive upload detail label must handle PC visit/exchange category semantics" >&2; exit 1; }
grep -q 'periodDisabled' "$upload_page" || { echo "FAIL: incentive upload edit-mode period lock must be normalized in JS" >&2; exit 1; }
grep -q 'showPeriodArrow' "$upload_page" || { echo "FAIL: incentive upload period picker arrow visibility must be normalized in JS" >&2; exit 1; }
grep -q 'showPeriodPreserveHint' "$upload_page" || { echo "FAIL: incentive upload edit-mode period hint visibility must be normalized in JS" >&2; exit 1; }
grep -q 'upload edit mode existing file must download before preview/save' "$automator_script" || { echo "FAIL: incentive automator must verify upload edit-mode existing-file fetches" >&2; exit 1; }
grep -q 'upload edit mode existing image must call previewImage' "$automator_script" || { echo "FAIL: incentive automator must verify upload edit-mode existing image preview" >&2; exit 1; }
grep -q 'upload edit mode existing file download must call saveFile' "$automator_script" || { echo "FAIL: incentive automator must verify upload edit-mode existing-file save" >&2; exit 1; }
grep -q 'buildUploadViewState' "$upload_page" || { echo "FAIL: incentive upload display state must be normalized in JS" >&2; exit 1; }
grep -q 'refreshUploadViewState' "$upload_page" || { echo "FAIL: incentive upload view state refresh is missing" >&2; exit 1; }
grep -q 'selectedFileSlotsView' "$upload_page" || { echo "FAIL: incentive upload selected-file visibility must be normalized in JS" >&2; exit 1; }
grep -q 'slotOptionsView' "$upload_page" || { echo "FAIL: incentive upload slot classes must be normalized in JS" >&2; exit 1; }
grep -q 'auditRows' "$upload_page" || { echo "FAIL: incentive upload audit rows must be normalized in JS like PC" >&2; exit 1; }
grep -q 'auditRecord: record' "$upload_page" || { echo "FAIL: incentive upload edit mode must expose PC audit info" >&2; exit 1; }
grep -q 'chooseImage' "$upload_page" || { echo "FAIL: incentive upload image picker action is missing" >&2; exit 1; }
grep -q 'wx.chooseMedia' "$upload_page" || { echo "FAIL: incentive upload cannot pick camera or album images" >&2; exit 1; }
grep -q "mediaType: \\['image'\\]" "$upload_page" || { echo "FAIL: incentive upload image picker is not limited to images" >&2; exit 1; }
grep -q 'wx.previewImage' "$upload_page" || { echo "FAIL: incentive upload selected images cannot preview" >&2; exit 1; }
grep -q 'wx.openDocument' "$upload_page" || { echo "FAIL: incentive upload selected documents cannot open" >&2; exit 1; }
# 【E5 去重 2026-10-05】上传页保存能力改用共享 saveDownloadedFileToDisk(能力本体在 utils/attachment.js)
grep -q 'saveDownloadedFileToDisk' "$upload_page" || { echo "FAIL: incentive upload selected files cannot save locally" >&2; exit 1; }
grep -q '文件槽位' "$upload_wxml" || { echo "FAIL: incentive upload slot UI is missing" >&2; exit 1; }
grep -q 'wx:for="{{uploadView.slotOptionsView}}"' "$upload_wxml" || { echo "FAIL: incentive upload UI does not render normalized PC slot options" >&2; exit 1; }
grep -q 'wx:if="{{item.visible}}"' "$upload_wxml" || { echo "FAIL: incentive upload UI does not hide non-PC slots from normalized state" >&2; exit 1; }
grep -q 'wx:for="{{uploadView.selectedFileSlotsView}}"' "$upload_wxml" || { echo "FAIL: incentive upload selected-file UI does not use normalized visible slots" >&2; exit 1; }
grep -q '相关产品' "$upload_wxml" || { echo "FAIL: incentive upload related product UI is missing" >&2; exit 1; }
grep -q 'disabled="{{uploadView.relatedSubDisabled}}"' "$upload_wxml" || { echo "FAIL: incentive upload product subcategory picker must be disabled until product category is selected" >&2; exit 1; }
grep -q 'uploadView.relatedSubPickerClass' "$upload_wxml" || { echo "FAIL: incentive upload product subcategory picker class must be normalized" >&2; exit 1; }
grep -q '记录周期' "$upload_wxml" || { echo "FAIL: incentive upload period UI is missing" >&2; exit 1; }
grep -q 'bindchange="choosePeriod"' "$upload_wxml" || { echo "FAIL: incentive upload period picker UI is missing" >&2; exit 1; }
grep -q '保留原记录周期' "$upload_wxml" || { echo "FAIL: incentive upload edit period preserve copy is missing" >&2; exit 1; }
grep -q 'disabled="{{uploadView.periodDisabled}}"' "$upload_wxml" || { echo "FAIL: incentive upload period picker must use normalized edit-mode disabled state" >&2; exit 1; }
grep -q 'uploadView.showPeriodArrow' "$upload_wxml" || { echo "FAIL: incentive upload period picker arrow must use normalized visibility" >&2; exit 1; }
grep -q 'uploadView.showPeriodPreserveHint' "$upload_wxml" || { echo "FAIL: incentive upload period preserve hint must use normalized visibility" >&2; exit 1; }
! grep -Eq '!!editId|!editId|wx:if="\{\{editId\}\}"' "$upload_wxml" || { echo "FAIL: incentive upload WXML must not branch directly on editId" >&2; exit 1; }
grep -q 'uploadView.detailLabel' "$upload_wxml" || { echo "FAIL: incentive upload detail field label must use normalized PC category label" >&2; exit 1; }
grep -q 'uploadView.remarkLabel' "$upload_wxml" || { echo "FAIL: incentive upload remark field label must use normalized PC category label" >&2; exit 1; }
grep -q 'showCustomerConfirm' "$upload_page" || { echo "FAIL: incentive upload selected-customer confirmation visibility must be normalized in JS" >&2; exit 1; }
grep -q 'selectedCustomerName' "$upload_page" || { echo "FAIL: incentive upload selected-customer confirmation name is missing" >&2; exit 1; }
grep -q 'selectedCustomerMeta' "$upload_page" || { echo "FAIL: incentive upload selected-customer confirmation metadata is missing" >&2; exit 1; }
grep -q 'incentiveSectionTitle' "$upload_page" || { echo "FAIL: incentive upload must expose PC section semantics for the selected category" >&2; exit 1; }
grep -q 'categoryScoreText' "$upload_page" || { echo "FAIL: incentive upload must expose selected category score like PC" >&2; exit 1; }
grep -q 'showCategorySummary' "$upload_page" || { echo "FAIL: incentive upload selected-category summary visibility must be normalized in JS" >&2; exit 1; }
grep -q 'showSpecificProduct' "$upload_page" || { echo "FAIL: incentive upload specific product visibility must follow PC trial-category semantics" >&2; exit 1; }
grep -q 'showRemarkField' "$upload_page" || { echo "FAIL: incentive upload remark visibility must follow PC trial-category semantics" >&2; exit 1; }
grep -q 'uploadView.categorySectionTitle' "$upload_wxml" || { echo "FAIL: incentive upload selected-category section summary is missing" >&2; exit 1; }
grep -q 'uploadView.categoryScoreText' "$upload_wxml" || { echo "FAIL: incentive upload selected-category score summary is missing" >&2; exit 1; }
grep -q 'uploadView.showCustomerConfirm' "$upload_wxml" || { echo "FAIL: incentive upload selected-customer confirmation UI is missing" >&2; exit 1; }
grep -q 'uploadView.selectedCustomerName' "$upload_wxml" || { echo "FAIL: incentive upload selected-customer confirmation name UI is missing" >&2; exit 1; }
grep -q 'customer-confirm' "$upload_wxml" || { echo "FAIL: incentive upload selected-customer confirmation style hook is missing" >&2; exit 1; }
grep -q 'uploadView.showSpecificProduct' "$upload_wxml" || { echo "FAIL: incentive upload specific product field must use normalized visibility" >&2; exit 1; }
grep -q 'uploadView.showRemarkField' "$upload_wxml" || { echo "FAIL: incentive upload remark field must use normalized visibility" >&2; exit 1; }
grep -q '.category-summary' "$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.wxss" || { echo "FAIL: incentive upload selected-category summary style is missing" >&2; exit 1; }
! grep -q '安装前问题' "$upload_wxml" || { echo "FAIL: incentive upload WXML must not keep fixed pre-install label" >&2; exit 1; }
grep -q '团队共享' "$upload_wxml" || { echo "FAIL: incentive upload share UI is missing" >&2; exit 1; }
grep -q 'disabled="{{uploadView.shareLocked}}"' "$upload_wxml" || { echo "FAIL: incentive upload share switch must be disabled from normalized non-owner edit state" >&2; exit 1; }
grep -q '仅上传人可修改共享状态' "$upload_wxml" || { echo "FAIL: incentive upload non-owner share preserve copy is missing" >&2; exit 1; }
grep -q '审计信息' "$upload_wxml" || { echo "FAIL: incentive upload edit audit UI is missing" >&2; exit 1; }
grep -q 'wx:for="{{uploadView.auditRows}}"' "$upload_wxml" || { echo "FAIL: incentive upload audit UI must render normalized audit rows" >&2; exit 1; }
grep -q 'catchtap="previewSelectedFile"' "$upload_wxml" || { echo "FAIL: incentive upload selected-file open UI is missing" >&2; exit 1; }
grep -q 'catchtap="downloadSelectedFile"' "$upload_wxml" || { echo "FAIL: incentive upload selected-file save UI is missing" >&2; exit 1; }
grep -q 'uploadView.showUploadProgress' "$upload_wxml" || { echo "FAIL: incentive upload progress panel must use normalized visibility" >&2; exit 1; }
grep -q 'uploadView.uploadProgressWidth' "$upload_wxml" || { echo "FAIL: incentive upload progress bar must use normalized width" >&2; exit 1; }
grep -q 'uploadView.uploadProgressText' "$upload_wxml" || { echo "FAIL: incentive upload progress text must use normalized copy" >&2; exit 1; }
grep -q 'uploadView.fileActionLoading' "$upload_wxml" || { echo "FAIL: incentive upload selected-file actions must use normalized loading state" >&2; exit 1; }
! grep -q 'loading="{{fileActionLoading}}"' "$upload_wxml" || { echo "FAIL: incentive upload WXML must not bind raw file action loading directly" >&2; exit 1; }
grep -q 'catchtap="chooseImage"' "$upload_wxml" || { echo "FAIL: incentive upload image picker UI is missing" >&2; exit 1; }
grep -q 'catchtap="chooseFile"' "$upload_wxml" || { echo "FAIL: incentive upload file picker UI is missing" >&2; exit 1; }
grep -q 'selected-file-thumb' "$upload_wxml" || { echo "FAIL: incentive upload selected image thumbnail is missing" >&2; exit 1; }
grep -q 'wx:for="{{slot.files}}"' "$upload_wxml" || { echo "FAIL: incentive upload selected file list no longer renders multiple files per slot" >&2; exit 1; }
grep -q 'data-file-index' "$upload_wxml" || { echo "FAIL: incentive upload selected file actions do not target per-slot file index" >&2; exit 1; }
grep -q 'item.countText' "$upload_wxml" || { echo "FAIL: incentive upload slot count UI is missing" >&2; exit 1; }
grep -q 'class="form-actions"' "$upload_wxml" || { echo "FAIL: incentive upload footer actions are not grouped like PC dialog actions" >&2; exit 1; }
grep -q 'bindtap="cancelUpload"' "$upload_wxml" || { echo "FAIL: incentive upload cancel UI is missing" >&2; exit 1; }
grep -q 'uploadView.cancelDisabled' "$upload_wxml" || { echo "FAIL: incentive upload cancel button does not bind normalized disabled state" >&2; exit 1; }
grep -q 'uploadView.submitLoading' "$upload_wxml" || { echo "FAIL: incentive upload submit button must use normalized loading state" >&2; exit 1; }
grep -q 'disabled="{{uploadView.submitDisabled}}"' "$upload_wxml" || { echo "FAIL: incentive upload submit button must use normalized disabled state" >&2; exit 1; }
grep -q 'uploadView.submitText' "$upload_wxml" || { echo "FAIL: incentive upload submit button must use normalized text" >&2; exit 1; }
grep -q '预览' "$upload_wxml" || { echo "FAIL: incentive upload selected-file preview copy is unclear" >&2; exit 1; }
grep -q '下载' "$upload_wxml" || { echo "FAIL: incentive upload selected-file download copy is unclear" >&2; exit 1; }
grep -q '选图片' "$upload_wxml" || { echo "FAIL: incentive upload image picker copy is unclear" >&2; exit 1; }
grep -q '选文件' "$upload_wxml" || { echo "FAIL: incentive upload file picker copy is unclear" >&2; exit 1; }
grep -q '预览图片/打开文档' "$upload_wxml" "$upload_page" || { echo "FAIL: incentive upload selected-file guidance is missing" >&2; exit 1; }
grep -q '.cancel-action' "$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.wxss" || { echo "FAIL: incentive upload cancel button style is missing" >&2; exit 1; }
grep -q '.submit-action' "$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.wxss" || { echo "FAIL: incentive upload submit action style is missing" >&2; exit 1; }
! grep -Eq "selected[A-Za-z]+Index >= 0 \\?|fileSlots\\[selectedSlotIndex\\]|selectedSlotIndex === index|editId && !canToggleShare|file\\.fileKind === 'image'|fileIndex \\+ 1" "$upload_wxml" || { echo "FAIL: incentive upload WXML must not keep picker/slot/file branching logic" >&2; exit 1; }
! grep -Eq "wx:for-index=\"fileIndex\"" "$upload_wxml" || { echo "FAIL: incentive upload WXML must not derive selected-file indexes in template" >&2; exit 1; }
! grep -q '{{loading}}' "$upload_wxml" || { echo "FAIL: incentive upload WXML must not bind raw loading state directly" >&2; exit 1; }
grep -q 'buildIncentiveFiles' "$detail_page" || { echo "FAIL: incentive detail attachment parsing is missing" >&2; exit 1; }
grep -q 'getDownloadUrl' "$detail_page" || { echo "FAIL: incentive detail attachments must use normalized download URLs" >&2; exit 1; }
grep -q 'buildDetailView' "$detail_page" || { echo "FAIL: incentive detail display state must be normalized in JS" >&2; exit 1; }
grep -q 'setDetailData' "$detail_page" || { echo "FAIL: incentive detail view state refresh helper is missing" >&2; exit 1; }
grep -q 'showLoading' "$detail_page" || { echo "FAIL: incentive detail loading visibility must be normalized in JS" >&2; exit 1; }
grep -q 'previewLoading' "$detail_page" || { echo "FAIL: incentive detail attachment loading state must be normalized in JS" >&2; exit 1; }
grep -q 'showEmpty' "$detail_page" || { echo "FAIL: incentive detail empty state must be normalized in JS" >&2; exit 1; }
grep -q 'detailView' "$detail_wxml" || { echo "FAIL: incentive detail WXML must render normalized detail fields" >&2; exit 1; }
grep -q 'detailView.showLoading' "$detail_wxml" || { echo "FAIL: incentive detail loading card must use normalized visibility" >&2; exit 1; }
grep -q 'detailView.hasRecord' "$detail_wxml" || { echo "FAIL: incentive detail record section must use normalized visibility" >&2; exit 1; }
grep -q 'detailView.showEmpty' "$detail_wxml" || { echo "FAIL: incentive detail empty card must use normalized visibility" >&2; exit 1; }
# 【2026-09-20 小程序同步轮】详情页对齐 PC search.vue 定稿:三联拨盘 + 记录卡 metabar + 附件凭证平铺大瓦片。
# 旧 infoRows 信息表与按槽位分组的 attachment-timeline,由 metabar 内联元信息条与「附件凭证 · N 项」3:2 网格取代。
grep -q 'metaChips' "$detail_page" || { echo "FAIL: incentive detail base fields must be normalized as metabar chips in JS" >&2; exit 1; }
grep -q 'detailView.items' "$detail_wxml" || { echo "FAIL: incentive detail record cards must render normalized items" >&2; exit 1; }
grep -q 'item.chips' "$detail_wxml" || { echo "FAIL: incentive detail metabar must render normalized chips" >&2; exit 1; }
grep -q '附件凭证' "$detail_page" || { echo "FAIL: incentive detail attachment count head must use PC wording" >&2; exit 1; }
grep -q 'showFilesBlock' "$detail_page" || { echo "FAIL: incentive detail attachment visibility must be normalized in JS" >&2; exit 1; }
grep -q 'countText' "$detail_page" || { echo "FAIL: incentive detail attachment count text must be normalized in JS" >&2; exit 1; }
grep -q 'item.files' "$detail_wxml" || { echo "FAIL: incentive detail attachment grid must render normalized files" >&2; exit 1; }
grep -q 'files-grid' "$detail_wxml" "$ROOT/aigicrm-miniprogram/pages/incentive/detail/index.wxss" || { echo "FAIL: incentive detail attachment tiles must use the 3:2 grid layout" >&2; exit 1; }
grep -q 'getScoreByCustomer' "$detail_page" || { echo "FAIL: incentive detail does not load customer score" >&2; exit 1; }
grep -q 'canDeleteRecord' "$detail_page" || { echo "FAIL: incentive detail delete permission state is missing" >&2; exit 1; }
grep -q 'showDeleteAction' "$detail_page" || { echo "FAIL: incentive detail delete action visibility must be normalized in JS" >&2; exit 1; }
grep -q 'getGrantCheck' "$detail_page" || { echo "FAIL: incentive detail does not load delete grant" >&2; exit 1; }
grep -q 'removeRecord' "$detail_page" || { echo "FAIL: incentive detail record delete action is missing" >&2; exit 1; }
grep -q 'deleteRecord(record.id)' "$detail_page" || { echo "FAIL: incentive detail record delete does not call delete API" >&2; exit 1; }
grep -q '客户累计积分' "$detail_page" || { echo "FAIL: incentive detail customer score row is missing from normalized state" >&2; exit 1; }
grep -q '相关产品' "$detail_page" || { echo "FAIL: incentive detail related product row is missing from normalized state" >&2; exit 1; }
grep -q '具体产品' "$detail_page" || { echo "FAIL: incentive detail specific product row is missing from normalized state" >&2; exit 1; }
grep -q "'周期'" "$detail_page" || { echo "FAIL: incentive detail period row is missing from normalized state" >&2; exit 1; }
grep -q 'preInstallQuestionText' "$detail_wxml" || { echo "FAIL: incentive detail description field UI is missing" >&2; exit 1; }
grep -q 'showTrialRemark' "$detail_wxml" || { echo "FAIL: incentive detail trial remark boundary is missing" >&2; exit 1; }
grep -q 'detailLabelOf' "$detail_page" || { echo "FAIL: incentive detail label does not follow PC category semantics" >&2; exit 1; }
grep -q 'remarkLabelOf' "$detail_page" || { echo "FAIL: incentive detail remark label does not follow PC category semantics" >&2; exit 1; }
grep -q 'incentiveCodeOf' "$detail_page" || { echo "FAIL: incentive detail cannot classify record categories for mobile labels" >&2; exit 1; }
# 【2026-09-20 小程序同步轮】三联拨盘:原生 picker-view 三列(iOS 滚轮),钉住 PC 定稿语义防回退——
# 位置语义(拨到哪显示哪)/整月兜底/每月完整天数(闰年自动)/年份自增/回到今天/实时时钟/「此时间无数据」空态
grep -q '<picker-view' "$detail_wxml" || { echo "FAIL: incentive detail dial must use native picker-view columns" >&2; exit 1; }
grep -q 'bindchange="onDialChange"' "$detail_wxml" || { echo "FAIL: incentive detail dial change handler is missing" >&2; exit 1; }
grep -q 'detailView.dialYears' "$detail_wxml" || { echo "FAIL: incentive detail year dial must render normalized years" >&2; exit 1; }
grep -q 'detailView.dialMonths' "$detail_wxml" || { echo "FAIL: incentive detail month dial must render normalized months" >&2; exit 1; }
grep -q 'detailView.dialDays' "$detail_wxml" || { echo "FAIL: incentive detail day dial must render normalized days" >&2; exit 1; }
grep -q 'onDialChange' "$detail_page" || { echo "FAIL: incentive detail dial position semantics handler is missing" >&2; exit 1; }
grep -q 'buildDialYears' "$detail_page" || { echo "FAIL: incentive detail year dial auto-increment builder is missing" >&2; exit 1; }
grep -q 'new Date(year, month, 0).getDate()' "$detail_page" || { echo "FAIL: incentive detail day dial must generate full month days" >&2; exit 1; }
grep -q "'整月'" "$detail_page" || { echo "FAIL: incentive detail day dial must keep whole-month fallback entry" >&2; exit 1; }
grep -q 'goToday' "$detail_page" || { echo "FAIL: incentive detail back-to-today shortcut is missing" >&2; exit 1; }
grep -q '回到今天' "$detail_wxml" || { echo "FAIL: incentive detail back-to-today UI is missing" >&2; exit 1; }
grep -q '此时间无数据' "$detail_page" || { echo "FAIL: incentive detail dial empty state copy is missing" >&2; exit 1; }
grep -q 'paneEmptyTitle' "$detail_wxml" || { echo "FAIL: incentive detail dial empty state must render normalized title" >&2; exit 1; }
grep -q 'nowTimeText' "$detail_wxml" || { echo "FAIL: incentive detail live clock text is missing" >&2; exit 1; }
grep -q 'setInterval' "$detail_page" || { echo "FAIL: incentive detail live clock ticker is missing" >&2; exit 1; }
grep -q 'stopClock' "$detail_page" || { echo "FAIL: incentive detail live clock must be cleaned up on hide/unload" >&2; exit 1; }
grep -q 'relativeDayText' "$detail_page" || { echo "FAIL: incentive detail metabar relative-day label is missing" >&2; exit 1; }
! grep -Eq "customerScore === null|record\\.remark && isTrialRecord|!record\\.preInstallQuestion|record\\.name \\|\\||record\\.score \\|\\||record\\.customerName \\|\\|" "$detail_wxml" || { echo "FAIL: incentive detail WXML must not keep score/detail branching logic" >&2; exit 1; }
! grep -Eq "record\\.relatedProducts|record\\.specificProduct|record\\.period|record\\.preInstallQuestion|record\\.remark" "$detail_wxml" || { echo "FAIL: incentive detail WXML must not keep direct record field rendering" >&2; exit 1; }
! grep -Eq "fileGroups\\.length|group\\.files\\.length|wx:for-index=\"groupIndex\"|wx:for-index=\"fileIndex\"" "$detail_wxml" || { echo "FAIL: incentive detail WXML must not keep attachment fallback/count/index logic" >&2; exit 1; }
! grep -q '{{loading}}' "$detail_wxml" || { echo "FAIL: incentive detail WXML must not bind raw loading state directly" >&2; exit 1; }
! grep -q '{{record}}' "$detail_wxml" || { echo "FAIL: incentive detail WXML must not bind raw record visibility directly" >&2; exit 1; }
grep -q 'bindtap="removeRecord"' "$detail_wxml" || { echo "FAIL: incentive detail delete UI is missing" >&2; exit 1; }
grep -q 'item.showDeleteAction' "$detail_wxml" || { echo "FAIL: incentive detail delete UI must use normalized per-record action visibility" >&2; exit 1; }
! grep -q 'wx:if="{{canDeleteRecord}}"' "$detail_wxml" || { echo "FAIL: incentive detail WXML must not bind raw delete permission directly" >&2; exit 1; }
# 【E5 去重 2026-10-05】详情页与列表页同口径: 附件能力经 fetchRemoteAttachment 共用 utils/attachment.js
# (下载/预览/打开/保存的 wx API 能力本体已在上方列表页检查块对 $attachment_util 校验)
grep -q 'fetchRemoteAttachment' "$detail_page" || { echo "FAIL: incentive detail attachments cannot be downloaded from server" >&2; exit 1; }
grep -q 'previewAttachment' "$detail_wxml" || { echo "FAIL: incentive detail preview action is missing" >&2; exit 1; }
grep -q 'downloadAttachment' "$detail_wxml" || { echo "FAIL: incentive detail download action is missing" >&2; exit 1; }
grep -q 'detailView.previewLoading' "$detail_wxml" || { echo "FAIL: incentive detail attachment UI must use normalized preview loading" >&2; exit 1; }
! grep -q 'loading="{{previewing}}"' "$detail_wxml" || { echo "FAIL: incentive detail WXML must not bind raw previewing state directly" >&2; exit 1; }
grep -q 'getRecords({ customerId' "$timeline_page" || { echo "FAIL: customer timeline does not load incentive records" >&2; exit 1; }
grep -q 'groupTimeline' "$timeline_page" || { echo "FAIL: customer timeline does not group records by month like PC detail" >&2; exit 1; }
grep -q 'timelineGroups' "$timeline_page" || { echo "FAIL: customer timeline grouped state is missing" >&2; exit 1; }
grep -q 'buildTimelineItemView' "$timeline_page" || { echo "FAIL: customer timeline WXML state must be normalized in JS" >&2; exit 1; }
grep -q 'buildTimelineView' "$timeline_page" || { echo "FAIL: customer timeline shell state must be normalized in JS" >&2; exit 1; }
grep -q 'setTimelineData' "$timeline_page" || { echo "FAIL: customer timeline normalized state refresh helper is missing" >&2; exit 1; }
grep -q 'showFiles' "$timeline_page" || { echo "FAIL: customer timeline attachment visibility must be normalized in JS" >&2; exit 1; }
grep -q 'metaTags' "$timeline_page" || { echo "FAIL: customer timeline incentive meta tags must be normalized in JS" >&2; exit 1; }
grep -q 'showEmpty' "$timeline_page" || { echo "FAIL: customer timeline empty state must be normalized in JS" >&2; exit 1; }
grep -q 'preInstallQuestionText' "$timeline_page" || { echo "FAIL: customer timeline detail text must be normalized in JS" >&2; exit 1; }
grep -q 'remarkText' "$timeline_page" || { echo "FAIL: customer timeline remark text must be normalized in JS" >&2; exit 1; }
grep -q 'goIncentiveDetail' "$timeline_page" || { echo "FAIL: customer timeline cannot open incentive detail records" >&2; exit 1; }
grep -q 'detailLabelOf' "$timeline_page" || { echo "FAIL: customer timeline detail labels do not follow PC category semantics" >&2; exit 1; }
grep -q 'remarkLabelOf' "$timeline_page" || { echo "FAIL: customer timeline remark labels do not follow PC category semantics" >&2; exit 1; }
grep -q 'incentiveCodeOf' "$timeline_page" || { echo "FAIL: customer timeline cannot classify incentive categories for mobile labels" >&2; exit 1; }
grep -q 'preInstallQuestion' "$timeline_page" || { echo "FAIL: customer timeline does not keep incentive detail descriptions" >&2; exit 1; }
grep -q 'createByName' "$timeline_page" || { echo "FAIL: customer timeline normal logs do not preserve PC creator names" >&2; exit 1; }
grep -q 'isTrialRecord' "$timeline_page" || { echo "FAIL: customer timeline does not keep trial remark boundary" >&2; exit 1; }
grep -q 'wx.downloadFile' "$timeline_page" || { echo "FAIL: customer timeline attachments cannot be downloaded from server" >&2; exit 1; }
grep -q 'getDownloadUrl' "$timeline_page" || { echo "FAIL: customer timeline attachments must use normalized download URLs" >&2; exit 1; }
grep -q 'wx.previewImage' "$timeline_page" || { echo "FAIL: customer timeline images cannot preview" >&2; exit 1; }
grep -q 'wx.openDocument' "$timeline_page" || { echo "FAIL: customer timeline documents cannot open" >&2; exit 1; }
grep -q 'getFileSystemManager' "$timeline_page" || { echo "FAIL: customer timeline attachments cannot save locally" >&2; exit 1; }
grep -q 'previewLoading' "$timeline_page" || { echo "FAIL: customer timeline attachment loading state must be normalized in JS" >&2; exit 1; }
grep -q 'timeline-month' "$timeline_wxml" || { echo "FAIL: customer timeline monthly grouped UI is missing" >&2; exit 1; }
grep -q 'timelineView.showLoading' "$timeline_wxml" || { echo "FAIL: customer timeline loading card must use normalized visibility" >&2; exit 1; }
grep -q 'timelineView.showEmpty' "$timeline_wxml" || { echo "FAIL: customer timeline empty card must use normalized visibility" >&2; exit 1; }
grep -q 'timeline-month-count' "$timeline_wxml" || { echo "FAIL: customer timeline monthly count UI is missing" >&2; exit 1; }
grep -q 'timeline-files' "$timeline_wxml" || { echo "FAIL: customer timeline attachment UI is missing" >&2; exit 1; }
grep -q 'timeline-meta' "$timeline_wxml" || { echo "FAIL: customer timeline incentive period/product UI is missing" >&2; exit 1; }
grep -q 'specificProduct' "$timeline_page" || { echo "FAIL: customer timeline specific product UI is missing" >&2; exit 1; }
grep -q 'relatedProducts' "$timeline_page" || { echo "FAIL: customer timeline related product UI is missing" >&2; exit 1; }
grep -q 'wx:for="{{item.metaTags}}"' "$timeline_wxml" || { echo "FAIL: customer timeline normalized meta tag UI is missing" >&2; exit 1; }
grep -q 'timeline-detail-block' "$timeline_wxml" || { echo "FAIL: customer timeline incentive detail block UI is missing" >&2; exit 1; }
grep -q 'catchtap="previewAttachment"' "$timeline_wxml" || { echo "FAIL: customer timeline preview UI is missing" >&2; exit 1; }
grep -q 'catchtap="downloadAttachment"' "$timeline_wxml" || { echo "FAIL: customer timeline download UI is missing" >&2; exit 1; }
grep -q 'timelineView.previewLoading' "$timeline_wxml" || { echo "FAIL: customer timeline attachment UI must use normalized preview loading" >&2; exit 1; }
! grep -q 'loading="{{previewing}}"' "$timeline_wxml" || { echo "FAIL: customer timeline WXML must not bind raw previewing state directly" >&2; exit 1; }
grep -q 'catchtap="goIncentiveDetail"' "$timeline_wxml" || { echo "FAIL: customer timeline incentive detail UI is missing" >&2; exit 1; }
grep -q '记录人：' "$timeline_page" || { echo "FAIL: customer timeline creator UI is missing" >&2; exit 1; }
! grep -Eq "logs\\.length|wx:for-index=\"groupIndex\"|wx:for-index=\"fileIndex\"|item\\.type ===|item\\.fileGroups &&|item\\.content \\|\\||item\\.preInstallQuestion\\}\\}|item\\.remark\\}\\}|creatorName \\|\\||userName|item\\.categoryName \\|\\||group\\.files\\.length|specificProduct|relatedProducts" "$timeline_wxml" || { echo "FAIL: customer timeline WXML must not keep incentive/file branching logic" >&2; exit 1; }
! grep -q '{{loading}}' "$timeline_wxml" || { echo "FAIL: customer timeline WXML must not bind raw loading state directly" >&2; exit 1; }
! grep -q '!loading && showEmpty' "$timeline_wxml" || { echo "FAIL: customer timeline WXML must not duplicate empty-state loading logic" >&2; exit 1; }
grep -q '"pagePath": "pages/incentive/index"' "$app_json" || { echo "FAIL: incentive tab is not bound to the PC-parity incentive workbench" >&2; exit 1; }
! grep -q '"pagePath": "pages/cases/index"' "$app_json" || { echo "FAIL: stale cases tab still exposes an alternate incentive surface" >&2; exit 1; }
# 【2026-10-05】cases 兼容壳已删: 原直接读取 pages/cases/* 文件的重定向/标题/样式检查随之移除,
# 保留上方 app.json 防回潮与下方 profile 入口/app.wxss 全局样式防回潮检查
grep -q 'openIncentive' "$profile_page" || { echo "FAIL: profile incentive entry still uses stale cases naming" >&2; exit 1; }
grep -q 'bindtap="openIncentive"' "$profile_wxml" || { echo "FAIL: profile incentive entry does not navigate through incentive naming" >&2; exit 1; }
! grep -q 'openCases' "$profile_page" || { echo "FAIL: profile page still exposes stale openCases handler" >&2; exit 1; }
! grep -q 'bindtap="openCases"' "$profile_wxml" || { echo "FAIL: profile page still binds stale openCases handler" >&2; exit 1; }
! grep -Eq 'cases-page|case-(card|category|status|title|customer|meta|accent|main)' "$app_wxss" || { echo "FAIL: global mini-program styles still carry standalone case UI styles" >&2; exit 1; }
grep -q "incentive.index.*已实现" "$manifest" || { echo "FAIL: incentive.index manifest status is stale" >&2; exit 1; }
grep -q "incentive.recordDelete.*已实现" "$manifest" || { echo "FAIL: incentive record delete manifest status is stale" >&2; exit 1; }
# 【PR-13 残余 2026-09-20】激励上传表单草稿接线(复刻客户表单议题四模式)
UPLOAD_JS="$ROOT/aigicrm-miniprogram/pages/incentive/upload/index.js"
for pattern in \
  "getFormDraft, saveFormDraft, deleteFormDraft" \
  "saveFormDraft('incentive_upload'" \
  "tryRestoreDraft() {" \
  "autoSaveDraft() {" \
  "deleteFormDraft('incentive_upload').catch(() => {})" \
  "draftVersion"; do
  grep -Fq "$pattern" "$UPLOAD_JS" || { echo "FAIL: incentive upload draft wiring missing: $pattern" >&2; exit 1; }
done
# 草稿仅限真实环境新建模式: autoSaveDraft/tryRestoreDraft 都必须带 demoMode 守卫, 编辑态不存草稿
grep -A2 "autoSaveDraft() {" "$UPLOAD_JS" | grep -q "demoMode" || { echo "FAIL: autoSaveDraft must skip demo mode" >&2; exit 1; }
grep -A2 "tryRestoreDraft() {" "$UPLOAD_JS" | grep -q "demoMode" || { echo "FAIL: tryRestoreDraft must skip demo mode" >&2; exit 1; }
grep -A2 "autoSaveDraft() {" "$UPLOAD_JS" | grep -q "editId" || { echo "FAIL: autoSaveDraft must skip edit mode" >&2; exit 1; }
# 已上传附件按 id 恢复为 existing 条目(提交端 recognized existing&&fileId)
grep -Fq "existing: true" "$UPLOAD_JS" || { echo "FAIL: draft-restored files must carry existing flag for submit parity" >&2; exit 1; }

echo "PASS: mobile incentive query, detail, upload, permission, and form-draft contract is aligned."
