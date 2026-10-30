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
# 【2026-10-26 用户令】客户跟进时间线页(pages/customer/timeline)整页退役——timeline_page/timeline_wxml
# 变量与数十条正向钉移除, 换防复活守卫(见下方 2026-10-26 块)
customer_detail_page="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
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
grep -q "没有四项服务查看权限" "$page" || { echo "FAIL: incentive view denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项服务查看权限" "$detail_page" || { echo "FAIL: incentive detail view denial copy must use four-incentive naming" >&2; exit 1; }
# 【2026-10-17 钉随需求C】b8d62e3f 把 removeRecord/appendRecord 系操作全下游到
# customer-detail 详情页, 列表页只留 openCustomerDetail 入口——删除/追加文案钉随迁
CUSTDETAIL="$ROOT/aigicrm-miniprogram/pages/incentive/customer-detail/index.js"
CUSTDETAIL_WXML="$ROOT/aigicrm-miniprogram/pages/incentive/customer-detail/index.wxml"
grep -q "没有四项服务删除权限" "$CUSTDETAIL" || { echo "FAIL: incentive customer-detail delete denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项服务删除权限" "$detail_page" || { echo "FAIL: incentive detail delete denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "没有四项服务上传权限" "$upload_page" || { echo "FAIL: incentive upload denial copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项服务上传失败" "$upload_page" || { echo "FAIL: incentive upload failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项服务详情加载失败" "$detail_page" || { echo "FAIL: incentive detail load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项服务记录" "$detail_page" || { echo "FAIL: incentive detail delete modal copy must use four-incentive naming" >&2; exit 1; }
grep -q "删除四项服务记录失败" "$detail_page" || { echo "FAIL: incentive detail delete failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项服务加载失败" "$page" || { echo "FAIL: incentive list load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项服务分类加载失败" "$page" || { echo "FAIL: incentive category load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q "四项服务统计加载失败" "$page" || { echo "FAIL: incentive stats load failure copy must use four-incentive naming" >&2; exit 1; }
# 【钉随需求C】列表页删除弹窗/失败文案随操作下游退役——customer-detail 已钉
grep -q "四项服务记录加载失败" "$upload_page" || { echo "FAIL: incentive upload edit-record load failure copy must use four-incentive naming" >&2; exit 1; }
grep -q '"navigationBarTitleText":"四项服务应用"' "$json" || { echo "FAIL: incentive workbench title must use four-services-app naming (2026-10-23 拆分: 完整版=四项服务应用)" >&2; exit 1; }
grep -q '"navigationBarTitleText":"四项服务详情"' "$detail_json" || { echo "FAIL: incentive detail title must use four-incentive naming" >&2; exit 1; }
grep -q '<view class="hero-title">四项服务详情</view>' "$detail_wxml" || { echo "FAIL: incentive detail hero title must use four-incentive naming" >&2; exit 1; }
grep -q '暂无四项服务详情' "$detail_wxml" || { echo "FAIL: incentive detail empty state must use four-incentive naming" >&2; exit 1; }
grep -q '暂无四项服务记录' "$wxml" || { echo "FAIL: incentive list empty state must use four-incentive naming" >&2; exit 1; }
# 【2026-10-26】timeline 四项服务命名钉随页面退役(防复活守卫见下方 2026-10-26 块)
grep -q '"navigationBarTitleText":"四项服务上传"' "$upload_json" || { echo "FAIL: incentive upload title must use four-incentive naming" >&2; exit 1; }
grep -q '<view class="hero-title">四项服务上传</view>' "$upload_wxml" || { echo "FAIL: incentive upload hero title must use four-incentive naming" >&2; exit 1; }
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
# 【2026-10-26 时间线页退役】verifyTimelinePage 随页移除, 此钉翻转为防复活
! grep -q 'verifyTimelinePage' "$automator_script" || { echo "FAIL: customer timeline page was removed 2026-10-26; automator must not verify it back" >&2; exit 1; }
grep -q '__aigicrmAttachmentActionsPatched' "$automator_script" || { echo "FAIL: incentive DevTools automator does not patch native attachment actions" >&2; exit 1; }
grep -q 'attachmentActionCalls' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert native attachment action calls" >&2; exit 1; }
grep -q 'selected-file-thumb' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload thumbnail rendering" >&2; exit 1; }
grep -q 'upload page selected image must call previewImage' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-image preview action" >&2; exit 1; }
grep -q 'upload page selected image download must call saveFile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-image download action" >&2; exit 1; }
grep -q 'upload page selected document must call openDocument' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-document preview action" >&2; exit 1; }
grep -q 'upload page selected document download must call saveFile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert upload selected-document download action" >&2; exit 1; }
grep -q 'file-tile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert detail attachment tiles" >&2; exit 1; }
grep -q 'wall-tile' "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert list attachment tiles" >&2; exit 1; }  # 【#4 2026-10-12】列表佐证改照片墙重锚
# 【2026-10-26 时间线页退役】timeline-file-row / timeline-attachments.png 断言随页移除, 翻转为防复活
! grep -q 'timeline-file-row' "$automator_script" || { echo "FAIL: customer timeline page was removed 2026-10-26; automator must not assert its UI back" >&2; exit 1; }
! grep -q 'timeline-attachments.png' "$automator_script" || { echo "FAIL: customer timeline page was removed 2026-10-26; automator must not capture its evidence back" >&2; exit 1; }
grep -q 'read-stats-ranking.png' "$automator_script" || { echo "FAIL: incentive DevTools automator does not capture read stats/ranking evidence" >&2; exit 1; }
grep -q "switchTab', eventWithDataset({ key: 'reads' })" "$automator_script" || { echo "FAIL: incentive DevTools automator does not switch to read stats/ranking tab" >&2; exit 1; }
grep -q "read page expected stats and ranking panels" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert read stats/ranking panels" >&2; exit 1; }
grep -q "assertAttachmentTags(files, \\['安装前', '安装中', '安装后', '跟踪', '佐证'\\], 'detail page')" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert all PC attachment slots on detail page" >&2; exit 1; }
grep -q "assertAttachmentTags(wallFiles, \\['安装前', '安装中', '安装后', '跟踪', '佐证'\\], 'list page')" "$automator_script" || { echo "FAIL: incentive DevTools automator does not assert all PC attachment slots on list page" >&2; exit 1; }
# 【F23 复审钉 2026-10-13】重锚后的关键断言字面量锚定(grep -F 固定串): 防脚本退化回旧数据模型
# (裸 setData 注入/choose 旧方法名/records[0].files)或删掉排序态真实 tap 归属断言。
# 【2026-10-27 深度检查重写】旧列表 UI(incentive-record-card/childRecordsView 已读链)随 b8d62e3f
# 操作下沉退役——token 'records[0].childRecordsView[0].unread' 换成现行统计明细抽屉的
# 'statDetailView.pagedRecords'(列表附件的真实载体)
for anchor_token in 'rebuildRecordViews' 'chooseOrder' 'wall-act-btn' 'previewStatDetailAttachment' 'sepDial' 'download/301'; do
  grep -qF "$anchor_token" "$automator_script" || { echo "FAIL: incentive automator must keep anchored assertion token: $anchor_token" >&2; exit 1; }
done
# 【2026-10-26 时间线页退役】'timeline page' 槽位断言随页移除, 翻转为防复活
! grep -q "'timeline page'" "$automator_script" || { echo "FAIL: customer timeline page was removed 2026-10-26; automator must not assert its attachment slots back" >&2; exit 1; }
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
# 【2026-10-26 重锚】用户令"要有按时间排序": 排序改四档(最新/最早记录在前+分值高低),
# 默认档=「最新记录在前」(time-desc)——与原"不排序=接口顺序(createdAt 倒序)"行为等价,
# 由隐性默认改显式档位; 旧 order:'' 与 asc/desc 字面量随旧三档退役
grep -q "order: 'time-desc'" "$page" || { echo "FAIL: incentive mobile order must default to latest-record-first (time-desc, user order 2026-10-26)" >&2; exit 1; }
grep -q 'selectedOrderIndex: 0' "$page" || { echo "FAIL: incentive mobile score order picker default index is missing" >&2; exit 1; }
grep -q "if (this.data.order === 'score-asc')" "$page" || { echo "FAIL: incentive mobile score ascending order branch is missing" >&2; exit 1; }
grep -q "if (this.data.order === 'score-desc')" "$page" || { echo "FAIL: incentive mobile score descending order branch is missing" >&2; exit 1; }
grep -q "if (this.data.order === 'time-asc')" "$page" || { echo "FAIL: incentive mobile time ascending order branch is missing (user order 2026-10-26)" >&2; exit 1; }
grep -q "if (this.data.order === 'time-desc')" "$page" || { echo "FAIL: incentive mobile time descending order branch is missing (user order 2026-10-26)" >&2; exit 1; }
grep -q 'ORDER_VALUES' "$page" || { echo "FAIL: incentive mobile order picker values must map to the four sort modes" >&2; exit 1; }
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
# 【钉随一客一册 2026-10-20】分类锚点chips+scrollToGroup 重设计为"封面→左侧月份时间轴+右侧单条翻页"
# (对齐 PC 时间轴拨片设计): 时间轴月份选择=selectMonth(data-key)+setCurrentGroup, 翻页=prev/nextRecord
grep -q 'selectMonth' "$CUSTDETAIL" || { echo "FAIL: incentive mobile customer archive timeline month selection is missing" >&2; exit 1; }
grep -q 'setCurrentGroup' "$CUSTDETAIL" || { echo "FAIL: incentive mobile customer archive cannot browse records by timeline" >&2; exit 1; }
grep -q 'openRecordDetail' "$CUSTDETAIL" || { echo "FAIL: incentive mobile customer archive cannot open child record detail" >&2; exit 1; }
grep -q 'markRecordReadByKey(' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive detail open must mark records read" >&2; exit 1; }
# 【钉随需求C】旧双index签名 markChildRecordRead 随操作下游退役, 一客一册用 markRecordReadByKey(key, index)
grep -q 'appendChildRecord' "$page" || { echo "FAIL: incentive mobile customer detail list cannot append child records" >&2; exit 1; }
grep -q 'removeChildRecord' "$page" || { echo "FAIL: incentive mobile customer detail list cannot delete child records" >&2; exit 1; }
# 【钉随一客一册 2026-10-20】旧 findWallFile 照片墙重设计为"版面照片 previewPhoto+文档附件
# previewAttachmentByFile"(照片/视频版面内联展示, 附件区只留需点开阅读的文档)
grep -q 'previewAttachmentByFile' "$CUSTDETAIL" || { echo "FAIL: incentive mobile customer archive cannot preview child record files" >&2; exit 1; }
grep -q '客户维度明细' "$wxml" || { echo "FAIL: incentive mobile customer detail list is missing" >&2; exit 1; }
grep -q 'readStatusClass' "$page" || { echo "FAIL: incentive mobile list must expose read status like PC" >&2; exit 1; }
# 【钉随一客一册 2026-10-20】readStatusText 徽章改为当前记录未读标记(currentRecord.unread cd-unread-tag)
grep -q 'cd-unread-tag' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile archive read status UI is missing" >&2; exit 1; }
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
# 【钉随需求C】"全部记录"链接改为整卡点击 openCustomerDetail(封面卡即入口)
grep -q 'openCustomerDetail' "$page" || { echo "FAIL: incentive mobile customer detail list all-records entry is missing" >&2; exit 1; }
# 【钉随需求C】customer-record-list 重设计为 cust-card 封面卡 + cd-wall-card 分组照片墙
grep -q 'cust-card' "$wxml" || { echo "FAIL: incentive mobile customer detail list expanded timeline UI is missing" >&2; exit 1; }
# 【钉随需求C】data-child-index 随操作下游退役——customer-detail 用 data-index
grep -q 'data-index' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile customer detail list child record actions lack record index" >&2; exit 1; }
! grep -Eq "item\\.files\\.length|item\\.fileGroups &&|item\\.records &&|item\\.expanded \\?|child\\.categoryName \\|\\||child\\.period \\|\\||child\\.score \\|\\||child\\.preInstallQuestion \\|\\||child\\.fileGroups &&|group\\.files\\.length" "$wxml" || { echo "FAIL: incentive mobile records WXML must not keep record/file fallback branching logic" >&2; exit 1; }
! grep -Eq "wx:for-index=\"groupIndex\"|wx:for-index=\"fileIndex\"" "$wxml" || { echo "FAIL: incentive mobile attachment WXML must not derive file action indexes in template" >&2; exit 1; }
# 【F25 2026-10-13】照片墙循环必须显式命名内层索引: 内层默认 index 遮蔽外层 record index 会让
# data-record-index 错取文件下标(H2 同款回归), 显式 wallGroupIdx/wallFileIdx 是防线, 真实点击钉在
# verify-incentive-attachments.mjs(tap 瓦片按钮经 WXML 绑定断言 previewImage 收到正确文件)。
grep -q 'wx:for-index="wallGroupIdx"' "$wxml" || { echo "FAIL: incentive wall group loops must declare wx:for-index (inner-index shadowing guard)" >&2; exit 1; }
grep -q 'wx:for-index="wallFileIdx"' "$wxml" || { echo "FAIL: incentive wall file loops must declare wx:for-index (inner-index shadowing guard)" >&2; exit 1; }
# 【F24 2026-10-13】setData 瘦身负向钉(1MB 真机预算): 视图不得回携签名缩略图 URL,
# buildRecordView 必须持续剥离 files/fileGroups 原始大数组(改由 thumb.wxs 渲染层拼 URL)
! grep -Eq "thumbUrl['\\\"]?[[:space:]]*[:=]" "$page" || { echo "FAIL: incentive view must not rebuild signed thumbUrl into data (setData 1MB budget)" >&2; exit 1; }
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
grep -q 'stats-detail-sheet' "$wxml" || { echo "FAIL: incentive mobile stats detail drawer UI is missing" >&2; exit 1; }
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
# 【钉随一客一册 2026-10-20】行级 showAppendAction/showDeleteAction 收敛为 onRecordMore
# 面板内 record.canEdit/canDelete 判定(JS 归一化保留, WXML 不暴露权限)
grep -q 'record.canEdit' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive append visibility must be normalized in JS" >&2; exit 1; }
grep -q 'record.canDelete' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive delete visibility must be normalized in JS" >&2; exit 1; }
grep -q 'appendRecord' "$page" || { echo "FAIL: incentive mobile list record append action is missing" >&2; exit 1; }
# 【钉随需求C】追加跳转(编辑模式)从列表页下沉到 customer-detail(goAppend)
grep -q "pages/incentive/upload/index?id=" "$CUSTDETAIL" || { echo "FAIL: incentive mobile list append action does not open upload edit mode" >&2; exit 1; }
grep -q 'removeRecord' "$page" || { echo "FAIL: incentive mobile list record delete action is missing" >&2; exit 1; }
# 【钉随需求C→一客一册】删除调用从列表页下沉到 customer-detail(removeRecord→deleteRecord(record.id))
grep -q 'deleteRecord(record.id)' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive record delete does not call delete API" >&2; exit 1; }
# 【钉随需求C】客户档案跳转从列表页下沉到 customer-detail(onOpenCustomer)
grep -q 'onOpenCustomer' "$CUSTDETAIL" || { echo "FAIL: incentive mobile list customer archive action is missing" >&2; exit 1; }
# 【钉随需求C】客户档案路由从列表页下沉到 customer-detail(onOpenCustomer)
grep -Fq '/pages/customer/detail/index?id=${record.customerId}&from=incentive' "$CUSTDETAIL" || { echo "FAIL: incentive mobile list customer archive route is stale" >&2; exit 1; }
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
# 【钉随一客一册 2026-10-20】照片墙 onTileTap/onTileLongPress 重设计为"版面照片 previewPhoto +
# 附件区文档 previewAttachment"(照片/视频版面内联, 长按下载入口随档案化退役, 下载能力由
# fetchRemoteAttachment preview 流内承接: 文档下载后 openDocument)
grep -q 'bindtap="previewPhoto"' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile archive photo preview UI is missing" >&2; exit 1; }
grep -q 'bindtap="previewAttachment"' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile archive attachment preview UI is missing" >&2; exit 1; }
grep -q 'fetchRemoteAttachment' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive attachments cannot preview via shared util" >&2; exit 1; }
grep -q 'incentiveView.previewLoading' "$wxml" || { echo "FAIL: incentive mobile list attachment UI must use normalized preview loading" >&2; exit 1; }
! grep -q 'loading="{{previewing}}"' "$wxml" || { echo "FAIL: incentive mobile list WXML must not bind raw previewing state directly" >&2; exit 1; }
# 【钉随一客一册 2026-10-20】吸底 onAppend/onChildMore(⋯面板) 重设计为记录行尾「···」onRecordMore
# (详情/追加/删除按 record.canEdit/canDelete 在面板 JS 判定, 不暴露 WXML)
grep -q 'bindtap="onRecordMore"' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile archive record actions UI is missing" >&2; exit 1; }
grep -q 'onAppend' "$CUSTDETAIL" || { echo "FAIL: incentive mobile archive append action is missing" >&2; exit 1; }
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
# 【钉随需求C】openCustomer 下沉到 customer-detail(onOpenCustomer, 吸底"查看客户档案")
grep -q 'bindtap="onOpenCustomer"' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile list customer archive UI is missing" >&2; exit 1; }
# 【钉随需求C】"客户档案"文案随操作下沉到 customer-detail 吸底
grep -q '客户档案' "$CUSTDETAIL_WXML" || { echo "FAIL: incentive mobile list customer archive copy is missing" >&2; exit 1; }
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
grep -q 'updateRecord(this.data.editId, payload, { keepEmpty: true })' "$upload_page" || { echo "FAIL: incentive upload edit mode does not call updateRecord PUT with keepEmpty (C4: cleared fields must reach backend as empty string)" >&2; exit 1; }
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
# 【2026-10-30 同槽翻页】同一上传格子内左右翻页: swiper 横滑+页码指示+深层补丁回调; 行式平铺禁止回潮
grep -q 'class="slot-swiper"' "$upload_wxml" || { echo "FAIL: incentive upload slot swiper paging is missing" >&2; exit 1; }
grep -q 'bindchange="onSlotPageChange"' "$upload_wxml" || { echo "FAIL: incentive upload slot page change handler is unwired" >&2; exit 1; }
grep -q 'selected-slot-page' "$upload_wxml" || { echo "FAIL: incentive upload slot page indicator is missing" >&2; exit 1; }
grep -q 'onSlotPageChange' "$upload_page" || { echo "FAIL: incentive upload slot paging handler is missing in JS" >&2; exit 1; }
grep -q 'slotPages' "$upload_page" || { echo "FAIL: incentive upload slot paging state is missing in JS" >&2; exit 1; }
! grep -q 'selected-file-row' "$upload_wxml" || { echo "FAIL: incentive upload per-file flat rows must not return (same-slot paging replaced them)" >&2; exit 1; }
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
# 【性能 2026-10-27】拨盘详情页数据拉取: recordId 定位只拉该记录所属客户一组(此前全量 544KB 再
# 客户端线性查找); 旧后端不识别 recordId 返回全量时须回退客户端查找(两侧兼容), 不得回退成无参全量
grep -q 'getCustomerRecords({ recordId: this.data.id })' "$detail_page" || { echo "FAIL: incentive detail must locate its record via the recordId-scoped customer-records query" >&2; exit 1; }
grep -q 'list.length === 1 ? list\[0\] : null' "$detail_page" || { echo "FAIL: incentive detail must consume the single-group recordId response with a full-list fallback" >&2; exit 1; }
# 【2026-09-20 小程序同步轮】三联拨盘:原生 picker-view 三列(iOS 滚轮),钉住 PC 定稿语义防回退——
# 位置语义(拨到哪显示哪)/整月兜底/每月完整天数(闰年自动)/年份自增/回到今天/实时时钟/「此时间无数据」空态
grep -q '<picker-view' "$detail_wxml" || { echo "FAIL: incentive detail dial must use native picker-view columns" >&2; exit 1; }
grep -q 'bindchange="onDialChange"' "$detail_wxml" || { echo "FAIL: incentive detail dial change handler is missing" >&2; exit 1; }
# 【性能 2026-10-27】拨盘滚动局部更新: 滚轮连发时不再整页重建 detailView(整包 setData 是滑动掉帧
# 直接来源), 只以路径键写回联动区; 全量重建与局部更新共用 buildDialPane 同一口径防漂移
grep -q 'function buildDialPane' "$detail_page" || { echo "FAIL: incentive detail dial pane builder must be shared by full rebuild and dial changes" >&2; exit 1; }
grep -q 'applyDialSelection(selYear, selMonth, selDay, columnsChanged)' "$detail_page" || { echo "FAIL: incentive detail dial changes must go through the partial-update path" >&2; exit 1; }
grep -q "'detailView.dialMonths': pane.dialMonths" "$detail_page" || { echo "FAIL: incentive detail partial update must write only the affected detailView keys" >&2; exit 1; }
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
# 【2026-10-26 用户令「客户服务记录 小程序上不要这个页面」】客户跟进时间线页
# pages/customer/timeline 整页退役(44→43页), 原本块 43 条正向钉随之移除。
# 防复活守卫(欠账七式⑥): 页面文件/app.json 注册/客户详情跟进入口不得回潮。
# 口径: 详情页「跟进日志」区块保留(customer:view 读取普通日志), 激励记录时间线随页移除。
[ ! -e "$ROOT/aigicrm-miniprogram/pages/customer/timeline" ] || { echo "FAIL: customer timeline page must stay removed (2026-10-26 user order)" >&2; exit 1; }
! grep -q 'pages/customer/timeline' "$app_json" || { echo "FAIL: app.json must not re-register the removed customer timeline page" >&2; exit 1; }
! grep -q 'openTimeline' "$customer_detail_page" || { echo "FAIL: customer detail must not re-introduce a timeline entry (page removed 2026-10-26)" >&2; exit 1; }
grep -q 'showActionCard: hasDetail && (canViewContacts || !isAdmin() || canEdit)' "$customer_detail_page" || { echo "FAIL: customer detail action card must not render empty after timeline entry removal" >&2; exit 1; }
# 【2026-10-23 四项服务拆分·用户令】tabBar「四项服务」=轻量客户记录页(home); 完整工作台(index)
# 不再是 tabBar 页, 由应用中心「四项服务应用」navigateTo 进入。此处钉两层: tabBar 绑 home +
# home 页标题=四项服务 + 应用中心入口指向完整版。
grep -q '"pagePath": "pages/incentive/home/index"' "$app_json" || { echo "FAIL: incentive tab must bind to the lightweight customer-records home page" >&2; exit 1; }
home_json="$ROOT/aigicrm-miniprogram/pages/incentive/home/index.json"
grep -q '"navigationBarTitleText":"四项服务"' "$home_json" || { echo "FAIL: incentive tab home title must be 四项服务 (lightweight view)" >&2; exit 1; }
! grep -q 'pages/incentive/customer-detail' "$ROOT/aigicrm-miniprogram/pages/incentive/home/index.js" || { echo "FAIL: lightweight home must not navigate into the archive customer-detail (2026-10-23 用户令: 不进档案界面)" >&2; exit 1; }
grep -q "incentiveQuery: '/pages/incentive/index'" "$ROOT/aigicrm-miniprogram/pages/app/index.js" || { echo "FAIL: app center 四项服务应用 entry must route to the full workbench" >&2; exit 1; }
grep -q "title: '四项服务应用'" "$ROOT/aigicrm-miniprogram/pages/app/index.js" || { echo "FAIL: app center must name the full workbench entry 四项服务应用" >&2; exit 1; }
# 【2026-10-26 用户令·排法分工】轻量页(home)与完整版(index)排法分开: 完整版保持按客户归集封面卡;
# 轻量页默认打散记录流(不绑客户, 按上传时间排), 「按客户」可切回归集。钉默认态/切换器/打散构建/点行直达。
HOME_JS="$ROOT/aigicrm-miniprogram/pages/incentive/home/index.js"
HOME_WXML="$ROOT/aigicrm-miniprogram/pages/incentive/home/index.wxml"
grep -q "listMode: 'time'" "$HOME_JS" || { echo "FAIL: lightweight home must default to the flat by-time record stream (2026-10-26 用户令)" >&2; exit 1; }
grep -q 'function buildFlatRecordRows' "$HOME_JS" || { echo "FAIL: lightweight home flat record stream builder is missing" >&2; exit 1; }
grep -q 'switchListMode' "$HOME_JS" || { echo "FAIL: lightweight home must keep the time/customer view toggle" >&2; exit 1; }
grep -q 'bindtap="switchListMode"' "$HOME_WXML" || { echo "FAIL: lightweight home view toggle UI is missing" >&2; exit 1; }
grep -q 'class="flat-rec"' "$HOME_WXML" || { echo "FAIL: lightweight home flat record row UI is missing" >&2; exit 1; }
grep -q "'/pages/incentive/detail/index?id=' + row.id" "$HOME_JS" || { echo "FAIL: flat record rows must open the dial detail by record id" >&2; exit 1; }
# 【2026-10-26 四次令"按时间的列表不要呈现客户名称,呈现图片"+"胶囊做醒目"】打散行首=记录首图缩略
# (coverFile/flat-cover, 无图退分类首字面), 客户名只留检索不显示; 切换胶囊改品牌蓝实底加大
grep -q 'class="flat-cover"' "$HOME_WXML" || { echo "FAIL: flat record rows must lead with the record image thumbnail (user order 2026-10-26)" >&2; exit 1; }
grep -q "coverFile: coverFile || {}" "$HOME_JS" || { echo "FAIL: flat row cover image derivation is missing" >&2; exit 1; }
! grep -q 'flat-rec-name' "$HOME_WXML" "$HOME_JS" || { echo "FAIL: flat record rows must not display customer names (search-only, user order 2026-10-26)" >&2; exit 1; }
# 【2026-10-26 三次澄清·用户令"按照客户排列,和之前的版本一样"(参考截图=客户封面卡)】按客户=封面卡
# 客户列表(缩略图/首字+客户名+行业胶囊+条数·文件数+分值胶囊; 一次上传=一条记录,攒在客户名下,点卡进拨盘);
# 纯名称清单试版被用户否决,防复活
grep -q 'class="cust-card"' "$HOME_WXML" || { echo "FAIL: lightweight home customer mode must be the cover-card customer list (user reference screenshot 2026-10-26)" >&2; exit 1; }
! grep -q 'cust-list-row' "$HOME_WXML" || { echo "FAIL: lightweight home plain name list was rejected 2026-10-26 (cover cards restored per user screenshot)" >&2; exit 1; }
# 【2026-10-27 用户令 B 案】轻量页分类筛选两级化(大类→子类): 原单列 picker 只列子类名,
# 四大主类(产品试点/交流拜访/外出参观/两谈两做方案)不可选且选项从"每客户最新一条"收集会漏分类;
# 改 multiSelector 双列滚轮(大类列=全部分类+四大类, 子类列=全部+该大类子类), 数据源=分类树接口,
# 树不可用降级为"全部分类+记录推导子类"单层。选大类=按全部子类名集合过滤(categoryNames 机制不变)。
grep -q 'mode="multiSelector" range="{{categoryRange}}"' "$HOME_WXML" || { echo "FAIL: lightweight home category filter must be the two-column main/sub picker (user order B 2026-10-27)" >&2; exit 1; }
grep -q 'bindcolumnchange="onCategoryColumnChange"' "$HOME_WXML" || { echo "FAIL: lightweight home two-column category picker must relink the sub column on main change" >&2; exit 1; }
! grep -q 'range="{{categories}}"' "$HOME_WXML" || { echo "FAIL: lightweight home must not fall back to the flat sub-name-only category picker (replaced 2026-10-27)" >&2; exit 1; }
grep -q "getCategoryTree" "$HOME_JS" || { echo "FAIL: lightweight home two-level category filter must load the category tree" >&2; exit 1; }
grep -q "CATEGORY_ALL = '全部分类'" "$HOME_JS" || { echo "FAIL: lightweight home category main column must keep the all-categories entry" >&2; exit 1; }
grep -q "names = subIndex > 0 && sub !== '全部' ? \[sub\] : subs.slice(1)" "$HOME_JS" || { echo "FAIL: lightweight home main-category selection must filter by its full sub-category name set" >&2; exit 1; }
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
# 【C4 2026-10-21】编辑态清空字段发 ''(后端 updateRecord 非 null 即合并, '' 才能清掉库内旧值);
# 新建态维持 || null。清空语义按模式分支的 clearableText 是唯一出口, 不得回退成一律 || null。
grep -q 'const clearableText = (value) => this.data.editId ? value : (value || null)' "$upload_page" || { echo "FAIL: incentive upload cleared-text semantics must branch on edit mode (C4)" >&2; exit 1; }
# 【C2 2026-10-21】客户选择半屏改服务端搜索+分页(原固定前100条本地过滤, 第101个客户选不到):
# 请求必须带 keyword/current/size 且 total 用服务端值; 选中按 customerId; 草稿恢复按 id 补拉。
grep -q 'getCustomerList({ current: page, size: CUSTOMER_SHEET_PAGE_SIZE, keyword })' "$upload_page" || { echo "FAIL: incentive upload customer sheet must query server-side with keyword/current/size (C2)" >&2; exit 1; }
grep -q "'customerSheet.total': total" "$upload_page" || { echo "FAIL: incentive upload customer sheet total must come from server (C2)" >&2; exit 1; }
grep -q 'loadMoreCustomers' "$upload_page" || { echo "FAIL: incentive upload customer sheet must support server pagination append (C2)" >&2; exit 1; }
grep -q 'bindscrolltolower="loadMoreCustomers"' "$upload_wxml" || { echo "FAIL: incentive upload customer sheet UI must trigger pagination on scroll (C2)" >&2; exit 1; }
grep -q 'data-id="{{item.id}}"' "$upload_wxml" || { echo "FAIL: incentive upload customer sheet must select by customerId, not array index (C2)" >&2; exit 1; }
grep -q "item.id === selectedCustomerId" "$upload_wxml" || { echo "FAIL: incentive upload customer sheet highlight must key on customerId (C2)" >&2; exit 1; }
# 【C2】草稿客户按 id 补拉(getCustomer); 只有 404/403 才清草稿, 不得本地列表找不到即删
grep -q 'getCustomer(parsed.customerId)' "$upload_page" || { echo "FAIL: incentive upload draft restore must re-fetch customer by id (C2)" >&2; exit 1; }
grep -q "error.statusCode === 404 || error.statusCode === 403" "$upload_page" || { echo "FAIL: incentive upload draft must only be dropped when customer is 404/403, not on transient errors (C2)" >&2; exit 1; }
grep -q 'getCustomerList, getCustomer' "$upload_page" || { echo "FAIL: incentive upload must import getCustomer for draft restore (C2)" >&2; exit 1; }
# 【C2】防回潮: 不得退回"拉前100客户本地过滤"的旧选择器
if grep -q 'size: 100\|size:100' "$upload_page"; then
  echo "FAIL: incentive upload must not go back to fixed first-100 customer fetch (C2)" >&2
  exit 1
fi
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
