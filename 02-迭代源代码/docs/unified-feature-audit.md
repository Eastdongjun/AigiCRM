# AIGICRM Unified Feature Audit

Generated from local source: `.`

## Summary

- PC routes: 27
- PC API functions: 163
- Mini Program pages: 45
- Mini Program API functions: 64
- Backend endpoints: 186
- PC UI actions: 201
- Mini Program UI actions: 112
- Backend permission tokens: 57
- Permission dictionary tokens: 66
- Local migration permission tokens: 11
- Backend permissions missing in dictionary or local migrations: 0
- Dictionary permissions not directly seen in backend annotations: 10
- UI permission tokens missing in backend, dictionary, and local migrations: 0
- Frontend code permission tokens: 44
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
| `incentive` | 四项激励 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/stats` | 激励统计 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/search` | 激励查询 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `reminder` | 提醒管理 | `reminder:view` | `aigicrm-ui/src/router/index.js` |
| `sea` | 公海池 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `sea/records` | 捞取记录 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `approval` | 审批管理 | `approval:view` | `aigicrm-ui/src/router/index.js` |
| `statistics` | 统计分析 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `score/entry` | 学分录入 | `score:entry` | `aigicrm-ui/src/router/index.js` |
| `score/ranking` | 学分排行 | `score:view` | `aigicrm-ui/src/router/index.js` |
| `indicator` | 统计总览 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `system/user` | 人员管理 | `system:user:view` | `aigicrm-ui/src/router/index.js` |
| `system/org` | 组织架构 | `system:dept:view` | `aigicrm-ui/src/router/index.js` |
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
| {{ row.approvalCode }} | `openDetail(row)` | `` | `aigicrm-ui/src/views/approval/index.vue:146` |
| (icon/button) | `handleApprove(row)` | `canApprove` | `aigicrm-ui/src/views/approval/index.vue:175` |
| (icon/button) | `handleReject(row)` | `canReject` | `aigicrm-ui/src/views/approval/index.vue:178` |
| 关闭 | `detailVisible = false` | `` | `aigicrm-ui/src/views/approval/index.vue:254` |
| 驳回 | `handleReject(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:255` |
| 通过 | `handleApprove(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:261` |
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
| 释放 | `openRelease(row)` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:74` |
| 删除 | `handleDelete(row)` | `canDelete` | `aigicrm-ui/src/views/customer/MainTable.vue:75` |
| 取消 | `showReleaseDialog = false` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:19` |
| 确认释放 | `confirmRelease` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:20` |
| 高级筛选 0" size="small" type="warning" style="margin-left:4px">{{ advFilterCount }} | `showAdvFilter = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:4` |
| 批量导入 | `showImportDialog = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:7` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:8` |
| 新客户 | `$router.push('/customer/add')` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:9` |
| 返回 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:4` |
| 取消 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:23` |
| {{ isEdit ? '更新客户' : '保存客户' }} | `handleSubmit` | `` | `aigicrm-ui/src/views/customer/add.vue:24` |
| 关联文件 | `openAttach` | `canEdit && !readonly` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:3` |
| 刷新 | `loadFiles` | `` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:3` |
| 预览 | `previewFile(file)` | `` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:5` |
| 解除关联 | `detachFile(file)` | `canEdit && !readonly` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:5` |
| 取消 | `attachVisible=false` | `` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:7` |
| 确认关联 | `attachFile` | `` | `aigicrm-ui/src/views/customer/detail/FilesTab.vue:7` |
| + 录入开票 | `showInvoiceDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:10` |
| (icon/button) | `handleDeleteInvoice(row)` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:24` |
| + 录入回款 | `showPaymentDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:37` |
| (icon/button) | `handleDeletePayment(row)` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:48` |
| 取消 | `showInvoiceDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:62` |
| 确认录入 | `handleAddInvoice` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:62` |
| 取消 | `showPaymentDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:72` |
| 确认录入 | `handleAddPayment` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:72` |
| 保存指标 | `handleSaveIndicator` | `` | `aigicrm-ui/src/views/customer/detail/IndicatorTab.vue:17` |
| 申请延期 | `handleExtend` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:46` |
| 添加 | `editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:52` |
| (icon/button) | `openEditContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:62` |
| (icon/button) | `handleDeleteContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:63` |
| 取消 | `showContactDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:88` |
| 确认 | `handleSaveContact` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:88` |
| 记录 | `submitLog` | `` | `aigicrm-ui/src/views/customer/detail/LogsTab.vue:5` |
| 编辑 | `handleEdit` | `canEditCustomer` | `aigicrm-ui/src/views/customer/detail.vue:9` |
| 分享 | `showShareDialog = true` | `canShareCustomer` | `aigicrm-ui/src/views/customer/detail.vue:10` |
| 取消 | `showShareDialog=false` | `` | `aigicrm-ui/src/views/customer/detail.vue:108` |
| 确认分享 | `handleAddShare` | `` | `aigicrm-ui/src/views/customer/detail.vue:108` |
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
| 上传文件 | `startUpload(item)` | `userStore.hasPermission('incentive:add')` | `aigicrm-ui/src/views/incentive/SubCategoryPanel.vue:16` |
| 上传文件 | `startUpload(group)` | `userStore.hasPermission('incentive:add')` | `aigicrm-ui/src/views/incentive/SubCategoryPanel.vue:25` |
| 取消 | `showCustomerDialog=false` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:171` |
| {{ pendingReplace?'追加提交':'上传并提交' }} | `confirmUpload` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:172` |
| 高级筛选 {{ activeFilterCount }} | `showFilter=!showFilter` | `` | `aigicrm-ui/src/views/incentive/search.vue:6` |
| 查询 | `doSearch` | `` | `aigicrm-ui/src/views/incentive/search.vue:13` |
| 重置 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:14` |
| 清空筛选 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:22` |
| 刷新 | `loadReadStats` | `` | `aigicrm-ui/src/views/incentive/search.vue:64` |
| 下载文件 | `downloadPreview` | `` | `aigicrm-ui/src/views/incentive/search.vue:125` |
| (icon/button) | `preview(f.id, f.type, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:178` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:179` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:188` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/search.vue:194` |
| 0 ? 'info' : 'primary'" :disabled="countdown > 0 || !form.method" :loading="sendingCode" @click="handleSendCode" size="large" class="send-btn"> {{ countdown > 0 ? countdown + 's' : '获取验证码' }} | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:61` |
| 1" @click="handleBack">上一步 | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:88` |
| 取消 | `visible = false` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:89` |
| {{ step === 3 ? '确认重置' : '下一步' }} | `handleNext` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:90` |
| 登 录 | `handleLogin` | `` | `aigicrm-ui/src/views/login/index.vue:34` |
| 一键清空 | `handleClearAll` | `` | `aigicrm-ui/src/views/reminder/index.vue:27` |
| 已读 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:64` |
| 已读 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:67` |
| 已跟进 | `handleFollowUp(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:70` |
| 已跟进 | `` | `` | `aigicrm-ui/src/views/reminder/index.vue:71` |
| 申请延期 | `handleExtend(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:72` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/score/entry.vue:20` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/score/entry.vue:21` |
| + | `` | `` | `aigicrm-ui/src/views/score/entry.vue:58` |
| (icon/button) | `handleDeleteRow(item,idx)` | `` | `aigicrm-ui/src/views/score/entry.vue:85` |
| 添加行 | `handleAddRow` | `` | `aigicrm-ui/src/views/score/entry.vue:96` |
| 学分规则 | `showRules = true` | `` | `aigicrm-ui/src/views/score/ranking.vue:11` |
| 捞取记录 | `router.push('/sea/records')` | `` | `aigicrm-ui/src/views/sea/index.vue:15` |
| 批量导入 | `showImportDialog=true` | `canImport` | `aigicrm-ui/src/views/sea/index.vue:16` |
| 重置 | `resetFilters` | `` | `aigicrm-ui/src/views/sea/index.vue:81` |
| 捞取 | `handleClaim(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:125` |
| 编辑 | `handleEdit(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:132` |
| 删除 | `handleDelete(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:133` |
| 下载导入模板 | `handleDownloadTemplate` | `` | `aigicrm-ui/src/views/sea/index.vue:145` |
| {{ importResult?'关闭':'取消' }} | `showImportDialog=false` | `` | `aigicrm-ui/src/views/sea/index.vue:160` |
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
| {{ btn.label }} | `btn.tab ? activeTab=btn.tab : router.push(btn.href)` | `` | `aigicrm-ui/src/views/system/PermMatrixManager.vue:21` |
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
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:1` |
| 驳回 | `reject` | `{{canReject}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:1` |
| 通过 | `approve` | `{{canApprove}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:1` |
| 查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:1` |
| 驳回 | `reject` | `{{canReject && (item.status==='pending' || item.status==='PENDING' || item.status==='WAITING' || item.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:1` |
| 通过 | `approve` | `{{canApprove && (item.status==='pending' || item.status==='PENDING' || item.status==='WAITING' || item.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:1` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:2` |
| 驳回 | `reject` | `{{canReject && (selectedApproval.status==='pending' || selectedApproval.status==='PENDING' || selectedApproval.status==='WAITING' || selectedApproval.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:2` |
| 通过 | `approve` | `{{canApprove && (selectedApproval.status==='pending' || selectedApproval.status==='PENDING' || selectedApproval.status==='WAITING' || selectedApproval.status==='待审批')}}` | `aigicrm-miniprogram/pages/approval/index.wxml:2` |
| 提交审批 | `submitReleaseApproval` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:3` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/cases/detail/index.wxml:1` |
| ＋ | `openAdd` | `` | `aigicrm-miniprogram/pages/cases/index.wxml:8` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:1` |
| 拨打电话 | `call` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:1` |
| 保存 | `submit` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:1` |
| 拨打电话 | `` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:1` |
| 新增联系人 | `openForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:2` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:1` |
| 查看明细 | `openFinance` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:2` |
| 申请保护期延期 | `submitExtendApproval` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:3` |
| 释放到公海 | `releaseToSea` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:5` |
| 查看全部联系人 | `openContacts` | `{{detail && canViewContacts}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:6` |
| 查看客户分享 | `openShares` | `{{detail}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:7` |
| 查看客户文件 | `openFiles` | `{{detail && canViewFiles}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:8` |
| 管理客户文件 | `manageFiles` | `{{detail && canEdit && canViewFiles}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:9` |
| 新增跟进记录 | `openLogForm` | `{{detail && canEdit}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:10` |
| 编辑六项指标 | `openIndicatorForm` | `{{detail && canEdit}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:11` |
| 查看完整跟进记录 | `openTimeline` | `{{detail}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:12` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:1` |
| 重置 | `reset` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:1` |
| 应用筛选 | `apply` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:1` |
| 保存 | `submit` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:1` |
| 新增{{tab==='invoice'?'开票':'回款'}} | `openForm` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:2` |
| 取消 | `cancel` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:1` |
| 保存 | `submit` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:1` |
| 筛选 | `search` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:1` |
| 清空 | `resetFilters` | `{{keyword || filter !== '全部'}}` | `aigicrm-miniprogram/pages/customer/index.wxml:1` |
| 高级筛选 | `openAdvancedFilter` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:2` |
| 保存指标 | `submit` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:1` |
| 取消 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:1` |
| 保存记录 | `submit` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:1` |
| 确认分享 | `addShares` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:2` |
| 撤回 {{item.sharedToUserName || '该用户'}} | `removeShare` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:4` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/timeline/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 全部文件 | `switchMode` | `` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 共享给我 | `switchMode` | `` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 上传 | `chooseAndUpload` | `{{mode !== 'shared'}}` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 查询 | `search` | `` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 打开 | `previewFile` | `` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 分享 | `shareFile` | `{{canShare && mode !== 'shared'}}` | `aigicrm-miniprogram/pages/file/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/files/detail/index.wxml:1` |
| 打开并预览 | `preview` | `` | `aigicrm-miniprogram/pages/files/detail/index.wxml:1` |
| {{id ? '保存名称' : '创建文件夹'}} | `submit` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:1` |
| 取消 | `back` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:1` |
| ＋ | `uploadFile` | `{{canUpload}}` | `aigicrm-miniprogram/pages/files/index.wxml:2` |
| 返回全部 | `clearFolder` | `{{activeFolderName}}` | `aigicrm-miniprogram/pages/files/index.wxml:6` |
| 关联 | `` | `{{mode === 'manage'}}` | `aigicrm-miniprogram/pages/files/index.wxml:9` |
| 解除关联 | `` | `` | `aigicrm-miniprogram/pages/files/index.wxml:9` |
| 新建文件夹 | `openFolderForm` | `{{canManageFolder}}` | `aigicrm-miniprogram/pages/files/index.wxml:13` |
| 重命名 {{item.name}} | `openFolderEdit` | `` | `aigicrm-miniprogram/pages/files/index.wxml:14` |
| 删除 {{item.name}} | `removeFolder` | `` | `aigicrm-miniprogram/pages/files/index.wxml:15` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:2` |
| 更新 | `loadReadStats` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:3` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:8` |
| 上传佐证文件 | `uploadEvidence` | `{{canUploadEvidence}}` | `aigicrm-miniprogram/pages/incentive/index.wxml:8` |
| {{countdown ? countdown + 's' : '获取验证码'}} | `sendCode` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:1` |
| {{step===3?'确认重置':'下一步'}} | `next` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:1` |
| {{step===1?'返回登录':'上一步'}} | `back` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:1` |
| {{demoMode ? '进入原型' : '登录'}} | `submit` | `` | `aigicrm-miniprogram/pages/login/index.wxml:1` |
| 忘记密码？ | `openForgot` | `{{!demoMode}}` | `aigicrm-miniprogram/pages/login/index.wxml:2` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:1` |
| 查看客户 | `openCustomer` | `{{customerId}}` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:1` |
| 标记已处理 | `process` | `` | `aigicrm-miniprogram/pages/notification/detail/index.wxml:1` |
| 查看详情 | `` | `` | `aigicrm-miniprogram/pages/notification/index.wxml:1` |
| 查看客户 | `` | `{{item.customerId}}` | `aigicrm-miniprogram/pages/notification/index.wxml:1` |
| 标记已处理 | `process` | `` | `aigicrm-miniprogram/pages/notification/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:1` |
| 清空 | `clearDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:1` |
| 保存草稿 | `saveDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:1` |
| 退出登录 | `logout` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:1` |
| 意见反馈 | `openFeedback` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:2` |
| 确认修改 | `submit` | `` | `aigicrm-miniprogram/pages/profile/password/index.wxml:1` |
| 绑定微信 | `bindWechat` | `` | `aigicrm-miniprogram/pages/profile/wechat/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/ranking/detail/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 待处理 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 到期客户 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 查询 | `search` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 客户详情 | `openCustomer` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 已处理 | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 已处理 | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:1` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/sea/claim-detail/index.wxml:1` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:1` |
| 搜索 | `loadRecords` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:1` |
| 捞取 | `claim` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:1` |
| 查看最新捞取申请 | `openClaimDetail` | `{{claimRecords.length}}` | `aigicrm-miniprogram/pages/sea/index.wxml:3` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:8` |
| 刷新数据 | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:52` |
| 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:1` |
| 查看统计明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:2` |

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
| `downloadImportTemplate` | `GET` | `/customer/${customerId}/files` | `aigicrm-ui/src/api/customer.js` |
| `attachCustomerFile` | `POST` | `/customer/${customerId}/files/${fileId}` | `aigicrm-ui/src/api/customer.js` |
| `detachCustomerFile` | `DELETE` | `/customer/${customerId}/files/${fileId}` | `aigicrm-ui/src/api/customer.js` |
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
| `markIncentiveRead` | `POST` | `/incentive/records/${recordId}/read` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveReadStats` | `GET` | `/incentive/read-stats` | `aigicrm-ui/src/api/incentive.js` |
| `checkIncentivePermission` | `GET` | `/incentive/permission/check` | `aigicrm-ui/src/api/incentive.js` |
| `getReminderList` | `GET` | `/reminder/list` | `aigicrm-ui/src/api/reminder.js` |
| `getExpiringList` | `GET` | `/reminder/expiring-list` | `aigicrm-ui/src/api/reminder.js` |
| `ensureExpiring` | `POST` | `/reminder/ensure-expiring` | `aigicrm-ui/src/api/reminder.js` |
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
| `login` | `POST` | `/api/auth/login` | `aigicrm-miniprogram/api/auth.js` |
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
| `addCustomerShares` | `POST` | `/api/customer/' + id + '/shares` | `aigicrm-miniprogram/api/customer.js` |
| `removeCustomerShare` | `DELETE` | `/api/customer/' + id + '/shares/' + userId` | `aigicrm-miniprogram/api/customer.js` |
| `addInvoice` | `POST` | `/api/customer/' + id + '/invoices` | `aigicrm-miniprogram/api/customer.js` |
| `addPayment` | `POST` | `/api/customer/' + id + '/payments` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerIndicator` | `GET` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerFiles` | `GET` | `/api/customer/' + id + '/files` | `aigicrm-miniprogram/api/customer.js` |
| `attachCustomerFile` | `POST` | `/api/customer/' + id + '/files/' + fileId` | `aigicrm-miniprogram/api/customer.js` |
| `detachCustomerFile` | `DELETE` | `/api/customer/' + id + '/files/' + fileId` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerIndicator` | `POST` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `releaseCustomer` | `POST` | `/api/customer/' + id + '/release` | `aigicrm-miniprogram/api/customer.js` |
| `getFileList` | `GET` | `/api/file/list` | `aigicrm-miniprogram/api/file.js` |
| `getDownloadToken` | `GET` | `/api/file/download-token` | `aigicrm-miniprogram/api/file.js` |
| `getFolders` | `GET` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `createFolder` | `POST` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `renameFolder` | `PUT` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `deleteFolder` | `DELETE` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `getSharedToMe` | `GET` | `/api/file/shared-to-me` | `aigicrm-miniprogram/api/file.js` |
| `shareFile` | `POST` | `/api/file/' + id + '/share` | `aigicrm-miniprogram/api/file.js` |
| `getCustomerRecords` | `GET` | `/api/incentive/customer-records` | `aigicrm-miniprogram/api/incentive.js` |
| `markIncentiveRead` | `POST` | `/api/incentive/records/' + recordId + '/read` | `aigicrm-miniprogram/api/incentive.js` |
| `getReadStats` | `GET` | `/api/incentive/read-stats` | `aigicrm-miniprogram/api/incentive.js` |
| `updateRecord` | `PUT` | `/api/incentive/records/' + id` | `aigicrm-miniprogram/api/incentive.js` |
| `getReminderList` | `GET` | `/api/reminder/list` | `aigicrm-miniprogram/api/reminder.js` |
| `getExpiringList` | `GET` | `/api/reminder/expiring-list` | `aigicrm-miniprogram/api/reminder.js` |
| `ensureExpiring` | `POST` | `/api/reminder/ensure-expiring` | `aigicrm-miniprogram/api/reminder.js` |
| `processReminder` | `POST` | `/api/reminder/' + id + '/process` | `aigicrm-miniprogram/api/reminder.js` |
| `getPendingCount` | `GET` | `/api/reminder/pending-count` | `aigicrm-miniprogram/api/reminder.js` |
| `getRanking` | `GET` | `/api/score/ranking` | `aigicrm-miniprogram/api/score.js` |
| `getSeaList` | `GET` | `/api/sea/list` | `aigicrm-miniprogram/api/sea.js` |
| `getSeaStatistics` | `GET` | `/api/sea/statistics` | `aigicrm-miniprogram/api/sea.js` |
| `getVisibleSeaConfigs` | `GET` | `/api/sea/configs/visible` | `aigicrm-miniprogram/api/sea.js` |
| `getClaimRecords` | `GET` | `/api/sea/claim-records` | `aigicrm-miniprogram/api/sea.js` |
| `getIndicatorStats` | `GET` | `/api/statistics/indicator-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getDashboardData` | `GET` | `/api/statistics/dashboard` | `aigicrm-miniprogram/api/statistics.js` |
| `getTeamActivities` | `GET` | `/api/statistics/team-activities` | `aigicrm-miniprogram/api/statistics.js` |
| `getDailyStats` | `GET` | `/api/statistics/daily-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getWeeklyStats` | `GET` | `/api/statistics/weekly-stats` | `aigicrm-miniprogram/api/statistics.js` |

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
| `GET` | `/api/customer/{id}/files` | `hasAuthority('customer:view') and hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/files/{fileId}` | `hasAuthority('customer:edit') and hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/files/{fileId}` | `hasAuthority('customer:edit') and hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/list` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/list/counts` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/add` | `hasAuthority('customer:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}` | `hasAuthority('customer:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/logs` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/logs` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/statistics` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices/summary` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/invoices` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/invoices/{invoiceId}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
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
| `POST` | `/api/customer/{id}/payments` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}/payments/{paymentId}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
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
| `GET` | `/api/incentive/category-stats` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/users` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/permission/list` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `POST` | `/api/incentive/permission/grant` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `DELETE` | `/api/incentive/permission/{id}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `GET` | `/api/incentive/permission/check` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java` |
| `GET` | `/api/reminder/expiring-list` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
| `GET` | `/api/reminder/list` | `hasAuthority('reminder:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java` |
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
| `DELETE` | `/api/sea/pool/{id}` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java` |
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
| `contact:view` | 客户联系人查看 (V25__add_finance_view_permission.sql) |
| `file:acl:manage` | 文件ACL管理 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `file:delete` | 文件删除 (V26__split_file_mutation_permissions.sql) |
| `file:edit` | 文件编辑 (V26__split_file_mutation_permissions.sql) |
| `file:folder:manage` | 文件夹管理 (V26__split_file_mutation_permissions.sql) |
| `file:share` | 文件分享 (V26__split_file_mutation_permissions.sql) |
| `file:upload` | 文件上传 (V26__split_file_mutation_permissions.sql) |
| `finance:view` | 客户财务查看 (V25__add_finance_view_permission.sql) |
| `incentive:add` | 激励新增 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `incentive:read-stats` | 激励查询阅读统计 (V24__incentive_read_status.sql) |
| `sea:import` | 公海导入 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |

## Permission Drift Candidates

### Backend Permissions Missing In Dictionary Or Local Migrations

- None

### Frontend Code Permission Tokens Missing In Known Sources

- None

### Dictionary Permissions Not Directly Seen In Backend Annotations

- `customer:share`: 
- `incentive:delete`: 
- `incentive:manage`: 
- `incentive:view-all`: ✅
- `incentive:view-shared`: 
- `score:ranking`: 
- `sea:claim`: 
- `sea:pool:delete`: 
- `statistics:scoreboard`: 
- `system:menu:edit`: 

### Backend Code References For Dictionary-Only Permissions

| Permission | Code Reference | Context |
| --- | --- | --- |
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
- A backend permission missing in both the generated dictionary and local Flyway sys_menu migrations is high-risk because users may not be able to receive that authority through normal role-menu binding.
- This report is local-source based. For production sign-off, regenerate from live database configuration.
