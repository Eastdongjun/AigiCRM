#!/usr/bin/env bash
# Keep high-risk file mutations permission-gated in mobile.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/file.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/files/index.js"
WXML="$ROOT/aigicrm-miniprogram/pages/files/index.wxml"
WXSS="$ROOT/aigicrm-miniprogram/pages/files/index.wxss"
FOLDER_FORM="$ROOT/aigicrm-miniprogram/pages/files/folder-form/index.js"
FOLDER_FORM_WXML="$ROOT/aigicrm-miniprogram/pages/files/folder-form/index.wxml"
LEGACY_PAGE="$ROOT/aigicrm-miniprogram/pages/file/index.js"
LEGACY_WXML="$ROOT/aigicrm-miniprogram/pages/file/index.wxml"
DETAIL="$ROOT/aigicrm-miniprogram/pages/files/detail/index.js"
CACHE="$ROOT/aigicrm-miniprogram/utils/file-cache.js"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java"
MANIFEST="$ROOT/docs/unified-feature-manifest.md"
AUTOMATOR_PACKAGE="$ROOT/scripts/miniprogram/package.json"
AUTOMATOR_SCRIPT="$ROOT/scripts/miniprogram/verify-file-center.mjs"

fail() { echo "FAIL: $*" >&2; exit 1; }
for token in saveAcl removeAcl setDownloadable; do
  ! grep -Fq "$token" "$API" || fail "mobile file API exposes forbidden $token"
  ! grep -Fq "$token" "$PAGE" || fail "mobile file page exposes forbidden $token"
done
for token in '分享设置' 'ACL'; do
  ! grep -Fq "$token" "$WXML" || fail "mobile file UI exposes forbidden $token"
done
for token in attachFile detachFile; do
  ! grep -Fq "$token" "$PAGE" || fail "mobile file page exposes stale customer-file relation action $token"
done
for token in '解除关联' '关联</button>'; do
  ! grep -Fq "$token" "$ROOT/aigicrm-miniprogram/pages/files/index.wxml" || fail "mobile files UI exposes stale customer-file relation copy: $token"
done
grep -Fq 'uploadFile' "$API" || fail 'mobile file upload contract disappeared'
grep -Fq 'getStorageInfo' "$API" || fail 'mobile file storage statistics API disappeared'
grep -Fq '/api/file/storage' "$API" || fail 'mobile file storage statistics endpoint disappeared'
grep -Fq 'getDownloadToken' "$API" || fail 'mobile file preview/download contract disappeared'
grep -Fq 'getDownloadUrl' "$API" || fail 'mobile file path-download URL helper disappeared'
grep -Fq '/api/file/download/' "$API" || fail 'mobile file path-download endpoint disappeared'
grep -Fq 'getSharedToMeCount' "$API" || fail 'mobile file shared-to-me count API disappeared'
grep -Fq '/api/file/shared-to-me/count' "$API" || fail 'mobile file shared-to-me count endpoint disappeared'
grep -Fq 'shareFile' "$API" || fail 'mobile file share API contract disappeared'
grep -Fq 'deleteFile' "$API" || fail 'mobile file delete API contract disappeared'
grep -Fq "hasPermission('file:delete')" "$PAGE" || fail 'mobile file delete permission gate disappeared'
grep -Fq 'removeFile' "$PAGE" || fail 'mobile file delete action disappeared'
grep -Fq 'deleteFile(item.id)' "$PAGE" || fail 'mobile file delete action does not call backend delete API'
# 【钉随需求A】行内 removeFile 按钮收敛到 openActionSheet(···面板)——JS 侧 removeFile 方法保留
# 【钉随需求A 2026-10-16】文件行操作面板(···)是全部高危操作的唯一入口
grep -Fq 'catchtap="openActionSheet"' "$WXML" || fail 'mobile file delete UI disappeared'
# 【钉随需求A】mini-action danger 收敛为 sheet-act danger(操作面板内删除按钮)
grep -Fq "act.key === 'delete' ? 'danger'" "$WXML" || fail 'mobile file delete UI is not visually scoped as a compact danger action'
grep -Fq 'getFolderAcl' "$API" || fail 'mobile file folder ACL read API disappeared'
grep -Fq 'getStorageInfo' "$PAGE" || fail 'mobile file page no longer loads storage statistics'
grep -Fq 'normalizeCapacity' "$PAGE" || fail 'mobile file page no longer normalizes storage statistics'
grep -Fq 'loadTabCounts' "$PAGE" || fail 'mobile file page no longer loads PC tab counts'
grep -Fq 'getSharedToMeCount' "$PAGE" || fail 'mobile file page no longer loads shared-to-me count like PC'
grep -Fq "scope: 'private'" "$PAGE" || fail 'mobile file page no longer counts private tab like PC'
grep -Fq "scope: 'incentive'" "$PAGE" || fail 'mobile file page no longer counts incentive tab like PC'
grep -Fq 'buildFileListParams' "$PAGE" || fail 'mobile file page no longer builds server-side list query params like PC'
grep -Fq 'keyword: this.data.keyword.trim() || undefined' "$PAGE" || fail 'mobile file page no longer sends keyword to backend list query like PC'
grep -Fq 'params.scope = this.activeFileScope()' "$PAGE" || fail 'mobile file page no longer sends active tab scope to backend list query like PC'
grep -Fq "getSharedToMe(this.buildFileListParams({ current: page, size: pageSize }))" "$PAGE" || fail 'mobile file shared-to-me tab no longer sends keyword to backend query like PC'
grep -Fq 'normalizeDownloadable' "$PAGE" || fail 'mobile file page no longer normalizes PC folder download permission metadata'
grep -Fq 'item.canDownload === false' "$PAGE" || fail 'mobile file page no longer honors row-level canDownload=false'
grep -Fq 'item.downloadable === 0' "$PAGE" || fail 'mobile file page no longer honors folder downloadable=0'
grep -Fq "perms.includes('download')" "$PAGE" || fail 'mobile file page no longer honors folder download permission'
grep -Fq 'buildFileView' "$PAGE" || fail 'mobile file page must normalize page view state before WXML'
grep -Fq 'buildFileRowView' "$PAGE" || fail 'mobile file rows must be normalized before WXML'
# 【2026-10-26 重锚】用户令"三个点不需要替换"——替换入口随 replaceListFile 一并退役,
# replaceLoading/replacingId/showReplace 状态位清退, 此钉改为防复活守卫
! grep -Fq 'replaceLoading' "$PAGE" || fail 'file replace was retired by user order (2026-10-26); replaceLoading must not come back'
grep -Fq 'showManageFolders' "$PAGE" || fail 'mobile file folder management visibility must be normalized before WXML'
grep -Fq 'showManageEmpty' "$PAGE" || fail 'mobile file folder management empty state must be normalized before WXML'
grep -Fq 'setFileData' "$PAGE" || fail 'mobile file page must refresh normalized view state with data changes'
grep -Fq 'searchFiles' "$PAGE" || fail 'mobile file page no longer has explicit server-side search action'
grep -Fq 'bindconfirm="searchFiles"' "$WXML" || fail 'mobile file search input no longer submits with keyboard search'
grep -Fq 'bindtap="searchFiles"' "$WXML" || fail 'mobile file search button disappeared'
grep -Fq '.search-action' "$WXSS" || fail 'mobile file search action is not styled as a compact control'
# 【2026-10-24 重锚】d1c17aaf 用户令"文件页顶部区域太大"——容量提示语行整行移除,
# 加载/未同步状态由 used/total 的 '-' 占位表达; hint 数据字段仍在 js(勿删归一化),
# 但展示层不得回摆多行提示。原 require {{capacity.hint}} 改为防复活守卫。
! grep -Fq '{{capacity.hint}}' "$WXML" || fail 'files-top was compressed by user order (d1c17aaf); capacity.hint line must not come back'
grep -Fq 'tab-count' "$WXML" || fail 'mobile file tabs no longer display PC tab counts'
grep -Fq 'fileView.tabs' "$WXML" || fail 'mobile file tabs must render normalized tab view state'
grep -Fq 'item.countText' "$WXML" || fail 'mobile file tabs must display normalized tab counts'
# 【钉随需求A】fileView.files 重设计为 fileView.fileGroups(时间分组面包屑)
grep -Fq 'fileView.fileGroups' "$WXML" || fail 'mobile file list must render normalized file rows'
# 【钉随需求A】metaText 拆为 metaBrief + metaExtra(两行元信息)
grep -Fq 'file.metaBrief' "$WXML" || fail 'mobile file rows must display normalized metadata text'
grep -Fq "hasPermission('file:share')" "$PAGE" || fail 'mobile file share permission gate disappeared'
# 【钉随需求A】行内 shareFile 收敛到操作面板 onSheetAction 分发(JS shareFile 方法保留)
grep -Fq 'catchtap="openActionSheet"' "$WXML" || fail 'mobile file share action disappeared'
grep -Fq 'class="folder-new-action"' "$WXML" || fail 'mobile file folder create action must use compact local style'
grep -Fq '.folder-new-action' "$WXSS" || fail 'mobile file folder create compact style disappeared'
grep -Fq 'getCustomerSimpleUsers' "$PAGE" || fail 'mobile file share user picker disappeared'
grep -Fq "hasPermission('file:acl:manage')" "$PAGE" || fail 'mobile file folder ACL view permission gate disappeared'
grep -Fq 'viewFolderAcl' "$PAGE" || fail 'mobile file folder ACL view action disappeared'
grep -Fq "key: 'acl'" "$PAGE" || fail 'mobile file folder action menu lost ACL entry'
grep -Fq 'catchtap="openFolderActions"' "$WXML" || fail 'mobile file folder action menu UI disappeared'
grep -Fq 'ossKey' "$PAGE" || fail 'mobile file list no longer preserves file path download key'
grep -Fq 'uploaderName' "$PAGE" || fail 'mobile file list no longer preserves uploader metadata'
grep -Fq 'typeof item.fileSize === '\''number'\''' "$PAGE" || fail 'mobile file list no longer formats numeric file sizes'
grep -Fq 'uploaderNameText' "$PAGE" || fail 'mobile file list no longer normalizes uploader metadata'
grep -Fq 'file.metaExtra' "$WXML" || fail 'mobile file list no longer displays uploader metadata'
# 【2026-10-26 重锚】用户令"一次点击一步打开 + 默认下载缓存": 点行 openFile 直达预览(委托
# previewFile→fetchFile), 列表页不再跳文件详情页(详情页文件保留, 契约仍钉其归一化字段);
# 缓存走 utils/file-cache(lookup 命中秒开 / store 落地, 均 fail-open)。
grep -Fq "this.previewFile(event)" "$PAGE" || fail 'mobile file one-tap open must delegate to the preview pipeline'
! grep -Fq '/pages/files/detail/index?' "$PAGE" || fail 'file list must not navigate back to the detail page (one-tap open, user order 2026-10-26)'
grep -Fq "require('../../utils/file-cache')" "$PAGE" || fail 'mobile file preview cache module is not wired'
grep -Fq 'fileCache.lookup(item)' "$PAGE" || fail 'mobile file preview must check the local cache before downloading'
grep -Fq 'fileCache.store(item, result.tempFilePath)' "$PAGE" || fail 'mobile file preview must store downloads into the local cache'
# 【2026-10-27 深度检查修复批】缓存安全三钉: ①键带用户/环境维度+version 优先(替换换版即失效)
# ②登出清缓存(auth.js logout 接 clear) ③惰性 DIR——模块顶层读 wx.env 曾把 Node 契约桩打崩(contracts 假红根因)
grep -Fq 'function scopeSlug()' "$CACHE" || fail 'mobile file cache key must be scoped by user and api env (cross-account offline read fix)'
grep -Fq 'item.version || item.updatedAt' "$CACHE" || fail 'mobile file cache key must prefer the backend version field (replace bumps version, not updatedAt)'
grep -Fq "module.exports = { lookup, store, clear }" "$CACHE" || fail 'mobile file cache must export clear() for logout'
grep -Fq "require('./file-cache').clear()" "$ROOT/aigicrm-miniprogram/utils/auth.js" || fail 'mobile logout must clear the file preview cache (cross-account leak fix)'
grep -Fq "wx.env.USER_DATA_PATH + '/file-cache'" "$CACHE" && ! grep -Fq '^const DIR' "$CACHE" || fail 'mobile file cache must resolve its dir lazily, never at module top level'
# 【同批】点行预览与下载同门(无下载权限提前拦, 缓存命中不得绕过); dlToken 缓存带用户维度(换号即废)
grep -Fq "if (!this.canDownloadItem(item)) return wx.showToast({ title: '该文件夹已关闭下载', icon: 'none' })
    this.fetchFile(item, 'preview')" "$PAGE" || fail 'mobile file preview must gate on download permission like downloadFile'
grep -Fq 'this._dlTokenUserId !== userId' "$PAGE" || fail 'mobile files dlToken cache must be invalidated on account switch'
# 【2026-10-26 二令"还是做成之前列表展示,但列表要呈现预览的小图"】四项服务 tab 回归时间分组
# 列表行, 行首图片=缩略小图(一次性 dlToken URL); 同日更早的图片墙(墙瓦片/previewWallImage)退役
grep -Fq 'function isImageRow(item)' "$PAGE" || fail 'mobile file incentive row thumbnail classifier is missing'
grep -Fq "rowThumbUrl = data.activeTab === '四项服务' && isImageRow(item)" "$PAGE" || fail 'mobile file incentive rows must build thumbnails for the incentive tab only'
grep -Fq 'withDownloadToken(getDownloadUrl({ id: item.id, ossKey: item.ossKey || item.fileUrl }, true), data.dlToken' "$PAGE" || fail 'mobile file row thumbnails must carry the one-time download token'
grep -Fq 'class="file-thumb-sq"' "$WXML" || fail 'mobile file incentive row thumbnail UI disappeared'
! grep -Fq 'files-wall' "$WXML" || fail 'mobile file photo wall was replaced by list rows (user order 2026-10-26); do not bring it back'
! grep -Fq 'previewWallImage' "$PAGE" "$WXML" || fail 'mobile file wall tap handler was removed with the wall (user order 2026-10-26)'
grep -Fq 'previewFile' "$PAGE" || fail 'mobile file list preview action disappeared'
grep -Fq 'downloadFile' "$PAGE" || fail 'mobile file list download action disappeared'
grep -Fq 'wx.downloadFile' "$PAGE" || fail 'mobile file list cannot download files for preview'
grep -Fq 'wx.previewImage' "$PAGE" || fail 'mobile file list cannot preview images'
grep -Fq 'wx.openDocument' "$PAGE" || fail 'mobile file list cannot open documents'
grep -Fq 'wx.saveFile' "$PAGE" || fail 'mobile file list cannot save downloaded files'
# 【钉随需求A】行内 previewFile 收敛为整行 bindtap="openFile"(点行即预览, 长按呼面板)
grep -Fq 'bindtap="openFile"' "$WXML" || fail 'mobile file list preview UI disappeared'
# 【钉随需求A】行内 downloadFile 收敛到操作面板 onSheetAction(JS downloadFile 方法保留)
grep -Fq 'catchtap="openActionSheet"' "$WXML" || fail 'mobile file list download UI disappeared'
# 【钉随需求A 2026-10-16】行内可见性按钮全部收敛进「⋯」面板: 行级可见性归一化仍在 JS(buildFileRowView),
# 但 WXML 不再引用 item.showXxx, 改为渲染 actionSheet.actions(下载/分享/替换/删除按归一化结果进面板)
grep -Fq 'const showDownload = item.canDownload !== false' "$PAGE" || fail 'mobile file list download visibility is not normalized like PC'
grep -Fq "if (showDownload) sheetActions.push({ key: 'download'" "$PAGE" || fail 'mobile file list download UI no longer hides non-downloadable rows like PC'
# 【2026-10-26 重锚】用户令"三个点不需要替换"——面板替换项退役, 两钉改防复活
! grep -Fq "sheetActions.push({ key: 'replace'" "$PAGE" || fail 'file sheet replace action was retired by user order (2026-10-26)'
! grep -Fq 'replacingId' "$PAGE" || fail 'file replace state was retired by user order (2026-10-26); replacingId must not come back'
grep -Fq "if (showDelete) sheetActions.push({ key: 'delete'" "$PAGE" || fail 'mobile file list delete UI must use normalized row visibility'
grep -Fq "if (showShare) sheetActions.push({ key: 'share'" "$PAGE" || fail 'mobile file list share UI must use normalized row visibility'
grep -Fq 'catchtap="onSheetAction"' "$WXML" || fail 'mobile file action sheet dispatch UI disappeared'
grep -Fq 'fileView.showManageFolders' "$WXML" || fail 'mobile file folder management list must use normalized visibility'
grep -Fq 'fileView.showManageEmpty' "$WXML" || fail 'mobile file folder management empty state must use normalized visibility'
! grep -Fq 'activeTab === item' "$WXML" || fail 'mobile file WXML must not compute active tab state'
! grep -Fq 'tabCounts[item]' "$WXML" || fail 'mobile file WXML must not index dynamic tab counts directly'
! grep -Fq 'activeFolderName ||' "$WXML" || fail 'mobile file WXML must not duplicate folder title fallback logic'
! grep -Fq 'folders.length ||' "$WXML" || fail 'mobile file WXML must not compute folder count fallback'
! grep -Fq 'filteredFiles.length' "$WXML" || fail 'mobile file WXML must not inspect filtered file length directly'
! grep -Fq 'fileView.folders.length' "$WXML" || fail 'mobile file WXML must not inspect folder array length directly'
! grep -Fq 'item.uploaderName ||' "$WXML" || fail 'mobile file WXML must not duplicate uploader fallback logic'
! grep -Fq "activeTab !== '共享给我'" "$WXML" || fail 'mobile file WXML must not duplicate shared-tab action visibility logic'
grep -Fq 'getDownloadUrl' "$DETAIL" || fail 'mobile file detail no longer supports path-download fallback'
# 【mp-0 2026-10-18】参数统一走 safeDecodeParam（单次解码+非法转义回退），不再裸 decodeURIComponent
grep -Fq 'uploaderName: safeDecodeParam' "$DETAIL" || fail 'mobile file detail no longer receives uploader metadata'
grep -Fq 'updatedAt: safeDecodeParam' "$DETAIL" || fail 'mobile file detail no longer receives updated time metadata'
grep -Fq 'buildDetailView' "$DETAIL" || fail 'mobile file detail must normalize view state before WXML'
grep -Fq 'setDetailData' "$DETAIL" || fail 'mobile file detail must refresh normalized view state with data changes'
grep -Fq 'detailView.typeText' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail must display normalized file type text'
grep -Fq 'detailView.uploaderNameText' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail must display normalized uploader text'
grep -Fq '上传者' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail no longer displays uploader metadata'
grep -Fq '更新时间' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail no longer displays updated time metadata'
! grep -Fq 'type ||' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail WXML must not duplicate type fallback logic'
! grep -Fq 'folder ||' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail WXML must not duplicate folder fallback logic'
! grep -Fq 'size ||' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail WXML must not duplicate size fallback logic'
! grep -Fq 'uploaderName ||' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail WXML must not duplicate uploader fallback logic'
! grep -Fq 'updatedAt ||' "$ROOT/aigicrm-miniprogram/pages/files/detail/index.wxml" || fail 'mobile file detail WXML must not duplicate update-time fallback logic'
grep -Fq 'verify:file-center' "$AUTOMATOR_PACKAGE" || fail 'file center DevTools automator script is not exposed'
grep -Fq 'verifyFileCenterPage' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify page UI'
grep -Fq 'mockWxMethod' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not mock native file actions'
grep -Fq 'calls.downloadFile.length === 2' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not assert downloadFile calls'
grep -Fq 'canDownload: false' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not cover non-downloadable row hiding'
grep -Fq '仅预览资料.pdf' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify non-downloadable metadata row'
grep -Fq 'calls.previewImage.length === 1' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not assert image preview calls'
grep -Fq 'calls.openDocument.length === 1' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not assert document preview calls'
grep -Fq 'file-center-actions.png' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not capture UI evidence'
grep -Fq '张三' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify uploader metadata'
grep -Fq '私人3' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify tab count metadata'
# 【2026-10-26 重锚】详情页跳转断言(uploaderName=/updatedAt=)随"一步打开"退役
grep -Fq '2.8 MB' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify formatted file size metadata'
grep -Fq 'aclVisible: true' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify folder ACL modal'
# 【2026-10-26 重锚】用户令"一次点击一步打开"——automator 详情页跳转断言退役,
# 改钉一步预览下载断言; 用户令"三个点不需要替换"——列表页替换入口/权限门/调用清退
# (后端 replaceFile API 与详情页替换能力保留: 下方两处钉 API/详情页)
grep -Fq 'one-tap preview download missing' "$AUTOMATOR_SCRIPT" || fail 'file center DevTools automator does not verify one-tap preview download'
grep -Fq 'replaceFile' "$API" || fail 'mobile file replace API contract disappeared'
! grep -Fq "hasPermission('file:edit')" "$PAGE" || fail 'file list replace permission gate was retired by user order (2026-10-26)'
! grep -Fq 'replaceListFile' "$PAGE" || fail 'file list replace action was retired by user order (2026-10-26)'
! grep -Fq "replaceFile(item.id, file.path)" "$PAGE" || fail 'file list replace call was retired by user order (2026-10-26)'
# 【钉随需求A】行内 replaceListFile 收敛到操作面板 onSheetAction(JS 方法保留)
grep -Fq 'catchtap="openActionSheet"' "$WXML" || fail 'mobile file list replace UI disappeared'
! grep -Fq 'replacingId===item.id' "$WXML" || fail 'mobile file WXML must not compare replacing id directly'
grep -Fq "hasPermission('file:edit')" "$DETAIL" || fail 'mobile file replace permission gate disappeared'
grep -Fq 'chooseMessageFile' "$DETAIL" || fail 'mobile file replace picker disappeared'
grep -Fq "hasPermission('file:folder:manage')" "$PAGE" || fail 'mobile file folder manage permission gate disappeared'
grep -Fq 'openFolderForm' "$PAGE" || fail 'mobile file folder create navigation disappeared'
grep -Fq 'openFolderEdit' "$PAGE" || fail 'mobile file folder edit navigation disappeared'
grep -Fq 'removeFolder' "$PAGE" || fail 'mobile file folder delete action disappeared'
grep -Fq 'bindtap="openFolderForm"' "$WXML" || fail 'mobile file folder create UI disappeared'
grep -Fq "label: '重命名'" "$PAGE" || fail 'mobile file folder rename action disappeared'
grep -Fq "label: '删除'" "$PAGE" || fail 'mobile file folder delete action disappeared'
grep -Fq "hasPermission('file:folder:manage')" "$PAGE" || fail 'mobile file folder permission gate (create+manage) disappeared'
# 【2026-10-07 钉随代码】D36: 创建分支自 folder-form 移除(口径不符且无入口), 创建=files 页 showModal+createFolder
grep -Fq 'createFolder' "$PAGE" || fail 'mobile file folder create API disappeared'
grep -Fq '新建文件夹' "$PAGE" || fail 'mobile file folder create copy moved to files page'
grep -Fq 'renameFolder' "$FOLDER_FORM" || fail 'mobile file folder rename API disappeared'
grep -Fq 'buildFolderFormView' "$FOLDER_FORM" || fail 'mobile file folder form display state must be normalized in JS'
grep -Fq 'applyState(patch' "$FOLDER_FORM" || fail 'mobile file folder form normalized state refresh is missing'
grep -Fq 'folderFormView.titleText' "$FOLDER_FORM_WXML" || fail 'mobile file folder form title must use normalized view state'
grep -Fq 'folderFormView.nameValue' "$FOLDER_FORM_WXML" || fail 'mobile file folder form input must use normalized view state'
grep -Fq 'folderFormView.submitLoading' "$FOLDER_FORM_WXML" || fail 'mobile file folder form loading must use normalized view state'

grep -Fq '保存名称' "$FOLDER_FORM" || fail 'mobile file folder rename form copy disappeared'
! grep -Eq "id \\?|\\{\\{id|\\{\\{name\\}\\}|\\{\\{saving\\}\\}|maxlength=\"60\"" "$FOLDER_FORM_WXML" \
  || fail 'mobile file folder form WXML must not keep id/name/saving fallback logic'
grep -Fq "showError(error, '分享失败')" "$PAGE" || fail 'mobile file share failure feedback disappeared'
grep -Fq 'catch((error) => showError(error, '\''替换失败'\''))' "$DETAIL" || fail 'mobile file replace failure feedback disappeared'
# 【2026-10-07 钉随代码】1b3c865b 死页面清理删除 pages/file 跳转壳(app.json 已无该注册, 无存量导航可达);
# 兼容目标"不再有旧文件中心"由"页面不存在"达成, 保底防复活
if [ -e "$LEGACY_PAGE" ] || [ -e "$LEGACY_WXML" ]; then
  fail 'legacy file page must not be revived (removed with app.json registration in 1b3c865b)'
fi
grep -Fq '@PostMapping("/{id}/share")' "$CONTROLLER" || fail 'backend file share route disappeared'
grep -Fq '@PostMapping("/{id}/replace")' "$CONTROLLER" || fail 'backend file replace route disappeared'
grep -Fq '`file.folderManage` | 文件 | 文件夹面板管理 | `pages/files/index` 与 `pages/files/folder-form/index`' "$MANIFEST" || fail 'file folderManage manifest does not point to mobile folder management'
grep -Fq '`file.aclView` | 文件 | 文件夹 ACL 弹窗只读 | `pages/files/index` 只读 ACL 弹窗' "$MANIFEST" || fail 'file aclView manifest does not protect mobile read-only ACL view'
echo 'PASS: mobile file high-risk boundary is aligned.'
