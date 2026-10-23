# AIGICRM Unified Feature Audit

Generated from local source: `.`

## Summary

- PC routes: 30
- PC API functions: 165
- Mini Program pages: 46
- Mini Program API functions: 81
- Backend endpoints: 188
- PC UI actions: 205
- Mini Program UI actions: 171
- Backend permission tokens: 60
- Permission dictionary tokens: 68
- Runtime permission dictionary tokens: 59
- Compatibility/deprecated dictionary tokens: 9
- Local migration permission tokens: 34
- Backend permissions missing in dictionary or local migrations: 0
- Runtime dictionary permissions not directly seen in backend annotations: 2
- Runtime dictionary permissions referenced outside backend annotations: 2
- Runtime dictionary permissions not seen in backend annotations or code references: 0
- UI permission tokens missing in backend, dictionary, and local migrations: 0
- Frontend code permission tokens: 48
- Frontend code permission tokens missing in known sources: 0
- PC API wrappers without backend endpoint match: 0
- Mini Program API wrappers without backend endpoint match: 0

## PC Routes

| Route | Title | Permission | Source |
| --- | --- | --- | --- |
| `/login` | 登录 | `` | `aigicrm-ui/src/router/index.js` |
| `/403` | 无权限 | `` | `aigicrm-ui/src/router/index.js` |
| `/` | 工作台 | `dashboard:view` | `aigicrm-ui/src/router/index.js` |
| `customer` | 客户列表 | `customer:view` | `aigicrm-ui/src/router/index.js` |
| `customer/add` | 新增客户 | `customer:add` | `aigicrm-ui/src/router/index.js` |
| `customer/edit/:id` | 编辑客户 | `customer:edit` | `aigicrm-ui/src/router/index.js` |
| `customer/detail/:id` | 客户详情 | `customer:view` | `aigicrm-ui/src/router/index.js` |
| `file` | 文件管理 | `file:view` | `aigicrm-ui/src/router/index.js` |
| `incentive` | 四项服务 | `incentive:add` | `aigicrm-ui/src/router/index.js` |
| `incentive/stats` | 激励统计 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/search` | 激励查询 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/ranking` | 积分排行 | `score:view` | `aigicrm-ui/src/router/index.js` |
| `reminder` | 提醒管理 | `reminder:view` | `aigicrm-ui/src/router/index.js` |
| `sea` | 公海池 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `sea/records` | 捞取记录 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `approval` | 审批管理 | `approval:view` | `aigicrm-ui/src/router/index.js` |
| `statistics` | 统计分析 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `score/entry` | 积分录入 | `score:entry` | `aigicrm-ui/src/router/index.js` |
| `score/ranking` | 积分排行 | `score:view` | `aigicrm-ui/src/router/index.js` |
| `ranking/customer` | 客户排行 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `indicator` | 核心客户六项指标 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `system/user` | 人员管理 | `system:user:view` | `aigicrm-ui/src/router/index.js` |
| `system/org` | 组织架构 | `system:dept:view` | `aigicrm-ui/src/router/index.js` |
| `system/dept` | 组织架构 | `system:dept:view` | `aigicrm-ui/src/router/index.js` |
| `system/role` | 角色管理 | `system:role:view` | `aigicrm-ui/src/router/index.js` |
| `system/position` | 岗位管理 | `system:position:view` | `aigicrm-ui/src/router/index.js` |
| `system/config` | 系统设置 | `system:config:view` | `aigicrm-ui/src/router/index.js` |
| `system/log` | 日志管理 | `system:log:view` | `aigicrm-ui/src/router/index.js` |
| `system/rule` | 权限规则 | `system:rule:view` | `aigicrm-ui/src/router/index.js` |
| `system/group` | 集团管理 | `system:group:view` | `aigicrm-ui/src/router/index.js` |

## Mini Program Pages

| Page |
| --- |
| `pages/login/index` |
| `pages/login/forgot/index` |
| `pages/home/index` |
| `pages/customer/index` |
| `pages/customer/detail/index` |
| `pages/customer/form/index` |
| `pages/customer/finance/index` |
| `pages/customer/finance/form/index` |
| `pages/customer/contact/index` |
| `pages/customer/contact/form/index` |
| `pages/customer/contact/detail/index` |
| `pages/customer/share/index` |
| `pages/customer/log/index` |
| `pages/customer/timeline/index` |
| `pages/customer/filter/index` |
| `pages/customer/indicator/index` |
| `pages/file/index` |
| `pages/files/index` |
| `pages/files/folder-form/index` |
| `pages/files/detail/index` |
| `pages/incentive/index` |
| `pages/incentive/upload/index` |
| `pages/incentive/detail/index` |
| `pages/ranking/index` |
| `pages/ranking/detail/index` |
| `pages/statistics/index` |
| `pages/statistics/detail/index` |
| `pages/cases/index` |
| `pages/cases/detail/index` |
| `pages/indicator/index` |
| `pages/app/index` |
| `pages/profile/index` |
| `pages/profile/password/index` |
| `pages/profile/scope/index` |
| `pages/profile/feedback/index` |
| `pages/profile/about/index` |
| `pages/profile/agreement/index` |
| `pages/profile/privacy/index` |
| `pages/profile/wechat/index` |
| `pages/approval/index` |
| `pages/approval/detail/index` |
| `pages/reminder/index` |
| `pages/notification/index` |
| `pages/notification/detail/index` |
| `pages/sea/index` |
| `pages/sea/claim-detail/index` |

## PC UI Actions

| Label | Handler | Permission Hint | Source |
| --- | --- | --- | --- |
| (icon/button) | `openDetail(row)` | `` | `aigicrm-ui/src/views/approval/index.vue:146` |
| (icon/button) | `handleApprove(row)` | `canApprove` | `aigicrm-ui/src/views/approval/index.vue:175` |
| (icon/button) | `handleReject(row)` | `canReject` | `aigicrm-ui/src/views/approval/index.vue:178` |
| 关闭 | `detailVisible = false` | `` | `aigicrm-ui/src/views/approval/index.vue:255` |
| 驳回 | `handleReject(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:256` |
| 通过 | `handleApprove(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:262` |
| 清空全部 | `clearAdvFilter` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:62` |
| 取消 | `showAdvFilter = false` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:63` |
| 确定 | `showAdvFilter = false` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:64` |
| 全选 | `setAllCols` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:4` |
| 全不选 | `clearCols` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:5` |
| 取消 | `colPickerVisible = false` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:12` |
| 确定 | `colPickerVisible = false` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:12` |
| 添加联系人 | `addContactRow` | `` | `aigicrm-ui/src/views/customer/ContactsSection.vue:6` |
| (icon/button) | `contacts.splice(idx, 1)` | `` | `aigicrm-ui/src/views/customer/ContactsSection.vue:16` |
| 下载导入模板 (.xlsx) | `downloadTemplate` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:4` |
| 取消 | `showImportDialog = false` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:19` |
| 开始导入 | `doImport` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:20` |
| 列设置 | `colPickerVisible = true` | `` | `aigicrm-ui/src/views/customer/MainTable.vue:24` |
| 释放 | `openRelease(row)` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:77` |
| 删除 | `handleDelete(row)` | `canDelete` | `aigicrm-ui/src/views/customer/MainTable.vue:78` |
| 取消 | `showReleaseDialog = false` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:19` |
| 确认释放 | `confirmRelease` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:20` |
| 高级筛选 0" size="small" type="warning" style="margin-left:4px"> | `showAdvFilter = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:4` |
| 批量导入 | `showImportDialog = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:7` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:8` |
| 新客户 | `$router.push('/customer/add')` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:9` |
| 返回 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:4` |
| 取消 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:24` |
| (icon/button) | `handleSubmit` | `` | `aigicrm-ui/src/views/customer/add.vue:25` |
| + 录入开票 | `showInvoiceDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:10` |
| (icon/button) | `handleDeleteInvoice(row)` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:24` |
| + 录入回款 | `showPaymentDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:37` |
| (icon/button) | `handleDeletePayment(row)` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:48` |
| 取消 | `showInvoiceDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:62` |
| 确认录入 | `handleAddInvoice` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:62` |
| 取消 | `showPaymentDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:72` |
| 确认录入 | `handleAddPayment` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:72` |
| 保存指标 | `handleSaveIndicator` | `` | `aigicrm-ui/src/views/customer/detail/IndicatorTab.vue:17` |
| 申请延期 | `handleExtend` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:53` |
| 添加 | `editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:65` |
| (icon/button) | `openEditContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:75` |
| (icon/button) | `handleDeleteContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:76` |
| 取消 | `showContactDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:101` |
| 确认 | `handleSaveContact` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:101` |
| 记录 | `submitLog` | `` | `aigicrm-ui/src/views/customer/detail/LogsTab.vue:5` |
| 编辑 | `handleEdit` | `canEditCustomer` | `aigicrm-ui/src/views/customer/detail.vue:9` |
| 分享 | `showShareDialog = true` | `canShareCustomer` | `aigicrm-ui/src/views/customer/detail.vue:10` |
| 取消 | `showShareDialog=false` | `` | `aigicrm-ui/src/views/customer/detail.vue:110` |
| 确认分享 | `handleAddShare` | `` | `aigicrm-ui/src/views/customer/detail.vue:110` |
| 通过 | `doApprove(item.id)` | `` | `aigicrm-ui/src/views/dashboard/index.vue:140` |
| 驳回 | `doReject(item.id)` | `` | `aigicrm-ui/src/views/dashboard/index.vue:141` |
| 返回登录 | `goLogin` | `` | `aigicrm-ui/src/views/error/403.vue:7` |
| 保存授权 | `saveAcl` | `` | `aigicrm-ui/src/views/file/AclDialog.vue:14` |
| 移除 | `removeAcl(row)` | `` | `aigicrm-ui/src/views/file/AclDialog.vue:36` |
| 取消 | `createFolderVisible = false` | `` | `aigicrm-ui/src/views/file/CreateFolderDialog.vue:9` |
| 创建 | `handleCreateFolder` | `` | `aigicrm-ui/src/views/file/CreateFolderDialog.vue:10` |
| 新建文件夹 | `openCreateFolder` | `canCreateFolder` | `aigicrm-ui/src/views/file/FolderPanel.vue:37` |
| 取消 | `renameFolderVisible = false` | `` | `aigicrm-ui/src/views/file/RenameFolderDialog.vue:9` |
| 保存 | `handleRenameFolder` | `` | `aigicrm-ui/src/views/file/RenameFolderDialog.vue:10` |
| 取消 | `shareDialogVisible = false` | `` | `aigicrm-ui/src/views/file/ShareDialog.vue:10` |
| 确认分享 | `confirmShare` | `` | `aigicrm-ui/src/views/file/ShareDialog.vue:11` |
| 取消 | `uploadVisible = false` | `` | `aigicrm-ui/src/views/file/UploadDialog.vue:22` |
| 开始上传{{ pendingFiles.length ? `（${pendingFiles.length} 个）` : '' }} | `startUploads` | `` | `aigicrm-ui/src/views/file/UploadDialog.vue:23` |
| 上传文件 | `openUpload` | `` | `aigicrm-ui/src/views/file/index.vue:14` |
| 导出 | `exportRank('dept')` | `` | `aigicrm-ui/src/views/incentive/DeptRankTableView.vue:8` |
| 导出 | `exportRank('personal')` | `` | `aigicrm-ui/src/views/incentive/RankTableView.vue:8` |
| 追加 | `startReplace(row)` | `` | `aigicrm-ui/src/views/incentive/RecordTable.vue:59` |
| 删除 | `handleDeleteRecord(row)` | `` | `aigicrm-ui/src/views/incentive/RecordTable.vue:60` |
| ← 返回激励统计 | `goBack` | `` | `aigicrm-ui/src/views/incentive/StatsDetail.vue:6` |
| 上传文件 | `startUpload(item)` | `userStore.hasPermission('incentive:add')` | `aigicrm-ui/src/views/incentive/SubCategoryPanel.vue:16` |
| 上传文件 | `startUpload(group)` | `userStore.hasPermission('incentive:add')` | `aigicrm-ui/src/views/incentive/SubCategoryPanel.vue:25` |
| 取消 | `showCustomerDialog=false` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:171` |
| (icon/button) | `confirmUpload` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:172` |
| 高级筛选 | `showFilter=!showFilter` | `` | `aigicrm-ui/src/views/incentive/search.vue:6` |
| 查询 | `doSearch` | `` | `aigicrm-ui/src/views/incentive/search.vue:13` |
| 重置 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:14` |
| 清空筛选 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:22` |
| 下载 | `downloadPreview` | `` | `aigicrm-ui/src/views/incentive/search.vue:119` |
| (icon/button) | `previewVisible=false` | `` | `aigicrm-ui/src/views/incentive/search.vue:120` |
| 仍要下载试试 | `downloadPreview` | `` | `aigicrm-ui/src/views/incentive/search.vue:130` |
| (icon/button) | `preview(f.id, f.type, f.label, previewableOf(r))` | `` | `aigicrm-ui/src/views/incentive/search.vue:227` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:228` |
| (icon/button) | `preview(f.id, f.type, f.label, previewableOf(r))` | `` | `aigicrm-ui/src/views/incentive/search.vue:237` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:238` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:245` |
| 关闭 | `detailVisible=false` | `` | `aigicrm-ui/src/views/incentive/search.vue:258` |
| 0 ? 'info' : 'primary'" :disabled="countdown > 0 || !form.method" :loading="sendingCode" @click="handleSendCode" size="large" class="send-btn"> | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:61` |
| 1" @click="handleBack">上一步 | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:88` |
| 取消 | `visible = false` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:89` |
| (icon/button) | `handleNext` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:90` |
| 登 录 | `handleLogin` | `` | `aigicrm-ui/src/views/login/index.vue:34` |
| ← 返回提醒管理 | `goBack` | `` | `aigicrm-ui/src/views/reminder/detail.vue:4` |
| 查看客户完整档案 › | `goCustomer` | `` | `aigicrm-ui/src/views/reminder/detail.vue:52` |
| 标记已处理 | `markProcessed` | `` | `aigicrm-ui/src/views/reminder/detail.vue:73` |
| 返回列表 | `goBack` | `` | `aigicrm-ui/src/views/reminder/detail.vue:79` |
| 一键清空 | `handleClearAll` | `` | `aigicrm-ui/src/views/reminder/index.vue:28` |
| 详情 | `openDetail(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:66` |
| 已读 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:68` |
| 已读 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:71` |
| 已跟进 | `handleFollowUp(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:74` |
| 已跟进 | `` | `` | `aigicrm-ui/src/views/reminder/index.vue:75` |
| 申请延期 | `handleExtend(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:76` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/score/entry.vue:20` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/score/entry.vue:21` |
| + | `` | `` | `aigicrm-ui/src/views/score/entry.vue:58` |
| (icon/button) | `handleDeleteRow(item,idx)` | `` | `aigicrm-ui/src/views/score/entry.vue:85` |
| 添加行 | `handleAddRow` | `` | `aigicrm-ui/src/views/score/entry.vue:96` |
| 积分规则 | `showRules = true` | `` | `aigicrm-ui/src/views/score/ranking.vue:10` |
| 捞取记录 | `router.push('/sea/records')` | `` | `aigicrm-ui/src/views/sea/index.vue:15` |
| 批量导入 | `showImportDialog=true` | `canImport` | `aigicrm-ui/src/views/sea/index.vue:16` |
| 重置 | `resetFilters` | `` | `aigicrm-ui/src/views/sea/index.vue:81` |
| 捞取 | `handleClaim(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:125` |
| 编辑 | `handleEdit(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:132` |
| 删除 | `handleDelete(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:133` |
| 下载导入模板 | `handleDownloadTemplate` | `` | `aigicrm-ui/src/views/sea/index.vue:145` |
| (icon/button) | `showImportDialog=false` | `` | `aigicrm-ui/src/views/sea/index.vue:160` |
| 开始导入 | `handleImport` | `` | `aigicrm-ui/src/views/sea/index.vue:160` |
| 公海池 | `router.push('/sea')` | `` | `aigicrm-ui/src/views/sea/records.vue:8` |
| 刷新 | `loadAll` | `` | `aigicrm-ui/src/views/statistics/index.vue:23` |
| 上传Logo | `` | `` | `aigicrm-ui/src/views/system/BasicSettings.vue:20` |
| 清除 | `configMap['system.logo'] = ''` | `canEditConfig && configMap['system.logo']` | `aigicrm-ui/src/views/system/BasicSettings.vue:22` |
| 保存设置 | `handleSave` | `canEditConfig` | `aigicrm-ui/src/views/system/BasicSettings.vue:85` |
| 新增类别 | `openCategoryDialog()` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:13` |
| 编辑 | `openCategoryDialog(top)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:27` |
| 子项 | `openCategoryDialog(null, top.id)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:28` |
| 删除 | `handleDeleteCategory(top)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:29` |
| 编辑 | `openCategoryDialog(row)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:61` |
| 删除 | `handleDeleteCategory(row)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:62` |
| 添加授权 | `openPermDialog` | `userStore.hasPermission('system:config:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:83` |
| 撤销 | `revokePerm(row.id)` | `userStore.hasPermission('system:config:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:124` |
| 取消 | `categoryDialogVisible = false` | `` | `aigicrm-ui/src/views/system/CategoryManager.vue:163` |
| 保存 | `submitCategory` | `` | `aigicrm-ui/src/views/system/CategoryManager.vue:164` |
| 新增 | `handleAddType` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:7` |
| 编辑 | `handleEditType(t)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:24` |
| 删除 | `handleDeleteType(t)` | `canEditConfig && !t.builtIn` | `aigicrm-ui/src/views/system/DictManager.vue:25` |
| 新增 | `handleAddData` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:40` |
| 编辑 | `handleEditData(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:53` |
| 删除 | `handleDeleteData(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:54` |
| 取消 | `typeDialogVisible = false` | `` | `aigicrm-ui/src/views/system/DictManager.vue:78` |
| 确定 | `submitType` | `` | `aigicrm-ui/src/views/system/DictManager.vue:79` |
| 取消 | `dataDialogVisible = false` | `` | `aigicrm-ui/src/views/system/DictManager.vue:100` |
| 确定 | `submitData` | `` | `aigicrm-ui/src/views/system/DictManager.vue:101` |
| 全选 | `checkAllUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:18` |
| 清空 | `clearUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:19` |
| 取消 | `showPermDialog=false` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:69` |
| 确认授权 | `grantPerm` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:70` |
| (icon/button) | `btn.tab ? activeTab=btn.tab : router.push(btn.href)` | `` | `aigicrm-ui/src/views/system/PermMatrixManager.vue:21` |
| 刷新 | `loadPermData` | `` | `aigicrm-ui/src/views/system/PermMatrixManager.vue:32` |
| 刷新 | `loadAll` | `` | `aigicrm-ui/src/views/system/PermScopeManager.vue:14` |
| 新增公海池 | `handleAddPool` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:6` |
| 编辑 | `handleEditPool(pool)` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:21` |
| 删除 | `handleDeletePool(pool)` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:22` |
| 清空 | `clearSeaSelection` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:65` |
| 清空 | `clearSecSelection(sec.key)` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:115` |
| 取消 | `poolDialogVisible = false` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:148` |
| 确定 | `submitPool` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:149` |
| 新增用户 | `openDialog()` | `userStore.hasPermission('system:user:add')` | `aigicrm-ui/src/views/system/UserFilterBar.vue:8` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/UserFormDialog.vue:101` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/UserFormDialog.vue:102` |
| 编辑 | `openDialog(row)` | `userStore.hasPermission('system:user:edit')` | `aigicrm-ui/src/views/system/UserTable.vue:57` |
| 重置密码 | `handleResetPwd(row)` | `userStore.hasPermission('system:user:edit')` | `aigicrm-ui/src/views/system/UserTable.vue:58` |
| 删除 | `handleDelete(row)` | `userStore.hasPermission('system:user:delete')` | `aigicrm-ui/src/views/system/UserTable.vue:59` |
| 新增流程 | `openDialog()` | `canEditConfig` | `aigicrm-ui/src/views/system/approvalConfig.vue:6` |
| 编辑 | `openDialog(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/approvalConfig.vue:28` |
| 删除 | `` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:30` |
| 取消 | `visible=false` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:72` |
| 保存 | `doSave` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:73` |
| 新增部门 | `openDialog()` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:5` |
| 管理{{ adminCounts[row.id] ? ` (${adminCounts[row.id]})` : '' }} | `showAdmins(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:35` |
| 编辑 | `openDialog(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:36` |
| 添加子部门 | `openDialog(null, row.id)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:37` |
| 删除 | `handleDelete(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:38` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/dept.vue:78` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/dept.vue:79` |
| 添加 | `doAddAdmin` | `` | `aigicrm-ui/src/views/system/dept.vue:91` |
| 移除 | `` | `` | `aigicrm-ui/src/views/system/dept.vue:100` |
| 新增集团 | `openDialog()` | `canAddGroup` | `aigicrm-ui/src/views/system/group.vue:5` |
| 编辑 | `openDialog(row)` | `canEditGroup` | `aigicrm-ui/src/views/system/group.vue:23` |
| 删除 | `handleDelete(row)` | `canDeleteGroup` | `aigicrm-ui/src/views/system/group.vue:24` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/group.vue:50` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/group.vue:51` |
| 刷新 | `load` | `` | `aigicrm-ui/src/views/system/incentive-read-ranking.vue:19` |
| 查看 | `showDetail(row)` | `` | `aigicrm-ui/src/views/system/log.vue:52` |
| 新增岗位 | `openDialog()` | `canAddPosition` | `aigicrm-ui/src/views/system/position.vue:5` |
| 编辑 | `openDialog(row)` | `canEditPosition` | `aigicrm-ui/src/views/system/position.vue:33` |
| 规则 | `openRuleDialog(row)` | `canEditPosition` | `aigicrm-ui/src/views/system/position.vue:34` |
| 删除 | `handleDelete(row)` | `canDeletePosition` | `aigicrm-ui/src/views/system/position.vue:35` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/position.vue:72` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/position.vue:73` |
| 取消 | `ruleDialogVisible = false` | `` | `aigicrm-ui/src/views/system/position.vue:98` |
| 保存规则 | `handleSaveRules` | `` | `aigicrm-ui/src/views/system/position.vue:99` |
| 新增角色 | `openDialog()` | `canAddRole` | `aigicrm-ui/src/views/system/role.vue:5` |
| 编辑 | `openDialog(row)` | `canEditRole` | `aigicrm-ui/src/views/system/role.vue:30` |
| 权限 | `openPermDialog(row)` | `canEditRole` | `aigicrm-ui/src/views/system/role.vue:31` |
| 删除 | `handleDelete(row)` | `canDeleteRole` | `aigicrm-ui/src/views/system/role.vue:32` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/role.vue:101` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/role.vue:102` |
| 取消 | `permDialogVisible = false` | `` | `aigicrm-ui/src/views/system/role.vue:124` |
| 保存权限 | `handleSavePerm` | `` | `aigicrm-ui/src/views/system/role.vue:125` |
| 新增规则 | `openDialog()` | `canAddRule` | `aigicrm-ui/src/views/system/rule.vue:5` |
| 编辑 | `openDialog(row)` | `canEditRule` | `aigicrm-ui/src/views/system/rule.vue:31` |
| 删除 | `handleDelete(row)` | `canDeleteRule` | `aigicrm-ui/src/views/system/rule.vue:32` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/rule.vue:92` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/rule.vue:93` |

## Mini Program UI Actions

| Label | Handler | Condition Hint | Source |
| --- | --- | --- | --- |
| › | `openApp` | `` | `aigicrm-miniprogram/pages/app/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:8` |
| 驳回 | `reject` | `{{canReject}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:38` |
| 通过 | `approve` | `{{canApprove}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:39` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:8` |
| 待我处理 | `changeTab` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:16` |
| 全部 | `changeTab` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:17` |
| 已通过 | `changeTab` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:18` |
| 查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:28` |
| 驳回 | `reject` | `{{canReject && (item.status==='pending' || item.status==='PENDING' || item.status==='WAITING' || item.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:29` |
| 通过 | `approve` | `{{canApprove && (item.status==='pending' || item.status==='PENDING' || item.status==='WAITING' || item.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:30` |
| 提交审批 | `submitReleaseApproval` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:38` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:44` |
| 驳回 | `reject` | `{{canReject && (selectedApproval.status==='pending' || selectedApproval.status==='PENDING' || selectedApproval.status==='WAITING' || selectedApproval.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:55` |
| 通过 | `approve` | `{{canApprove && (selectedApproval.status==='pending' || selectedApproval.status==='PENDING' || selectedApproval.status==='WAITING' || selectedApproval.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:56` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:8` |
| 拨打电话 | `call` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:17` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:8` |
| 保存联系人 | `submit` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:24` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:8` |
| 手机号 邮箱 拨打电话 点击卡片查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:13` |
| 拨打电话 | `call` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:17` |
| 新增联系人 | `openForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:22` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:8` |
| 查看明细 | `openFinance` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:55` |
| 申请保护期延期 | `submitExtendApproval` | `{{detail}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:62` |
| 分 | `selectRelationship` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:68` |
| 编辑六项指标 | `openIndicatorForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:83` |
| 释放到公海 | `releaseToSea` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:87` |
| 全部联系人 | `openContacts` | `{{canViewContacts}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:92` |
| 客户分享 | `openShares` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:93` |
| 完整跟进 | `openTimeline` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:94` |
| 新增跟进记录 | `openLogForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:96` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:8` |
| 重置 | `reset` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:19` |
| 应用筛选 | `apply` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:20` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:8` |
| 保存 | `submit` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:34` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:8` |
| 开票明细 | `switchTab` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:12` |
| 回款明细 | `switchTab` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:13` |
| 新增 | `openForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:28` |
| 返回 | `cancel` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:8` |
| 联系人管理› | `openContacts` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:118` |
| 客情 · 五项指标› | `openIndicator` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:121` |
| 取消 | `cancel` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:135` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:136` |
| 海 | `openSea` | `{{canViewSea}}` | `aigicrm-miniprogram/pages/customer/index.wxml:8` |
| ↺ | `resetFilters` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:9` |
| 筛选 | `search` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:15` |
| 高级 | `openAdvancedFilter` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:19` |
| 清空 | `resetFilters` | `{{keyword || filter !== '全部'}}` | `aigicrm-miniprogram/pages/customer/index.wxml:20` |
| · · 产能信息 ● · | `openDetail` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:25` |
| ＋ | `addCustomer` | `{{canAdd}}` | `aigicrm-miniprogram/pages/customer/index.wxml:42` |
| 分 | `selectScore` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:27` |
| 保存指标 | `submit` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:36` |
| 取消 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:37` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:8` |
| 保存记录 | `submit` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:14` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:8` |
| 撤回共享 | `removeShare` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/share/index.wxml:16` |
| 确认分享 | `addShares` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:26` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/timeline/index.wxml:8` |
| 打开 | `previewAttachment` | `` | `aigicrm-miniprogram/pages/customer/timeline/index.wxml:29` |
| 下载 | `downloadAttachment` | `` | `aigicrm-miniprogram/pages/customer/timeline/index.wxml:30` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/files/detail/index.wxml:8` |
| 打开并预览 | `preview` | `` | `aigicrm-miniprogram/pages/files/detail/index.wxml:17` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:9` |
| 取消 | `back` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:10` |
| ＋ | `uploadFile` | `{{canUpload}}` | `aigicrm-miniprogram/pages/files/index.wxml:8` |
| 本地上传自测 | `runDevUploadSmoke` | `{{canDevUploadSmoke}}` | `aigicrm-miniprogram/pages/files/index.wxml:10` |
| (button) | `onTabTap` | `` | `aigicrm-miniprogram/pages/files/index.wxml:22` |
| 返回全部 | `clearFolder` | `{{activeFolderName}}` | `aigicrm-miniprogram/pages/files/index.wxml:24` |
| ▱ | `openFolder` | `` | `aigicrm-miniprogram/pages/files/index.wxml:27` |
| ▱客户文档 | `openFolder` | `{{!folders.length}}` | `aigicrm-miniprogram/pages/files/index.wxml:28` |
| ▱团队共享 | `openFolder` | `{{!folders.length}}` | `aigicrm-miniprogram/pages/files/index.wxml:29` |
| · · 分享 ··· | `openFile` | `` | `aigicrm-miniprogram/pages/files/index.wxml:34` |
| 分享 | `shareFile` | `{{canShare && activeTab !== '共享给我'}}` | `aigicrm-miniprogram/pages/files/index.wxml:37` |
| 新建 | `openFolderForm` | `` | `aigicrm-miniprogram/pages/files/index.wxml:43` |
| 权限 | `viewFolderAcl` | `{{canViewFolderAcl}}` | `aigicrm-miniprogram/pages/files/index.wxml:48` |
| 重命名 | `openFolderEdit` | `` | `aigicrm-miniprogram/pages/files/index.wxml:49` |
| 删除 | `removeFolder` | `` | `aigicrm-miniprogram/pages/files/index.wxml:50` |
| 权限：关闭 加载中... 暂无授权角色 | `closeFolderAcl` | `{{aclVisible}}` | `aigicrm-miniprogram/pages/files/index.wxml:56` |
| 权限：关闭 加载中... 暂无授权角色 | `noop` | `` | `aigicrm-miniprogram/pages/files/index.wxml:57` |
| 关闭 | `closeFolderAcl` | `` | `aigicrm-miniprogram/pages/files/index.wxml:58` |
| ♧ | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:8` |
| ＋新客户 | `openNewCustomer` | `` | `aigicrm-miniprogram/pages/home/index.wxml:34` |
| ♜四项服务 | `openIncentive` | `` | `aigicrm-miniprogram/pages/home/index.wxml:35` |
| ▣激励上报 | `openIncentive` | `` | `aigicrm-miniprogram/pages/home/index.wxml:36` |
| ✓审批中心 | `openApproval` | `` | `aigicrm-miniprogram/pages/home/index.wxml:37` |
| ♧提醒 | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:38` |
| ▥客户统计 | `openStatistics` | `` | `aigicrm-miniprogram/pages/home/index.wxml:39` |
| 全部提醒 › | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:41` |
| · 到期 剩 天 | `openCustomer` | `` | `aigicrm-miniprogram/pages/home/index.wxml:44` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:8` |
| 打开 | `previewAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:35` |
| 下载 | `downloadAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:36` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:8` |
| (button) | `switchTab` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:20` |
| 分 条 文件 NEW 未读 查看后自动标记为已读 客户卡片 | `showDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:40` |
| 客户卡片 | `openCustomer` | `{{item.customerId}}` | `aigicrm-miniprogram/pages/incentive/index.wxml:59` |
| 更新 | `loadScoreboard` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:93` |
| 更新 | `loadReadStats` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:127` |
| 已选 | `chooseSlot` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:75` |
| ＋ | `chooseFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:80` |
| 预览 | `previewSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:95` |
| 下载 | `downloadSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:96` |
| 移除 | `removeSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:97` |
| 提交激励 | `submit` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:101` |
| (button) | `sendCode` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:20` |
| (button) | `next` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:29` |
| (button) | `back` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:30` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/login/index.wxml:17` |
| 忘记密码？ | `openForgot` | `{{!demoMode}}` | `aigicrm-miniprogram/pages/login/index.wxml:18` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:8` |
| 查看客户 | `openCustomer` | `{{customerId}}` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:16` |
| 标记已处理 | `process` | `` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:17` |
| 详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/notification/index.wxml:13` |
| 客户 | `openCustomer` | `{{item.customerId}}` | `aigicrm-miniprogram/pages/notification/index.wxml:14` |
| 已处理 | `process` | `` | `aigicrm-miniprogram/pages/notification/index.wxml:15` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:8` |
| 清空 | `clearDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:18` |
| 保存草稿 | `saveDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:19` |
| ◉我的客户› | `openCustomers` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:23` |
| ★我的四项服务› | `openIncentive` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:24` |
| ↻我的捞取记录› | `openClaims` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:25` |
| ⚿修改密码› | `openPassword` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:28` |
| ◇数据权限说明› | `openScope` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:29` |
| ◌微信绑定待接入 | `openWechat` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:32` |
| □清除缓存› | `clearCache` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:33` |
| ⓘ关于我们› | `openAbout` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:34` |
| ▤用户协议› | `openAgreement` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:35` |
| ▧隐私政策› | `openPrivacy` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:36` |
| 意见反馈 | `openFeedback` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:39` |
| 退出登录 | `logout` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:40` |
| 确认修改 | `submit` | `` | `aigicrm-miniprogram/pages/profile/password/index.wxml:11` |
| 绑定微信 | `bindWechat` | `` | `aigicrm-miniprogram/pages/profile/wechat/index.wxml:7` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/ranking/detail/index.wxml:8` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:2` |
| (button) | `switchTab` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:12` |
| . 分 · 上传 条 条/ 分 · 渠道 · 基层 · 产品 · 项目 开票 万 · 回款 万 · 核心 | `openDetail` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:17` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:8` |
| 待处理 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:13` |
| 到期客户 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:14` |
| 查询 | `search` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:19` |
| 详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:33` |
| 客户 | `openCustomer` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:34` |
| 已处理 | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:35` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:49` |
| 已处理 | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:56` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/sea/claim-detail/index.wxml:8` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:8` |
| 搜索 | `loadRecords` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:17` |
| 编号 原负责人 入池原因 入池时间 可操作权限： | `openCustomer` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:26` |
| 捞取 | `claim` | `{{item.status==='available' && item.canClaim}}` | `aigicrm-miniprogram/pages/sea/index.wxml:36` |
| 编辑 | `editSeaCustomer` | `{{item.canEdit}}` | `aigicrm-miniprogram/pages/sea/index.wxml:37` |
| 最新申请 | `openClaimDetail` | `{{claimRecords.length}}` | `aigicrm-miniprogram/pages/sea/index.wxml:45` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:8` |
| 客户 | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:11` |
| 审批 | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:12` |
| 公海 | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:13` |
| 团队动态 | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:14` |
| 趋势 | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:15` |
| 刷新数据 | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:57` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:12` |
| 展开 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:36` |
| 更新 | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:45` |
| 趋势 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:53` |
| 明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:67` |
| 全部 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:74` |
| 查看统计明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:85` |

## PC API Functions

| Function | Method | Path | Source |
| --- | --- | --- | --- |
| `getApprovalList` | `GET` | `/approval/list` | `aigicrm-ui/src/api/approval.js` |
| `getApprovalDetail` | `GET` | `/approval/${id}` | `aigicrm-ui/src/api/approval.js` |
| `submitApproval` | `POST` | `/approval/submit` | `aigicrm-ui/src/api/approval.js` |
| `approve` | `POST` | `/approval/${id}/approve` | `aigicrm-ui/src/api/approval.js` |
| `reject` | `POST` | `/approval/${id}/reject` | `aigicrm-ui/src/api/approval.js` |
| `getApprovalStatistics` | `GET` | `/approval/statistics` | `aigicrm-ui/src/api/approval.js` |
| `login` | `POST` | `/auth/login` | `aigicrm-ui/src/api/auth.js` |
| `getUserInfo` | `GET` | `/auth/info` | `aigicrm-ui/src/api/auth.js` |
| `logout` | `POST` | `/auth/logout` | `aigicrm-ui/src/api/auth.js` |
| `switchGroup` | `POST` | `/auth/switch-group` | `aigicrm-ui/src/api/auth.js` |
| `forgotPasswordLookup` | `POST` | `/auth/forgot-password/lookup` | `aigicrm-ui/src/api/auth.js` |
| `forgotPasswordSendCode` | `POST` | `/auth/forgot-password/send-code` | `aigicrm-ui/src/api/auth.js` |
| `forgotPasswordReset` | `POST` | `/auth/forgot-password/reset` | `aigicrm-ui/src/api/auth.js` |
| `getCustomerList` | `GET` | `/customer/list` | `aigicrm-ui/src/api/customer.js` |
| `getCustomer` | `GET` | `/customer/${id}` | `aigicrm-ui/src/api/customer.js` |
| `addCustomer` | `POST` | `/customer/add` | `aigicrm-ui/src/api/customer.js` |
| `updateCustomer` | `PUT` | `/customer/${id}` | `aigicrm-ui/src/api/customer.js` |
| `deleteCustomer` | `DELETE` | `/customer/${id}` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerLogs` | `GET` | `/customer/${id}/logs` | `aigicrm-ui/src/api/customer.js` |
| `addCustomerLog` | `POST` | `/customer/${id}/logs` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerStatistics` | `GET` | `/customer/statistics` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerListCounts` | `GET` | `/customer/list/counts` | `aigicrm-ui/src/api/customer.js` |
| `getSimpleUserList` | `GET` | `/customer/users/simple` | `aigicrm-ui/src/api/customer.js` |
| `releaseCustomer` | `POST` | `/customer/${id}/release` | `aigicrm-ui/src/api/customer.js` |
| `getInvoices` | `GET` | `/customer/${id}/invoices` | `aigicrm-ui/src/api/customer.js` |
| `getInvoiceSummary` | `GET` | `/customer/${id}/invoices/summary` | `aigicrm-ui/src/api/customer.js` |
| `addInvoice` | `POST` | `/customer/${id}/invoices` | `aigicrm-ui/src/api/customer.js` |
| `deleteInvoice` | `DELETE` | `/customer/${customerId}/invoices/${invoiceId}` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerGroupNames` | `GET` | `/customer/group-names` | `aigicrm-ui/src/api/customer.js` |
| `getIndicator` | `GET` | `/customer/${id}/indicator` | `aigicrm-ui/src/api/customer.js` |
| `saveIndicator` | `POST` | `/customer/${id}/indicator` | `aigicrm-ui/src/api/customer.js` |
| `saveRelationship` | `PUT` | `/customer/${id}/relationship` | `aigicrm-ui/src/api/customer.js` |
| `getShares` | `GET` | `/customer/${id}/shares` | `aigicrm-ui/src/api/customer.js` |
| `addShares` | `POST` | `/customer/${id}/shares` | `aigicrm-ui/src/api/customer.js` |
| `removeShare` | `DELETE` | `/customer/${customerId}/shares/${userId}` | `aigicrm-ui/src/api/customer.js` |
| `getContacts` | `GET` | `/customer/${id}/contacts` | `aigicrm-ui/src/api/customer.js` |
| `addContact` | `POST` | `/customer/${id}/contacts` | `aigicrm-ui/src/api/customer.js` |
| `updateContact` | `PUT` | `/customer/${customerId}/contacts/${contactId}` | `aigicrm-ui/src/api/customer.js` |
| `deleteContact` | `DELETE` | `/customer/${customerId}/contacts/${contactId}` | `aigicrm-ui/src/api/customer.js` |
| `getPayments` | `GET` | `/customer/${id}/payments` | `aigicrm-ui/src/api/customer.js` |
| `getPaymentSummary` | `GET` | `/customer/${id}/payments/summary` | `aigicrm-ui/src/api/customer.js` |
| `addPayment` | `POST` | `/customer/${id}/payments` | `aigicrm-ui/src/api/customer.js` |
| `deletePayment` | `DELETE` | `/customer/${customerId}/payments/${paymentId}` | `aigicrm-ui/src/api/customer.js` |
| `importCustomers` | `POST` | `/customer/import` | `aigicrm-ui/src/api/customer.js` |
| `getFolderTree` | `GET` | `/file/folders` | `aigicrm-ui/src/api/file.js` |
| `getFileList` | `GET` | `/file/list` | `aigicrm-ui/src/api/file.js` |
| `createFolder` | `POST` | `/file/folders` | `aigicrm-ui/src/api/file.js` |
| `renameFolder` | `PUT` | `/file/folders/${id}` | `aigicrm-ui/src/api/file.js` |
| `deleteFolder` | `DELETE` | `/file/folders/${id}` | `aigicrm-ui/src/api/file.js` |
| `deleteFile` | `DELETE` | `/file/${id}` | `aigicrm-ui/src/api/file.js` |
| `getStorageInfo` | `GET` | `/file/storage` | `aigicrm-ui/src/api/file.js` |
| `shareFile` | `POST` | `/file/${fileId}/share` | `aigicrm-ui/src/api/file.js` |
| `getSharedToMe` | `GET` | `/file/shared-to-me` | `aigicrm-ui/src/api/file.js` |
| `getSharedToMeCount` | `GET` | `/file/shared-to-me/count` | `aigicrm-ui/src/api/file.js` |
| `getFolderAcl` | `GET` | `/file/folders/${id}/acl` | `aigicrm-ui/src/api/file.js` |
| `saveFolderAcl` | `POST` | `/file/folders/${id}/acl` | `aigicrm-ui/src/api/file.js` |
| `removeFolderAcl` | `DELETE` | `/file/folders/${id}/acl/${roleId}` | `aigicrm-ui/src/api/file.js` |
| `setFolderDownloadable` | `PUT` | `/file/folders/${id}/download` | `aigicrm-ui/src/api/file.js` |
| `getFolderPermissions` | `GET` | `/file/folders/${id}/permissions` | `aigicrm-ui/src/api/file.js` |
| `replaceFile` | `POST` | `/file/${id}/replace` | `aigicrm-ui/src/api/file.js` |
| `getCategoryTree` | `GET` | `/incentive/categories` | `aigicrm-ui/src/api/incentive.js` |
| `createCategory` | `POST` | `/incentive/categories` | `aigicrm-ui/src/api/incentive.js` |
| `updateCategory` | `PUT` | `/incentive/categories/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `deleteCategory` | `DELETE` | `/incentive/categories/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `getRecords` | `GET` | `/incentive/records` | `aigicrm-ui/src/api/incentive.js` |
| `addRecord` | `POST` | `/incentive/records` | `aigicrm-ui/src/api/incentive.js` |
| `deleteRecord` | `DELETE` | `/incentive/records/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `updateRecord` | `PUT` | `/incentive/records/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `getScoreboard` | `GET` | `/incentive/scoreboard` | `aigicrm-ui/src/api/incentive.js` |
| `getCategoryStats` | `GET` | `/incentive/category-stats` | `aigicrm-ui/src/api/incentive.js` |
| `getScoreByCustomer` | `GET` | `/incentive/score/customer/${customerId}` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveRanking` | `GET` | `/incentive/ranking` | `aigicrm-ui/src/api/incentive.js` |
| `getCustomerRecords` | `GET` | `/incentive/customer-records` | `aigicrm-ui/src/api/incentive.js` |
| `markIncentiveRead` | `POST` | `/incentive/records/${recordId}/read` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveReadStats` | `GET` | `/incentive/read-stats` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveReadRanking` | `GET` | `/incentive/read-ranking` | `aigicrm-ui/src/api/incentive.js` |
| `checkIncentivePermission` | `GET` | `/incentive/permission/check` | `aigicrm-ui/src/api/incentive.js` |
| `getReminderList` | `GET` | `/reminder/list` | `aigicrm-ui/src/api/reminder.js` |
| `getExpiringList` | `GET` | `/reminder/expiring-list` | `aigicrm-ui/src/api/reminder.js` |
| `ensureExpiring` | `POST` | `/reminder/ensure-expiring` | `aigicrm-ui/src/api/reminder.js` |
| `getReminderDetail` | `GET` | `/reminder/${id}` | `aigicrm-ui/src/api/reminder.js` |
| `processReminder` | `POST` | `/reminder/${id}/process` | `aigicrm-ui/src/api/reminder.js` |
| `getPendingCount` | `GET` | `/reminder/pending-count` | `aigicrm-ui/src/api/reminder.js` |
| `addScoreItem` | `POST` | `/score/items` | `aigicrm-ui/src/api/score.js` |
| `updateScoreItem` | `PUT` | `/score/items/${id}` | `aigicrm-ui/src/api/score.js` |
| `deleteScoreItem` | `DELETE` | `/score/items/${id}` | `aigicrm-ui/src/api/score.js` |
| `getScoreGrid` | `GET` | `/score/grid` | `aigicrm-ui/src/api/score.js` |
| `batchSaveEntries` | `POST` | `/score/entries/batch` | `aigicrm-ui/src/api/score.js` |
| `getScoreRanking` | `GET` | `/score/ranking` | `aigicrm-ui/src/api/score.js` |
| `getScoreRankingStats` | `GET` | `/score/ranking/stats` | `aigicrm-ui/src/api/score.js` |
| `getSeaList` | `GET` | `/sea/list` | `aigicrm-ui/src/api/sea.js` |
| `getSeaStatistics` | `GET` | `/sea/statistics` | `aigicrm-ui/src/api/sea.js` |
| `deleteSeaPool` | `DELETE` | `/sea/pool/${id}` | `aigicrm-ui/src/api/sea.js` |
| `importSeaCustomers` | `POST` | `/sea/import` | `aigicrm-ui/src/api/sea.js` |
| `getSeaConfigs` | `GET` | `/sea/configs` | `aigicrm-ui/src/api/sea.js` |
| `getVisibleSeaConfigs` | `GET` | `/sea/configs/visible` | `aigicrm-ui/src/api/sea.js` |
| `getOrgTree` | `GET` | `/sea/org-tree` | `aigicrm-ui/src/api/sea.js` |
| `createSeaConfig` | `POST` | `/sea/configs` | `aigicrm-ui/src/api/sea.js` |
| `updateSeaConfig` | `PUT` | `/sea/configs/${id}` | `aigicrm-ui/src/api/sea.js` |
| `deleteSeaConfig` | `DELETE` | `/sea/configs/${id}` | `aigicrm-ui/src/api/sea.js` |
| `getClaimRecords` | `GET` | `/sea/claim-records` | `aigicrm-ui/src/api/sea.js` |
| `getDashboardData` | `GET` | `/statistics/dashboard` | `aigicrm-ui/src/api/statistics.js` |
| `getTeamActivities` | `GET` | `/statistics/team-activities` | `aigicrm-ui/src/api/statistics.js` |
| `getWeeklyStats` | `GET` | `/statistics/weekly-stats` | `aigicrm-ui/src/api/statistics.js` |
| `getDailyStats` | `GET` | `/statistics/daily-stats` | `aigicrm-ui/src/api/statistics.js` |
| `getAnalysis` | `GET` | `/statistics/analysis` | `aigicrm-ui/src/api/statistics.js` |
| `getIndicatorStats` | `GET` | `/statistics/indicator-stats` | `aigicrm-ui/src/api/statistics.js` |
| `getCustomerRankings` | `GET` | `/statistics/customer-rankings` | `aigicrm-ui/src/api/statistics.js` |
| `getUserList` | `GET` | `/system/users` | `aigicrm-ui/src/api/system.js` |
| `getUserById` | `GET` | `/system/users/${id}` | `aigicrm-ui/src/api/system.js` |
| `createUser` | `POST` | `/system/users` | `aigicrm-ui/src/api/system.js` |
| `updateUser` | `PUT` | `/system/users/${id}` | `aigicrm-ui/src/api/system.js` |
| `resetPassword` | `POST` | `/system/users/${id}/reset-password` | `aigicrm-ui/src/api/system.js` |
| `changePassword` | `POST` | `/system/users/change-password` | `aigicrm-ui/src/api/system.js` |
| `deleteUser` | `DELETE` | `/system/users/${id}` | `aigicrm-ui/src/api/system.js` |
| `getRoleList` | `GET` | `/system/roles` | `aigicrm-ui/src/api/system.js` |
| `getRole` | `GET` | `/system/roles/${id}` | `aigicrm-ui/src/api/system.js` |
| `createRole` | `POST` | `/system/roles` | `aigicrm-ui/src/api/system.js` |
| `updateRole` | `PUT` | `/system/roles/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteRole` | `DELETE` | `/system/roles/${id}` | `aigicrm-ui/src/api/system.js` |
| `getDeptTree` | `GET` | `/system/depts/tree` | `aigicrm-ui/src/api/system.js` |
| `getDeptList` | `GET` | `/system/depts` | `aigicrm-ui/src/api/system.js` |
| `getScopeDepts` | `GET` | `/common/scope/depts` | `aigicrm-ui/src/api/system.js` |
| `createDept` | `POST` | `/system/depts` | `aigicrm-ui/src/api/system.js` |
| `updateDept` | `PUT` | `/system/depts/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteDept` | `DELETE` | `/system/depts/${id}` | `aigicrm-ui/src/api/system.js` |
| `getMenuTree` | `GET` | `/system/menus/tree` | `aigicrm-ui/src/api/system.js` |
| `getLogList` | `GET` | `/system/logs` | `aigicrm-ui/src/api/system.js` |
| `getConfigList` | `GET` | `/system/configs` | `aigicrm-ui/src/api/system.js` |
| `getPublicConfig` | `GET` | `/system/configs/public` | `aigicrm-ui/src/api/system.js` |
| `updateConfigs` | `PUT` | `/system/configs` | `aigicrm-ui/src/api/system.js` |
| `getDictTypes` | `GET` | `/system/dict/types` | `aigicrm-ui/src/api/system.js` |
| `createDictType` | `POST` | `/system/dict/types` | `aigicrm-ui/src/api/system.js` |
| `updateDictType` | `PUT` | `/system/dict/types/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteDictType` | `DELETE` | `/system/dict/types/${id}` | `aigicrm-ui/src/api/system.js` |
| `getDictData` | `GET` | `/system/dict/data/${dictCode}` | `aigicrm-ui/src/api/system.js` |
| `getDictDataByTypeId` | `GET` | `/system/dict/data/type/${typeId}` | `aigicrm-ui/src/api/system.js` |
| `createDictData` | `POST` | `/system/dict/data` | `aigicrm-ui/src/api/system.js` |
| `updateDictData` | `PUT` | `/system/dict/data/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteDictData` | `DELETE` | `/system/dict/data/${id}` | `aigicrm-ui/src/api/system.js` |
| `getDeptAdmins` | `GET` | `/system/depts/${deptId}/admins` | `aigicrm-ui/src/api/system.js` |
| `addDeptAdmin` | `POST` | `/system/depts/${deptId}/admins` | `aigicrm-ui/src/api/system.js` |
| `removeDeptAdmin` | `DELETE` | `/system/depts/${deptId}/admins/${adminId}` | `aigicrm-ui/src/api/system.js` |
| `getPositionList` | `GET` | `/system/positions` | `aigicrm-ui/src/api/system.js` |
| `createPosition` | `POST` | `/system/positions` | `aigicrm-ui/src/api/system.js` |
| `updatePosition` | `PUT` | `/system/positions/${id}` | `aigicrm-ui/src/api/system.js` |
| `deletePosition` | `DELETE` | `/system/positions/${id}` | `aigicrm-ui/src/api/system.js` |
| `getUserPositions` | `GET` | `/system/positions/users/${userId}` | `aigicrm-ui/src/api/system.js` |
| `assignUserPositions` | `PUT` | `/system/positions/users/${userId}` | `aigicrm-ui/src/api/system.js` |
| `getRuleList` | `GET` | `/system/rules` | `aigicrm-ui/src/api/system.js` |
| `createRule` | `POST` | `/system/rules` | `aigicrm-ui/src/api/system.js` |
| `updateRule` | `PUT` | `/system/rules/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteRule` | `DELETE` | `/system/rules/${id}` | `aigicrm-ui/src/api/system.js` |
| `getRoleOrgs` | `GET` | `/system/roles/${roleId}/orgs` | `aigicrm-ui/src/api/system.js` |
| `updateRoleOrgs` | `PUT` | `/system/roles/${roleId}/orgs` | `aigicrm-ui/src/api/system.js` |
| `getPositionRules` | `GET` | `/system/positions/${positionId}/rules` | `aigicrm-ui/src/api/system.js` |
| `updatePositionRules` | `PUT` | `/system/positions/${positionId}/rules` | `aigicrm-ui/src/api/system.js` |
| `getGroupList` | `GET` | `/system/groups` | `aigicrm-ui/src/api/system.js` |
| `createGroup` | `POST` | `/system/groups` | `aigicrm-ui/src/api/system.js` |
| `updateGroup` | `PUT` | `/system/groups/${id}` | `aigicrm-ui/src/api/system.js` |
| `deleteGroup` | `DELETE` | `/system/groups/${id}` | `aigicrm-ui/src/api/system.js` |
| `getRolePermScopes` | `GET` | `/system/perm-scopes/${roleCode}` | `aigicrm-ui/src/api/system.js` |
| `updateRolePermScopes` | `PUT` | `/system/perm-scopes/${roleCode}` | `aigicrm-ui/src/api/system.js` |
| `getAssigneePermScopes` | `GET` | `/system/perm-scopes/assignee/${type}/${key}` | `aigicrm-ui/src/api/system.js` |
| `updateAssigneePermScopes` | `PUT` | `/system/perm-scopes/assignee/${type}/${key}` | `aigicrm-ui/src/api/system.js` |

## Mini Program API Functions

| Function | Method | Path | Source |
| --- | --- | --- | --- |
| `getApprovalList` | `GET` | `/api/approval/list` | `aigicrm-miniprogram/api/approval.js` |
| `approveApproval` | `POST` | `/api/approval/' + id + '/approve` | `aigicrm-miniprogram/api/approval.js` |
| `rejectApproval` | `POST` | `/api/approval/' + id + '/reject` | `aigicrm-miniprogram/api/approval.js` |
| `getApprovalStatistics` | `GET` | `/api/approval/statistics` | `aigicrm-miniprogram/api/approval.js` |
| `getApprovalDetail` | `GET` | `/api/approval/' + id` | `aigicrm-miniprogram/api/approval.js` |
| `submitApproval` | `POST` | `/api/approval/submit` | `aigicrm-miniprogram/api/approval.js` |
| `getGlobalData` | `POST` | `/api/auth/login` | `aigicrm-miniprogram/api/auth.js` |
| `loadCurrentUser` | `GET` | `/api/auth/info` | `aigicrm-miniprogram/api/auth.js` |
| `switchGroup` | `POST` | `/api/auth/switch-group` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordLookup` | `POST` | `/api/auth/forgot-password/lookup` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordSendCode` | `POST` | `/api/auth/forgot-password/send-code` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordReset` | `POST` | `/api/auth/forgot-password/reset` | `aigicrm-miniprogram/api/auth.js` |
| `getCustomerList` | `GET` | `/api/customer/list` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomer` | `GET` | `/api/customer/' + id` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerCounts` | `GET` | `/api/customer/list/counts` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomer` | `POST` | `/api/customer/add` | `aigicrm-miniprogram/api/customer.js` |
| `updateCustomer` | `PUT` | `/api/customer/' + id` | `aigicrm-miniprogram/api/customer.js` |
| `checkCustomerName` | `GET` | `/api/customer/check-name` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerContacts` | `GET` | `/api/customer/' + id + '/contacts` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomerContact` | `POST` | `/api/customer/' + id + '/contacts` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerLogs` | `GET` | `/api/customer/' + id + '/logs` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomerLog` | `POST` | `/api/customer/' + id + '/logs` | `aigicrm-miniprogram/api/customer.js` |
| `getInvoiceSummary` | `GET` | `/api/customer/' + id + '/invoices/summary` | `aigicrm-miniprogram/api/customer.js` |
| `getPaymentSummary` | `GET` | `/api/customer/' + id + '/payments/summary` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerInvoices` | `GET` | `/api/customer/' + id + '/invoices` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerPayments` | `GET` | `/api/customer/' + id + '/payments` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerShares` | `GET` | `/api/customer/' + id + '/shares` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerSimpleUsers` | `GET` | `/api/customer/users/simple` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerGroupNames` | `GET` | `/api/customer/group-names` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomerShares` | `POST` | `/api/customer/' + id + '/shares` | `aigicrm-miniprogram/api/customer.js` |
| `removeCustomerShare` | `DELETE` | `/api/customer/' + id + '/shares/' + userId` | `aigicrm-miniprogram/api/customer.js` |
| `addInvoice` | `POST` | `/api/customer/' + id + '/invoices` | `aigicrm-miniprogram/api/customer.js` |
| `addPayment` | `POST` | `/api/customer/' + id + '/payments` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerIndicator` | `GET` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerIndicator` | `POST` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerRelationship` | `PUT` | `/api/customer/' + id + '/relationship` | `aigicrm-miniprogram/api/customer.js` |
| `releaseCustomer` | `POST` | `/api/customer/' + id + '/release` | `aigicrm-miniprogram/api/customer.js` |
| `getGlobalData` | `GET` | `/api/file/list` | `aigicrm-miniprogram/api/file.js` |
| `getStorageInfo` | `GET` | `/api/file/storage` | `aigicrm-miniprogram/api/file.js` |
| `getDownloadToken` | `GET` | `/api/file/download-token` | `aigicrm-miniprogram/api/file.js` |
| `getFolders` | `GET` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `createFolder` | `POST` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `renameFolder` | `PUT` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `deleteFolder` | `DELETE` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `getFolderAcl` | `GET` | `/api/file/folders/' + id + '/acl` | `aigicrm-miniprogram/api/file.js` |
| `getSharedToMe` | `GET` | `/api/file/shared-to-me` | `aigicrm-miniprogram/api/file.js` |
| `shareFile` | `POST` | `/api/file/' + id + '/share` | `aigicrm-miniprogram/api/file.js` |
| `getCustomerRecords` | `GET` | `/api/incentive/customer-records` | `aigicrm-miniprogram/api/incentive.js` |
| `getRecords` | `GET` | `/api/incentive/records` | `aigicrm-miniprogram/api/incentive.js` |
| `getCategoryTree` | `GET` | `/api/incentive/categories` | `aigicrm-miniprogram/api/incentive.js` |
| `addRecord` | `POST` | `/api/incentive/records` | `aigicrm-miniprogram/api/incentive.js` |
| `createRecord` | `POST` | `/api/incentive/records` | `aigicrm-miniprogram/api/incentive.js` |
| `markIncentiveRead` | `POST` | `/api/incentive/records/' + recordId + '/read` | `aigicrm-miniprogram/api/incentive.js` |
| `getReadStats` | `GET` | `/api/incentive/read-stats` | `aigicrm-miniprogram/api/incentive.js` |
| `getCategoryStats` | `GET` | `/api/incentive/category-stats` | `aigicrm-miniprogram/api/incentive.js` |
| `getReadRanking` | `GET` | `/api/incentive/read-ranking` | `aigicrm-miniprogram/api/incentive.js` |
| `getScoreboard` | `GET` | `/api/incentive/scoreboard` | `aigicrm-miniprogram/api/incentive.js` |
| `getScoreByCustomer` | `GET` | `/api/incentive/score/customer/' + customerId` | `aigicrm-miniprogram/api/incentive.js` |
| `getRanking` | `GET` | `/api/incentive/ranking` | `aigicrm-miniprogram/api/incentive.js` |
| `getUsers` | `GET` | `/api/incentive/users` | `aigicrm-miniprogram/api/incentive.js` |
| `updateRecord` | `PUT` | `/api/incentive/records/' + id` | `aigicrm-miniprogram/api/incentive.js` |
| `getReminderList` | `GET` | `/api/reminder/list` | `aigicrm-miniprogram/api/reminder.js` |
| `getExpiringList` | `GET` | `/api/reminder/expiring-list` | `aigicrm-miniprogram/api/reminder.js` |
| `ensureExpiring` | `POST` | `/api/reminder/ensure-expiring` | `aigicrm-miniprogram/api/reminder.js` |
| `processReminder` | `POST` | `/api/reminder/' + id + '/process` | `aigicrm-miniprogram/api/reminder.js` |
| `getPendingCount` | `GET` | `/api/reminder/pending-count` | `aigicrm-miniprogram/api/reminder.js` |
| `getRanking` | `GET` | `/api/score/ranking` | `aigicrm-miniprogram/api/score.js` |
| `getRankingStats` | `GET` | `/api/score/ranking/stats` | `aigicrm-miniprogram/api/score.js` |
| `getSeaList` | `GET` | `/api/sea/list` | `aigicrm-miniprogram/api/sea.js` |
| `getSeaStatistics` | `GET` | `/api/sea/statistics` | `aigicrm-miniprogram/api/sea.js` |
| `getVisibleSeaConfigs` | `GET` | `/api/sea/configs/visible` | `aigicrm-miniprogram/api/sea.js` |
| `getClaimRecords` | `GET` | `/api/sea/claim-records` | `aigicrm-miniprogram/api/sea.js` |
| `getIndicatorStats` | `GET` | `/api/statistics/indicator-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getDashboardData` | `GET` | `/api/statistics/dashboard` | `aigicrm-miniprogram/api/statistics.js` |
| `getAnalysis` | `GET` | `/api/statistics/analysis` | `aigicrm-miniprogram/api/statistics.js` |
| `getTeamActivities` | `GET` | `/api/statistics/team-activities` | `aigicrm-miniprogram/api/statistics.js` |
| `getDailyStats` | `GET` | `/api/statistics/daily-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getWeeklyStats` | `GET` | `/api/statistics/weekly-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getCustomerRankings` | `GET` | `/api/statistics/customer-rankings` | `aigicrm-miniprogram/api/statistics.js` |
| `getDepts` | `GET` | `/api/system/depts` | `aigicrm-miniprogram/api/system.js` |
| `getDictData` | `GET` | `/api/system/dict/data/' + dictCode` | `aigicrm-miniprogram/api/system.js` |

## Backend Endpoints

| Method | Path | Permission Expression | Source |
| --- | --- | --- | --- |
| `GET` | `/api/approval/config/list` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalConfigController.java` |
| `POST` | `/api/approval/config` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalConfigController.java` |
| `PUT` | `/api/approval/config/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalConfigController.java` |
| `DELETE` | `/api/approval/config/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalConfigController.java` |
| `GET` | `/api/approval/list` | `hasAuthority('approval:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `GET` | `/api/approval/{id}` | `hasAuthority('approval:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `POST` | `/api/approval/submit` | `hasAuthority('approval:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `POST` | `/api/approval/{id}/approve` | `hasAuthority('approval:approve')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `POST` | `/api/approval/{id}/reject` | `hasAuthority('approval:reject')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `GET` | `/api/approval/statistics` | `hasAuthority('approval:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
| `POST` | `/api/auth/login` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `GET` | `/api/auth/info` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `POST` | `/api/auth/logout` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `POST` | `/api/auth/switch-group` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `POST` | `/api/auth/forgot-password/lookup` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `POST` | `/api/auth/forgot-password/send-code` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `POST` | `/api/auth/forgot-password/reset` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java` |
| `GET` | `/api/common/scope/users` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/ScopeController.java` |
| `GET` | `/api/common/scope/depts` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/ScopeController.java` |
| `GET` | `/api/customer/list` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/list/counts` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/group-names` | `hasAuthority('customer:add') or hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/rankings` | `hasAuthority('score:view') or hasAuthority('ranking:customer:view') or hasAuthority('statistics:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/add` | `hasAuthority('customer:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}` | `hasAuthority('customer:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/logs` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/logs` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/statistics` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices/summary` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/invoices` | `hasAuthority('customer:edit') and hasAuthority('finance:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/invoices/{invoiceId}` | `hasAuthority('customer:edit') and hasAuthority('finance:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/indicator` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/indicator` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}/relationship` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/shares` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/shares` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/shares/{userId}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/release` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/users/simple` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/contacts` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/contacts` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}/contacts/{contactId}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/contacts/{contactId}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/payments` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/payments/summary` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/payments` | `hasAuthority('customer:edit') and hasAuthority('finance:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/payments/{paymentId}` | `hasAuthority('customer:edit') and hasAuthority('finance:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/import` | `hasAuthority('customer:import')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/import/template` | `hasAuthority('customer:import')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/export` | `hasAuthority('customer:export')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/check-name` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/file/download-token` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/folders` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/list` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `POST` | `/api/file/upload` | `hasAuthority('file:upload')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `POST` | `/api/file/folders` | `hasAuthority('file:folder:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `PUT` | `/api/file/folders/{id}` | `hasAuthority('file:folder:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `DELETE` | `/api/file/folders/{id}` | `hasAuthority('file:folder:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `DELETE` | `/api/file/{id}` | `hasAuthority('file:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `POST` | `/api/file/{id}/replace` | `hasAuthority('file:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/storage` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/{id}` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/public/**` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/**` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `POST` | `/api/file/{id}/share` | `hasAuthority('file:share')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/shared-to-me` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/shared-to-me/count` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/folders/{id}/acl` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `POST` | `/api/file/folders/{id}/acl` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `DELETE` | `/api/file/folders/{id}/acl/{roleId}` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `PUT` | `/api/file/folders/{id}/download` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/file/folders/{id}/permissions` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/incentive/categories` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/categories` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `PUT` | `/api/incentive/categories/{id}` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `DELETE` | `/api/incentive/categories/{id}` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/records` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records` | `hasAuthority('incentive:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `PUT` | `/api/incentive/records/{id}` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records/recalculate` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `DELETE` | `/api/incentive/records/{id}` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/score/customer/{customerId}` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/scoreboard` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/ranking` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/customer-records` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records/{id}/read` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/read-stats` | `hasAuthority('incentive:read-stats')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/read-ranking` | `hasAuthority('incentive:read-ranking:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/category-stats` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/users` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/permission/list` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `POST` | `/api/incentive/permission/grant` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `DELETE` | `/api/incentive/permission/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `GET` | `/api/incentive/permission/check` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `GET` | `/api/reminder/expiring-list` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `GET` | `/api/reminder/list` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `GET` | `/api/reminder/{id}` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `GET` | `/api/reminder/pending-count` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `POST` | `/api/reminder/ensure-expiring` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `POST` | `/api/reminder/{id}/process` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `POST` | `/api/score/items` | `hasAuthority('score:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `PUT` | `/api/score/items/{id}` | `hasAuthority('score:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `DELETE` | `/api/score/items/{id}` | `hasAuthority('score:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/score/grid` | `hasAuthority('score:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `POST` | `/api/score/entries/batch` | `hasAuthority('score:entry')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/score/ranking` | `hasAuthority('score:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/score/ranking/stats` | `hasAuthority('score:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/score/export/grid` | `hasAuthority('score:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/sea/list` | `hasAuthority('sea:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/sea/statistics` | `hasAuthority('sea:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `DELETE` | `/api/sea/pool/{id}` | `hasAuthority('sea:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `POST` | `/api/sea/import` | `hasAuthority('sea:import')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/sea/configs` | `hasAuthority('sea:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/sea/configs/visible` | `hasAuthority('sea:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/sea/org-tree` | `hasAuthority('sea:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `POST` | `/api/sea/configs` | `hasAuthority('sea:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `PUT` | `/api/sea/configs/{id}` | `hasAuthority('sea:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `DELETE` | `/api/sea/configs/{id}` | `hasAuthority('sea:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/sea/claim-records` | `hasAuthority('sea:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
| `GET` | `/api/statistics/dashboard` | `hasAnyAuthority('statistics:view','dashboard:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/daily-stats` | `hasAnyAuthority('statistics:view','dashboard:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/weekly-stats` | `hasAnyAuthority('statistics:view','dashboard:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/indicator-stats` | `hasAuthority('statistics:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/customer-rankings` | `hasAuthority('statistics:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/analysis` | `hasAuthority('statistics:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/statistics/team-activities` | `hasAnyAuthority('statistics:view','dashboard:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java` |
| `GET` | `/api/system/perm-scopes/{roleCode}` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `PUT` | `/api/system/perm-scopes/{roleCode}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `GET` | `/api/system/perm-scopes/assignee/{type}/{key}` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `PUT` | `/api/system/perm-scopes/assignee/{type}/{key}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `GET` | `/api/system/configs` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysConfigController.java` |
| `PUT` | `/api/system/configs` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysConfigController.java` |
| `GET` | `/api/system/configs/public` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysConfigController.java` |
| `GET` | `/api/system/configs/logo` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysConfigController.java` |
| `GET` | `/api/system/depts/tree` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `GET` | `/api/system/depts` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `POST` | `/api/system/depts` | `hasAuthority('system:dept:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `PUT` | `/api/system/depts/{id}` | `hasAuthority('system:dept:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `DELETE` | `/api/system/depts/{id}` | `hasAuthority('system:dept:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `GET` | `/api/system/depts/{deptId}/admins` | `hasAuthority('system:dept:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `POST` | `/api/system/depts/{deptId}/admins` | `hasAuthority('system:dept:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `DELETE` | `/api/system/depts/{deptId}/admins/{id}` | `hasAuthority('system:dept:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDeptController.java` |
| `GET` | `/api/system/dict/types` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `POST` | `/api/system/dict/types` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `PUT` | `/api/system/dict/types/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `DELETE` | `/api/system/dict/types/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `GET` | `/api/system/dict/data/{dictCode}` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `GET` | `/api/system/dict/data/type/{typeId}` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `POST` | `/api/system/dict/data` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `PUT` | `/api/system/dict/data/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `DELETE` | `/api/system/dict/data/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java` |
| `GET` | `/api/system/groups` | `hasAuthority('system:group:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysGroupController.java` |
| `POST` | `/api/system/groups` | `hasAuthority('system:group:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysGroupController.java` |
| `PUT` | `/api/system/groups/{id}` | `hasAuthority('system:group:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysGroupController.java` |
| `DELETE` | `/api/system/groups/{id}` | `hasAuthority('system:group:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysGroupController.java` |
| `GET` | `/api/system/logs` | `hasAuthority('system:log:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysLogController.java` |
| `GET` | `/api/system/logs/counts` | `hasAuthority('system:log:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysLogController.java` |
| `GET` | `/api/system/menus/tree` | `hasAuthority('system:menu:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysMenuController.java` |
| `GET` | `/api/system/rules` | `hasAuthority('system:rule:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPermissionRuleController.java` |
| `POST` | `/api/system/rules` | `hasAuthority('system:rule:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPermissionRuleController.java` |
| `PUT` | `/api/system/rules/{id}` | `hasAuthority('system:rule:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPermissionRuleController.java` |
| `DELETE` | `/api/system/rules/{id}` | `hasAuthority('system:rule:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPermissionRuleController.java` |
| `GET` | `/api/system/positions` | `hasAuthority('system:position:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `POST` | `/api/system/positions` | `hasAuthority('system:position:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `PUT` | `/api/system/positions/{id}` | `hasAuthority('system:position:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `DELETE` | `/api/system/positions/{id}` | `hasAuthority('system:position:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `GET` | `/api/system/positions/{positionId}/rules` | `hasAuthority('system:position:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `PUT` | `/api/system/positions/{positionId}/rules` | `hasAuthority('system:position:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `GET` | `/api/system/positions/users/{userId}` | `hasAnyAuthority('system:position:view','system:user:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `PUT` | `/api/system/positions/users/{userId}` | `hasAuthority('system:user:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysPositionController.java` |
| `GET` | `/api/system/roles` | `hasAuthority('system:role:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `GET` | `/api/system/roles/{id}` | `hasAuthority('system:role:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `GET` | `/api/system/roles/{id}/orgs` | `hasAuthority('system:role:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `PUT` | `/api/system/roles/{id}/orgs` | `hasAuthority('system:role:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `POST` | `/api/system/roles` | `hasAuthority('system:role:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `PUT` | `/api/system/roles/{id}` | `hasAuthority('system:role:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `DELETE` | `/api/system/roles/{id}` | `hasAuthority('system:role:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRoleController.java` |
| `GET` | `/api/system/users` | `hasAnyAuthority('system:user:view','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `GET` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:view','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users` | `hasAnyAuthority('system:user:add','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `PUT` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:edit','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users/{id}/reset-password` | `hasAnyAuthority('system:user:edit','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users/change-password` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `DELETE` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:delete','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/task/check-expire` | `` | `aigicrm-backend/src/main/java/com/aigicrm/task/TaskController.java` |

## Local Migration Permission Sources

| Permission | Source |
| --- | --- |
| `approval:view` | approval/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `contact:view` | 客户联系人查看 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `customer:add` | customer/add (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `customer:date:edit` | 客户日期编辑 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `customer:view` | customer/list (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `dashboard:view` | dashboard/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `file:acl:manage` | 文件ACL管理 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `file:delete` | 文件删除 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:edit` | 文件编辑 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:folder:manage` | 文件夹管理 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:share` | 文件分享 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:upload` | 文件上传 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:view` | 文件ACL管理 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `finance:view` | 客户财务查看 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `incentive:add` | 激励新增 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `incentive:read-ranking:view` | 本月激励查询阅读排行榜 (V2026.09.13.4__add_incentive_read_ranking_menu.sql) |
| `incentive:read-stats` | 激励查询已读统计 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `incentive:view` | 激励新增 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `ranking:customer:view` | 客户排行查看 (V2026.09.11.4__register_orphan_customer_ranking_permission.sql) |
| `reminder:view` | reminder/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `score:entry` | score/entry (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `score:ranking` | score/ranking (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `sea:import` | 公海导入 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `sea:view` | 公海导入 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `statistics:scoreboard` | statistics/scoreboard (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `statistics:view` | statistics/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:config:view` | system/config (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:dept:view` | system/org (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:group:view` | system/group/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:log:view` | system/log (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:position:view` | system/position/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:role:view` | system/role (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:rule:view` | system/rule/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:user:view` | system/user (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |

## Permission Drift Candidates

### Backend Permissions Missing In Dictionary Or Local Migrations

- None

### Frontend Code Permission Tokens Missing In Known Sources

- None

### Runtime Dictionary Permissions Not Directly Seen In Backend Annotations

- `contact:view`: 客户联系人查看
- `sea:claim`: 公海捞取

### Runtime Dictionary Permissions Referenced Outside Backend Annotations

- `contact:view`: 客户联系人查看
- `sea:claim`: 公海捞取

### Runtime Dictionary Permissions Not Seen In Backend Annotations Or Code References

- None

### Compatibility Or Deprecated Dictionary Permissions Excluded From Runtime Drift

- `customer:share`: 客户分享（兼容历史项，当前沿用客户访问/编辑守卫）
- `incentive:delete`: 激励删除（兼容历史项，当前由授权表与服务范围控制）
- `incentive:manage`: ~~激励管理(他人记录)~~ 已废弃
- `incentive:view-all`: ~~激励查看(全部)~~ 已废弃
- `incentive:view-shared`: ~~激励查看(已分享)~~ 已废弃
- `score:ranking`: 积分排行（兼容历史项，当前统一使用 `score:view`）
- `sea:pool:delete`: 公海删除（兼容历史项，当前由池级服务范围控制）
- `statistics:scoreboard`: 积分榜（兼容历史项，当前统一使用 `score:view`）
- `system:menu:edit`: 历史残留（菜单维护端点已移除，当前不可用）

### Backend Code References For Dictionary-Only Permissions

| Permission | Code Reference | Context |
| --- | --- | --- |
| `contact:view` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java:260` | `if (!SecurityUtils.hasAuthority("contact:view")) return Result.ok(List.of());` |
| `sea:claim` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java:191` | `&& !SecurityUtils.hasAuthority("sea:claim")) {` |

### UI Permission Tokens Missing In Known Sources

- None

## API Drift Candidates

### PC API Wrappers Without Backend Endpoint Match

- None

### Mini Program API Wrappers Without Backend Endpoint Match

- None

## Notes

- A dictionary permission can be valid even when it is not directly seen in annotations if enforcement happens in service-layer checks.
- Compatibility/deprecated dictionary rows are kept visible but excluded from runtime drift alerts when the permission dictionary documents them as non-runtime authorization.
- A backend permission missing in both the generated dictionary and local Flyway sys_menu migrations is high-risk because users may not be able to receive that authority through normal role-menu binding.
- This report is local-source based. For production sign-off, regenerate from live database configuration.
