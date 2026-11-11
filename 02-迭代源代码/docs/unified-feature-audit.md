# AIGICRM Unified Feature Audit

Generated from local source: `02-迭代源代码`

## Summary

- PC routes: 35
- PC API functions: 205
- Mini Program pages: 42
- Mini Program API functions: 108
- Backend endpoints: 230
- PC UI actions: 263
- Mini Program UI actions: 250
- Backend permission tokens: 69
- Permission dictionary tokens: 81
- Runtime permission dictionary tokens: 73
- Compatibility/deprecated dictionary tokens: 8
- Local migration permission tokens: 48
- Backend permissions missing in dictionary or local migrations: 0
- Runtime dictionary permissions not directly seen in backend annotations: 6
- Runtime dictionary permissions referenced outside backend annotations: 3
- Runtime dictionary permissions not seen in backend annotations or code references: 3
- UI permission tokens missing in backend, dictionary, and local migrations: 0
- Frontend code permission tokens: 59
- Frontend code permission tokens missing in known sources: 0
- PC API wrappers without backend endpoint match: 0
- Mini Program API wrappers without backend endpoint match: 4

## PC Routes

| Route | Title | Permission | Source |
| --- | --- | --- | --- |
| `/login` | 登录 | `` | `aigicrm-ui/src/router/index.js` |
| `/403` | 无权限 | `` | `aigicrm-ui/src/router/index.js` |
| `/mp-file-picker` | 手机文件上传 | `` | `aigicrm-ui/src/router/index.js` |
| `/:pathMatch(.*)*` | 页面不存在 | `` | `aigicrm-ui/src/router/index.js` |
| `/` | 工作台 | `dashboard:view` | `aigicrm-ui/src/router/index.js` |
| `customer` | 客户列表 | `customer:view` | `aigicrm-ui/src/router/index.js` |
| `customer/add` | 新增客户 | `customer:add` | `aigicrm-ui/src/router/index.js` |
| `customer/edit/:id` | 编辑客户 | `customer:edit` | `aigicrm-ui/src/router/index.js` |
| `customer/detail/:id` | 客户详情 | `customer:view` | `aigicrm-ui/src/router/index.js` |
| `file` | 文件管理 | `file:view` | `aigicrm-ui/src/router/index.js` |
| `incentive` | 激励上传 | `incentive:add` | `aigicrm-ui/src/router/index.js` |
| `incentive/stats` | 激励统计 | `incentive:stats:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/search` | 激励查询 | `incentive:view` | `aigicrm-ui/src/router/index.js` |
| `incentive/ranking` | 学分排行 | `score:view` | `aigicrm-ui/src/router/index.js` |
| `task` | 我的任务 | `task:view` | `aigicrm-ui/src/router/index.js` |
| `reminder` | 提醒管理 | `reminder:view` | `aigicrm-ui/src/router/index.js` |
| `sea` | 公海池 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `sea/records` | 捞取记录 | `sea:view` | `aigicrm-ui/src/router/index.js` |
| `approval` | 审批管理 | `approval:view` | `aigicrm-ui/src/router/index.js` |
| `statistics` | 统计分析 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `score/entry` | 学分录入 | `score:entry` | `aigicrm-ui/src/router/index.js` |
| `score/ranking` | 学分排行 | `score:view` | `aigicrm-ui/src/router/index.js` |
| `ranking/customer` | 客户排行 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `indicator` | 核心客户六项指标 | `statistics:view` | `aigicrm-ui/src/router/index.js` |
| `system/user` | 人员管理 | `system:user:view` | `aigicrm-ui/src/router/index.js` |
| `system/org` | 组织架构 | `system:dept:view` | `aigicrm-ui/src/router/index.js` |
| `system/dept` | 组织架构 | `system:dept:view` | `aigicrm-ui/src/router/index.js` |
| `system/role` | 角色管理 | `system:role:view` | `aigicrm-ui/src/router/index.js` |
| `system/position` | 岗位管理 | `system:position:view` | `aigicrm-ui/src/router/index.js` |
| `system/config` | 系统设置 | `system:config:view` | `aigicrm-ui/src/router/index.js` |
| `system/perm-center` | 权限中心(旧) | `system:config:view` | `aigicrm-ui/src/router/index.js` |
| `system/log` | 日志管理(旧) | `system:log:view` | `aigicrm-ui/src/router/index.js` |
| `system/recycle` | 回收站 | `system:recycle:view` | `aigicrm-ui/src/router/index.js` |
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
| `pages/customer/filter/index` |
| `pages/customer/indicator/index` |
| `pages/files/index` |
| `pages/files/folder-form/index` |
| `pages/files/textview/index` |
| `pages/files/picker/index` |
| `pages/incentive/index` |
| `pages/incentive/home/index` |
| `pages/incentive/upload/index` |
| `pages/incentive/detail/index` |
| `pages/ranking/index` |
| `pages/ranking/detail/index` |
| `pages/statistics/index` |
| `pages/statistics/detail/index` |
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
| `pages/sea/index` |
| `pages/sea/claim-detail/index` |

## PC UI Actions

| Label | Handler | Permission Hint | Source |
| --- | --- | --- | --- |
| 去公海池发起捞取 | `router.push('/sea')` | `` | `aigicrm-ui/src/views/approval/index.vue:100` |
| 0" size="small" type="primary" plain @click="switchTab('mine')">查看我发起的 条 | `` | `` | `aigicrm-ui/src/views/approval/index.vue:101` |
| (icon/button) | `openDetail(row)` | `` | `aigicrm-ui/src/views/approval/index.vue:107` |
| (icon/button) | `handleApprove(row)` | `canApprove` | `aigicrm-ui/src/views/approval/index.vue:138` |
| (icon/button) | `handleReject(row)` | `canReject` | `aigicrm-ui/src/views/approval/index.vue:141` |
| 转交 | `openTransfer(node)` | `` | `aigicrm-ui/src/views/approval/index.vue:220` |
| 关闭 | `detailVisible = false` | `` | `aigicrm-ui/src/views/approval/index.vue:234` |
| 驳回 | `handleReject(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:235` |
| 通过 | `handleApprove(selectedApproval)` | `` | `aigicrm-ui/src/views/approval/index.vue:241` |
| 取消 | `transferVisible = false` | `` | `aigicrm-ui/src/views/approval/index.vue:263` |
| 确认转交 | `confirmTransfer` | `` | `aigicrm-ui/src/views/approval/index.vue:264` |
| 取消 | `extendApproveVisible = false` | `` | `aigicrm-ui/src/views/approval/index.vue:281` |
| 通过并延期 | `confirmExtendApprove` | `` | `aigicrm-ui/src/views/approval/index.vue:282` |
| 清空全部 | `clearAdvFilter` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:62` |
| 取消 | `showAdvFilter = false` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:63` |
| 确定 | `showAdvFilter = false` | `` | `aigicrm-ui/src/views/customer/AdvFilterDialog.vue:64` |
| 全选 | `setAllCols` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:4` |
| 全不选 | `clearCols` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:5` |
| 取消 | `colPickerVisible = false` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:12` |
| 确定 | `colPickerVisible = false` | `` | `aigicrm-ui/src/views/customer/ColPickerDialog.vue:12` |
| 添加联系人 | `addContactRow` | `` | `aigicrm-ui/src/views/customer/ContactsSection.vue:6` |
| (icon/button) | `contacts.splice(idx, 1)` | `` | `aigicrm-ui/src/views/customer/ContactsSection.vue:20` |
| 下载导入模板 (.xlsx) | `downloadTemplate` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:4` |
| 取消 | `showImportDialog = false` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:26` |
| 开始导入 | `doImport` | `` | `aigicrm-ui/src/views/customer/ImportDialog.vue:27` |
| 列设置 | `colPickerVisible = true` | `` | `aigicrm-ui/src/views/customer/MainTable.vue:26` |
| 释放() | `handleBatchRelease` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:29` |
| 归档() | `handleBatchArchive` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:30` |
| 删除() | `handleBatchDelete` | `canDelete` | `aigicrm-ui/src/views/customer/MainTable.vue:31` |
| 重试 | `loadData` | `` | `aigicrm-ui/src/views/customer/MainTable.vue:45` |
| 清空搜索与筛选 | `clearSearch` | `` | `aigicrm-ui/src/views/customer/MainTable.vue:57` |
| 恢复 | `handleRestore(row)` | `canRelease && row.status === 'archived'` | `aigicrm-ui/src/views/customer/MainTable.vue:110` |
| 释放 | `openRelease(row)` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:113` |
| 归档 | `handleArchive(row)` | `canRelease` | `aigicrm-ui/src/views/customer/MainTable.vue:114` |
| 删除 | `handleDelete(row)` | `canDelete` | `aigicrm-ui/src/views/customer/MainTable.vue:116` |
| 取消 | `showReleaseDialog = false` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:20` |
| 确认释放 | `confirmRelease` | `` | `aigicrm-ui/src/views/customer/ReleaseDialog.vue:21` |
| 高级筛选 0" size="small" type="warning" style="margin-left:4px"> | `showAdvFilter = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:4` |
| 批量导入 | `showImportDialog = true` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:7` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:8` |
| 新客户 | `$router.push('/customer/add')` | `` | `aigicrm-ui/src/views/customer/TopBar.vue:9` |
| 返回 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:5` |
| 取消 | `goBack` | `` | `aigicrm-ui/src/views/customer/add.vue:40` |
| (icon/button) | `handleSubmit` | `` | `aigicrm-ui/src/views/customer/add.vue:41` |
| 更新 | `saveInvoiceDate` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:14` |
| + 录入阶段数据 | `openStageDialog(null)` | `canWriteFinance` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:33` |
| 编辑 | `openStageDialog(row)` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:64` |
| 取消 | `showStageDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:173` |
| 保存 | `handleSaveStageAmount` | `` | `aigicrm-ui/src/views/customer/detail/FinanceTab.vue:174` |
| 保存指标 | `handleSaveIndicator` | `` | `aigicrm-ui/src/views/customer/detail/IndicatorTab.vue:26` |
| 保存 | `saveInvoiceDate` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:60` |
| 取消 | `invoiceDateEditing = false` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:61` |
| 申请延期 | `handleExtend` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:75` |
| 转移客户 | `openTransfer` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:77` |
| 取消 | `showTransferDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:98` |
| 提交转移审批 | `confirmTransfer` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:98` |
| 添加 | `editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:104` |
| (icon/button) | `openEditContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:114` |
| (icon/button) | `handleDeleteContact(c)` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:115` |
| 取消 | `showContactDialog=false` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:159` |
| 确认 | `handleSaveContact` | `` | `aigicrm-ui/src/views/customer/detail/InfoTab.vue:159` |
| 记录 | `handleSubmit` | `` | `aigicrm-ui/src/views/customer/detail/LogsTab.vue:17` |
| 重试加载 | `loadAll` | `` | `aigicrm-ui/src/views/customer/detail.vue:10` |
| 编辑 | `handleEdit` | `canEditCustomer` | `aigicrm-ui/src/views/customer/detail.vue:21` |
| 分享 | `showShareDialog = true` | `canShareCustomer` | `aigicrm-ui/src/views/customer/detail.vue:22` |
| 取消 | `showShareDialog=false` | `` | `aigicrm-ui/src/views/customer/detail.vue:157` |
| 确认分享 | `handleAddShare` | `` | `aigicrm-ui/src/views/customer/detail.vue:157` |
| 重试 | `loadAll` | `` | `aigicrm-ui/src/views/customer/detail.vue:167` |
| (icon/button) | `goBack` | `` | `aigicrm-ui/src/views/customer/detail.vue:168` |
| 通过 | `doApprove(item.id)` | `canApprove` | `aigicrm-ui/src/views/dashboard/index.vue:231` |
| 驳回 | `doReject(item.id)` | `canReject` | `aigicrm-ui/src/views/dashboard/index.vue:232` |
| 返回登录 | `goLogin` | `` | `aigicrm-ui/src/views/error/403.vue:7` |
| 返回工作台 | `goHome` | `` | `aigicrm-ui/src/views/error/NotFound.vue:9` |
| 返回上一页 | `goBack` | `` | `aigicrm-ui/src/views/error/NotFound.vue:10` |
| 保存授权 | `saveAcl` | `` | `aigicrm-ui/src/views/file/AclDialog.vue:14` |
| 移除 | `removeAcl(row)` | `` | `aigicrm-ui/src/views/file/AclDialog.vue:36` |
| 取消 | `createFolderVisible = false` | `` | `aigicrm-ui/src/views/file/CreateFolderDialog.vue:9` |
| 创建 | `handleCreateFolder` | `` | `aigicrm-ui/src/views/file/CreateFolderDialog.vue:10` |
| 取消 | `renameFolderVisible = false` | `` | `aigicrm-ui/src/views/file/RenameFolderDialog.vue:9` |
| 保存 | `handleRenameFolder` | `` | `aigicrm-ui/src/views/file/RenameFolderDialog.vue:10` |
| 取消 | `shareDialogVisible = false` | `` | `aigicrm-ui/src/views/file/ShareDialog.vue:10` |
| 确认分享 | `confirmShare` | `` | `aigicrm-ui/src/views/file/ShareDialog.vue:11` |
| 取消 | `storageApplyVisible = false` | `` | `aigicrm-ui/src/views/file/StorageApplyDialog.vue:50` |
| 提交申请 | `handleSubmit` | `` | `aigicrm-ui/src/views/file/StorageApplyDialog.vue:51` |
| 取消 | `uploadVisible = false` | `` | `aigicrm-ui/src/views/file/UploadDialog.vue:22` |
| 开始上传{{ pendingFiles.length ? `（${pendingFiles.length} 个）` : '' }} | `startUploads` | `` | `aigicrm-ui/src/views/file/UploadDialog.vue:23` |
| 申请扩容 | `openStorageApply` | `` | `aigicrm-ui/src/views/file/index.vue:26` |
| 新建文件夹 | `openCreateFolder` | `canCreateFolder` | `aigicrm-ui/src/views/file/index.vue:37` |
| 上传文件 | `openUpload` | `` | `aigicrm-ui/src/views/file/index.vue:48` |
| 导出 | `exportPersonRank` | `userStore.hasPermission('incentive:person-rank:export')` | `aigicrm-ui/src/views/incentive/PersonRankTableView.vue:9` |
| 导出 | `exportRank()` | `userStore.hasPermission('incentive:rank:export')` | `aigicrm-ui/src/views/incentive/RankTableView.vue:11` |
| 追加 | `startReplace(row)` | `` | `aigicrm-ui/src/views/incentive/RecordTable.vue:68` |
| 删除 | `handleDeleteRecord(row)` | `` | `aigicrm-ui/src/views/incentive/RecordTable.vue:69` |
| 清除 | `detailKeyword=''` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:51` |
| (icon/button) | `preview(f.id, f.type, f.label, previewableOf(r))` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:124` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:125` |
| (icon/button) | `preview(f.id, f.type, f.label, previewableOf(r))` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:133` |
| (icon/button) | `preview(f.id, f.type, f.label, previewableOf(r))` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:140` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:141` |
| (icon/button) | `downloadFile(f.id, f.label)` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:148` |
| 清除搜索 | `detailKeyword=''` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:164` |
| 关闭 | `visible=false` | `` | `aigicrm-ui/src/views/incentive/SearchDetailDialog.vue:170` |
| 下载 | `downloadCurrent` | `` | `aigicrm-ui/src/views/incentive/SearchLightbox.vue:11` |
| (icon/button) | `visible=false` | `` | `aigicrm-ui/src/views/incentive/SearchLightbox.vue:12` |
| 下载文件 | `downloadCurrent` | `` | `aigicrm-ui/src/views/incentive/SearchLightbox.vue:33` |
| 仍要下载试试 | `downloadCurrent` | `` | `aigicrm-ui/src/views/incentive/SearchLightbox.vue:39` |
| ← 返回激励统计 | `goBack` | `` | `aigicrm-ui/src/views/incentive/StatsDetail.vue:6` |
| 分 上传文件 | `setPanelExpanded(false)` | `` | `aigicrm-ui/src/views/incentive/SubCategoryPanel.vue:8` |
| 取消 | `showCustomerDialog=false` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:140` |
| (icon/button) | `confirmUpload` | `` | `aigicrm-ui/src/views/incentive/UploadDialog.vue:141` |
| 查询 | `doSearch` | `` | `aigicrm-ui/src/views/incentive/case-library.vue:23` |
| 高级筛选 | `showFilter=!showFilter` | `` | `aigicrm-ui/src/views/incentive/search.vue:9` |
| 查询 | `doSearch` | `` | `aigicrm-ui/src/views/incentive/search.vue:16` |
| 重置 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:17` |
| 清空筛选 | `doReset` | `` | `aigicrm-ui/src/views/incentive/search.vue:25` |
| 0 ? 'info' : 'primary'" :disabled="countdown > 0 || !form.method" :loading="sendingCode" @click="handleSendCode" size="large" class="send-btn"> | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:61` |
| 1" @click="handleBack">上一步 | `` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:88` |
| 取消 | `visible = false` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:89` |
| (icon/button) | `handleNext` | `` | `aigicrm-ui/src/views/login/ForgotPasswordDialog.vue:90` |
| 登 录 | `handleLogin` | `` | `aigicrm-ui/src/views/login/index.vue:41` |
| ← 返回提醒管理 | `goBack` | `` | `aigicrm-ui/src/views/reminder/detail.vue:4` |
| 查看客户完整档案 › | `goCustomer` | `` | `aigicrm-ui/src/views/reminder/detail.vue:52` |
| 标记已处理 | `markProcessed` | `` | `aigicrm-ui/src/views/reminder/detail.vue:73` |
| 返回列表 | `goBack` | `` | `aigicrm-ui/src/views/reminder/detail.vue:79` |
| 清除本页 | `handleClearAll` | `` | `aigicrm-ui/src/views/reminder/index.vue:30` |
| 详情 | `openDetail(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:72` |
| 已处理 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:75` |
| 已处理 | `handleProcessShare(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:78` |
| 已跟进 | `handleFollowUp(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:81` |
| 已跟进 | `` | `` | `aigicrm-ui/src/views/reminder/index.vue:82` |
| 申请延期 | `handleExtend(row)` | `` | `aigicrm-ui/src/views/reminder/index.vue:83` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/score/entry.vue:18` |
| 查看排行 | `goRanking` | `` | `aigicrm-ui/src/views/score/entry.vue:20` |
| 导出 | `handleExport` | `` | `aigicrm-ui/src/views/score/entry.vue:21` |
| + | `` | `` | `aigicrm-ui/src/views/score/entry.vue:58` |
| (icon/button) | `handleDeleteRow(item,idx)` | `` | `aigicrm-ui/src/views/score/entry.vue:86` |
| 添加行 | `handleAddRow` | `` | `aigicrm-ui/src/views/score/entry.vue:97` |
| 学分规则 | `showRules = true` | `` | `aigicrm-ui/src/views/score/ranking.vue:17` |
| 捞取记录 | `router.push('/sea/records')` | `` | `aigicrm-ui/src/views/sea/index.vue:15` |
| 批量导入 | `showImportDialog=true` | `canImport` | `aigicrm-ui/src/views/sea/index.vue:16` |
| 重置 | `resetFilters` | `` | `aigicrm-ui/src/views/sea/index.vue:55` |
| 捞取 | `handleClaim(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:99` |
| 编辑 | `handleEdit(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:106` |
| 删除 | `handleDelete(row)` | `` | `aigicrm-ui/src/views/sea/index.vue:107` |
| 下载导入模板 | `handleDownloadTemplate` | `` | `aigicrm-ui/src/views/sea/index.vue:119` |
| (icon/button) | `showImportDialog=false` | `` | `aigicrm-ui/src/views/sea/index.vue:134` |
| 开始导入 | `handleImport` | `` | `aigicrm-ui/src/views/sea/index.vue:134` |
| 公海池 | `router.push('/sea')` | `` | `aigicrm-ui/src/views/sea/records.vue:8` |
| 刷新 | `loadAll` | `` | `aigicrm-ui/src/views/statistics/index.vue:26` |
| 上传Logo | `` | `` | `aigicrm-ui/src/views/system/BasicSettings.vue:28` |
| 清除 | `configMap['system.logo'] = ''` | `canEditConfig && configMap['system.logo']` | `aigicrm-ui/src/views/system/BasicSettings.vue:30` |
| 保存设置 | `handleSave` | `canEditConfig` | `aigicrm-ui/src/views/system/BasicSettings.vue:174` |
| 新增类别 | `openCategoryDialog()` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:13` |
| 编辑 | `openCategoryDialog(top)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:27` |
| 子项 | `openCategoryDialog(null, top.id)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:28` |
| 删除 | `handleDeleteCategory(top)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:29` |
| 编辑 | `openCategoryDialog(row)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:63` |
| 删除 | `handleDeleteCategory(row)` | `userStore.hasPermission('incentive:edit')` | `aigicrm-ui/src/views/system/CategoryManager.vue:64` |
| 取消 | `categoryDialogVisible = false` | `` | `aigicrm-ui/src/views/system/CategoryManager.vue:108` |
| 保存 | `submitCategory` | `` | `aigicrm-ui/src/views/system/CategoryManager.vue:109` |
| 新增 | `handleAddType` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:7` |
| 编辑 | `handleEditType(t)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:24` |
| 删除 | `handleDeleteType(t)` | `canEditConfig && !t.builtIn` | `aigicrm-ui/src/views/system/DictManager.vue:25` |
| 新增 | `handleAddData` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:40` |
| 编辑 | `handleEditData(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:53` |
| 删除 | `handleDeleteData(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/DictManager.vue:54` |
| 取消 | `typeDialogVisible = false` | `` | `aigicrm-ui/src/views/system/DictManager.vue:79` |
| 确定 | `submitType` | `` | `aigicrm-ui/src/views/system/DictManager.vue:80` |
| 取消 | `dataDialogVisible = false` | `` | `aigicrm-ui/src/views/system/DictManager.vue:101` |
| 确定 | `submitData` | `` | `aigicrm-ui/src/views/system/DictManager.vue:102` |
| 删除 | `removeRow(t.value, scope?.row?.roleId)` | `canEdit` | `aigicrm-ui/src/views/system/FileSpaceDefaults.vue:21` |
| 保存 | `saveRow(t.value)` | `` | `aigicrm-ui/src/views/system/FileSpaceDefaults.vue:33` |
| 添加授权 | `openPermDialog` | `canEdit` | `aigicrm-ui/src/views/system/PermCenter.vue:28` |
| 撤销 | `revokePerm(scope?.row?.id)` | `canEdit` | `aigicrm-ui/src/views/system/PermCenter.vue:48` |
| 全选 | `checkAllUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:23` |
| 清空 | `clearUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:24` |
| 清空 | `clearEditUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:70` |
| 清空 | `clearDeleteUsers` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:94` |
| 取消 | `showPermDialog=false` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:109` |
| 确认授权 | `grantPerm` | `` | `aigicrm-ui/src/views/system/PermGrantDialog.vue:110` |
| (icon/button) | `btn.tab ? activeTab=btn.tab : router.push(btn.href)` | `` | `aigicrm-ui/src/views/system/PermMatrixManager.vue:21` |
| 刷新 | `loadPermData` | `` | `aigicrm-ui/src/views/system/PermMatrixManager.vue:32` |
| 刷新 | `loadAll` | `` | `aigicrm-ui/src/views/system/PermScopeManager.vue:18` |
| 全不勾 | `toggleModuleAll(false)` | `` | `aigicrm-ui/src/views/system/PermScopeManager.vue:57` |
| 全勾 | `toggleModuleAll(true)` | `` | `aigicrm-ui/src/views/system/PermScopeManager.vue:58` |
| 恢复默认范围 | `resetCurrent` | `` | `aigicrm-ui/src/views/system/PermScopeManager.vue:59` |
| 新增公海池 | `handleAddPool` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:6` |
| 编辑 | `handleEditPool(pool)` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:21` |
| 删除 | `handleDeletePool(pool)` | `canEditSeaConfig` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:22` |
| 清空 | `clearSeaSelection` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:65` |
| 清空 | `clearSecSelection(sec.key)` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:128` |
| 取消 | `poolDialogVisible = false` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:161` |
| 确定 | `submitPool` | `` | `aigicrm-ui/src/views/system/SeaConfigManager.vue:162` |
| 新增用户 | `openDialog()` | `userStore.hasPermission('system:user:add')` | `aigicrm-ui/src/views/system/UserFilterBar.vue:8` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/UserFormDialog.vue:107` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/UserFormDialog.vue:108` |
| 重试 | `retryLoad` | `` | `aigicrm-ui/src/views/system/UserTable.vue:7` |
| 编辑 | `openDialog(row)` | `userStore.hasPermission('system:user:edit')` | `aigicrm-ui/src/views/system/UserTable.vue:69` |
| 重置密码 | `handleResetPwd(row)` | `userStore.hasPermission('system:user:edit')` | `aigicrm-ui/src/views/system/UserTable.vue:70` |
| 删除 | `handleDelete(row)` | `userStore.hasPermission('system:user:delete')` | `aigicrm-ui/src/views/system/UserTable.vue:71` |
| 新增流程 | `openDialog()` | `canEditConfig` | `aigicrm-ui/src/views/system/approvalConfig.vue:6` |
| 编辑 | `openDialog(row)` | `canEditConfig` | `aigicrm-ui/src/views/system/approvalConfig.vue:28` |
| 删除 | `` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:30` |
| 取消 | `visible=false` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:77` |
| 保存 | `doSave` | `` | `aigicrm-ui/src/views/system/approvalConfig.vue:78` |
| 新增部门 | `openDialog()` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:5` |
| 管理{{ adminCounts[row.id] ? ` (${adminCounts[row.id]})` : '' }} | `showAdmins(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:35` |
| 编辑 | `openDialog(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:36` |
| 添加子部门 | `openDialog(null, row.id)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:37` |
| 删除 | `handleDelete(row)` | `canEditDept` | `aigicrm-ui/src/views/system/dept.vue:38` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/dept.vue:79` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/dept.vue:80` |
| 添加 | `doAddAdmin` | `` | `aigicrm-ui/src/views/system/dept.vue:92` |
| 移除 | `` | `` | `aigicrm-ui/src/views/system/dept.vue:101` |
| 新增集团 | `openDialog()` | `canAddGroup` | `aigicrm-ui/src/views/system/group.vue:5` |
| 编辑 | `openDialog(row)` | `canEditGroup` | `aigicrm-ui/src/views/system/group.vue:23` |
| 禁用 | `handleDelete(row)` | `canDeleteGroup` | `aigicrm-ui/src/views/system/group.vue:25` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/group.vue:52` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/group.vue:53` |
| 刷新 | `load` | `` | `aigicrm-ui/src/views/system/incentive-read-ranking.vue:19` |
| 查看 | `showDetail(row)` | `` | `aigicrm-ui/src/views/system/log.vue:72` |
| 新增岗位 | `openDialog()` | `canAddPosition` | `aigicrm-ui/src/views/system/position.vue:5` |
| 编辑 | `openDialog(row)` | `canEditPosition` | `aigicrm-ui/src/views/system/position.vue:33` |
| 规则 | `openRuleDialog(row)` | `canEditPosition && isAdmin` | `aigicrm-ui/src/views/system/position.vue:37` |
| 删除 | `handleDelete(row)` | `canDeletePosition` | `aigicrm-ui/src/views/system/position.vue:38` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/position.vue:76` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/position.vue:77` |
| 取消 | `ruleDialogVisible = false` | `` | `aigicrm-ui/src/views/system/position.vue:102` |
| 保存规则 | `handleSaveRules` | `` | `aigicrm-ui/src/views/system/position.vue:103` |
| 清空回收站 | `handlePurgeFiles` | `` | `aigicrm-ui/src/views/system/recycle.vue:47` |
| 删除为软删除留痕：客户恢复校验同名活跃卡、负责人与公海残留；人员恢复后需重新分配角色；文件删除先入回收站，保留期内可恢复，超期自动清理。 列表加载失败，请检查网络后重试。 重试 | `refreshPage` | `` | `aigicrm-ui/src/views/system/recycle.vue:52` |
| 详情 | `openCustomerDetail(row)` | `` | `aigicrm-ui/src/views/system/recycle.vue:110` |
| 恢复 | `handleRestoreCustomer(row)` | `canRestore` | `aigicrm-ui/src/views/system/recycle.vue:111` |
| 彻底删除 | `handleHardDeleteCustomer(row)` | `canPurge` | `aigicrm-ui/src/views/system/recycle.vue:112` |
| 详情 | `openUserDetail(row)` | `` | `aigicrm-ui/src/views/system/recycle.vue:159` |
| 恢复 | `handleRestoreUser(row)` | `canRestore` | `aigicrm-ui/src/views/system/recycle.vue:160` |
| 彻底删除 | `handleHardDeleteUser(row)` | `canPurge` | `aigicrm-ui/src/views/system/recycle.vue:161` |
| 详情 | `openFileDetail(row)` | `` | `aigicrm-ui/src/views/system/recycle.vue:217` |
| 恢复 | `handleRestoreFile(row)` | `canRestore && row.contentAvailable` | `aigicrm-ui/src/views/system/recycle.vue:218` |
| 彻底删除 | `handleHardDeleteFile(row)` | `canPurge` | `aigicrm-ui/src/views/system/recycle.vue:219` |
| 下载文件 | `downloadRecycleFile(detailData.file)` | `` | `aigicrm-ui/src/views/system/recycle.vue:314` |
| 关闭 | `detailVisible = false` | `` | `aigicrm-ui/src/views/system/recycle.vue:323` |
| 恢复 | `restoreFromDetail` | `canRestore && detailData && detailCanRestore` | `aigicrm-ui/src/views/system/recycle.vue:324` |
| 彻底删除 | `hardDeleteFromDetail` | `canPurge && detailData` | `aigicrm-ui/src/views/system/recycle.vue:325` |
| 新增角色 | `openDialog()` | `canAddRole` | `aigicrm-ui/src/views/system/role.vue:5` |
| 编辑 | `openDialog(row)` | `canEditRole` | `aigicrm-ui/src/views/system/role.vue:30` |
| 权限 | `openPermDialog(row)` | `canEditRole` | `aigicrm-ui/src/views/system/role.vue:31` |
| 删除 | `handleDelete(row)` | `canDeleteRole` | `aigicrm-ui/src/views/system/role.vue:32` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/role.vue:94` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/role.vue:95` |
| 关闭 | `permDialogVisible = false` | `` | `aigicrm-ui/src/views/system/role.vue:112` |
| 去权限中心配置 | `goPermCenter` | `` | `aigicrm-ui/src/views/system/role.vue:113` |
| 新增规则 | `openDialog()` | `canAddRule` | `aigicrm-ui/src/views/system/rule.vue:5` |
| 编辑 | `openDialog(row)` | `canEditRule` | `aigicrm-ui/src/views/system/rule.vue:31` |
| 删除 | `handleDelete(row)` | `canDeleteRule` | `aigicrm-ui/src/views/system/rule.vue:32` |
| 取消 | `dialogVisible = false` | `` | `aigicrm-ui/src/views/system/rule.vue:92` |
| 保存 | `handleSave` | `` | `aigicrm-ui/src/views/system/rule.vue:93` |
| 完成 | `showComplete(row)` | `` | `aigicrm-ui/src/views/task/index.vue:78` |
| 取消 | `showCancel(row)` | `` | `aigicrm-ui/src/views/task/index.vue:79` |
| 取消 | `completeVisible=false` | `` | `aigicrm-ui/src/views/task/index.vue:98` |
| 确认完成 | `confirmComplete` | `` | `aigicrm-ui/src/views/task/index.vue:99` |
| 取消 | `cancelVisible=false` | `` | `aigicrm-ui/src/views/task/index.vue:107` |
| 确认取消 | `confirmCancel` | `` | `aigicrm-ui/src/views/task/index.vue:108` |

## Mini Program UI Actions

| Label | Handler | Condition Hint | Source |
| --- | --- | --- | --- |
| › | `openApp` | `` | `aigicrm-miniprogram/pages/app/index.wxml:20` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:8` |
| 查看客户完整档案 | `openCustomer` | `` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:30` |
| 驳回 | `reject` | `{{approvalView.showRejectAction}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:58` |
| 通过 | `approve` | `{{approvalView.showApproveAction}}` | `aigicrm-miniprogram/pages/approval/detail/index.wxml:59` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:8` |
| (button) | `changeTab` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:16` |
| 查询 | `search` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:22` |
| 查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/approval/index.wxml:48` |
| 客户档案 | `openCustomer` | `{{item.showCustomerAction}}` | `aigicrm-miniprogram/pages/approval/index.wxml:49` |
| 驳回 | `reject` | `{{item.showRejectAction}}` | `aigicrm-miniprogram/pages/approval/index.wxml:50` |
| 通过 | `approve` | `{{item.showApproveAction}}` | `aigicrm-miniprogram/pages/approval/index.wxml:51` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:8` |
| 拨打电话 | `call` | `` | `aigicrm-miniprogram/pages/customer/contact/detail/index.wxml:17` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:8` |
| (button) | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:21` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/contact/form/index.wxml:22` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:8` |
| 手机号 拨打电话 编辑 删除 点击卡片查看详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:13` |
| 拨打电话 | `call` | `` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:17` |
| 编辑 | `editContact` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:18` |
| 删除 | `deleteContact` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:19` |
| 新增联系人 | `openForm` | `{{contactView.showAddAction}}` | `aigicrm-miniprogram/pages/customer/contact/index.wxml:25` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:8` |
| 编辑信息 | `editCustomer` | `{{detailView.showEditAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:19` |
| 申请延期 | `submitExtendApproval` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:30` |
| 查看明细 | `openFinance` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:66` |
| 维护指标 | `openIndicatorForm` | `{{detailView.showIndicatorEditAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:86` |
| 分 | `selectRelationship` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:110` |
| 编辑六项指标 | `openIndicatorForm` | `{{detailView.showIndicatorEditAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:147` |
| 释放到公海 | `releaseToSea` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:151` |
| 归档（停止跟进） | `archiveCurrentCustomer` | `` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:156` |
| 删除客户 | `deleteCurrentCustomer` | `{{detailView.showDeleteButton}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:162` |
| 人联系人 | `openContacts` | `{{detailView.showContactsAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:167` |
| 享分享 | `openShares` | `{{detailView.showSharesAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:168` |
| 新增跟进记录 | `openLogForm` | `{{detailView.showLogAction}}` | `aigicrm-miniprogram/pages/customer/detail/index.wxml:170` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:8` |
| 重置 | `reset` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:43` |
| 应用筛选 | `apply` | `` | `aigicrm-miniprogram/pages/customer/filter/index.wxml:44` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:8` |
| (button) | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:34` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/finance/form/index.wxml:35` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:9` |
| 编辑 | `openForm` | `{{stageView.showAddAction}}` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:29` |
| 录入阶段数据 | `openForm` | `{{stageView.showAddAction}}` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:50` |
| 录入与保护期规则 | `toggleNote` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:53` |
| 历史逐笔明细（只读） 开票 笔 · 回款 笔 | `toggleHistory` | `` | `aigicrm-miniprogram/pages/customer/finance/index.wxml:63` |
| 返回 | `cancel` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:8` |
| 联系人管理› | `openContacts` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:127` |
| 分 | `selectIndicatorScore` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:153` |
| (button) | `cancel` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:170` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/form/index.wxml:171` |
| 海 | `openSea` | `{{canViewSea}}` | `aigicrm-miniprogram/pages/customer/index.wxml:8` |
| ↺ | `resetFilters` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:9` |
| 搜索 | `search` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:15` |
| 高级 | `openAdvancedFilter` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:20` |
| 清空 | `resetFilters` | `{{customerView.showReset}}` | `aigicrm-miniprogram/pages/customer/index.wxml:21` |
| (button) | `openDetail` | `` | `aigicrm-miniprogram/pages/customer/index.wxml:26` |
| ＋ | `addCustomer` | `{{canAdd}}` | `aigicrm-miniprogram/pages/customer/index.wxml:47` |
| 分 | `selectScore` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:27` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:36` |
| 取消 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/indicator/index.wxml:37` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:8` |
| (button) | `goBack` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:15` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/customer/log/index.wxml:16` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:8` |
| 撤回共享 | `removeShare` | `{{canEdit}}` | `aigicrm-miniprogram/pages/customer/share/index.wxml:16` |
| (button) | `goBack` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:27` |
| (button) | `addShares` | `` | `aigicrm-miniprogram/pages/customer/share/index.wxml:28` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:9` |
| 取消 | `back` | `` | `aigicrm-miniprogram/pages/files/folder-form/index.wxml:10` |
| ＋ 上传 | `uploadFile` | `{{fileView.showUploadAction}}` | `aigicrm-miniprogram/pages/files/index.wxml:8` |
| 申请扩容 › | `openStorageApply` | `{{storageApplyView.showApplyAction}}` | `aigicrm-miniprogram/pages/files/index.wxml:13` |
| 本地上传自测 | `runDevUploadSmoke` | `{{canDevUploadSmoke}}` | `aigicrm-miniprogram/pages/files/index.wxml:17` |
| 搜索 | `searchFiles` | `` | `aigicrm-miniprogram/pages/files/index.wxml:18` |
| (button) | `onTabTap` | `` | `aigicrm-miniprogram/pages/files/index.wxml:20` |
| 返回全部 | `clearFolder` | `{{fileView.showClearFolder}}` | `aigicrm-miniprogram/pages/files/index.wxml:23` |
| (button) | `openFolder` | `` | `aigicrm-miniprogram/pages/files/index.wxml:26` |
| 客户文档 | `openFolder` | `{{fileView.showFallbackFolders}}` | `aigicrm-miniprogram/pages/files/index.wxml:27` |
| 团队共享 | `openFolder` | `{{fileView.showFallbackFolders}}` | `aigicrm-miniprogram/pages/files/index.wxml:28` |
| ··· | `openFile` | `` | `aigicrm-miniprogram/pages/files/index.wxml:43` |
| ··· | `openActionSheet` | `` | `aigicrm-miniprogram/pages/files/index.wxml:52` |
| 取消 | `closeActionSheet` | `{{actionSheet.visible}}` | `aigicrm-miniprogram/pages/files/index.wxml:61` |
| 取消 | `noop` | `` | `aigicrm-miniprogram/pages/files/index.wxml:62` |
| (button) | `onSheetAction` | `` | `aigicrm-miniprogram/pages/files/index.wxml:65` |
| 取消 | `closeActionSheet` | `` | `aigicrm-miniprogram/pages/files/index.wxml:70` |
| ＋ 新建 | `openFolderForm` | `` | `aigicrm-miniprogram/pages/files/index.wxml:74` |
| ··· | `openFolderActions` | `` | `aigicrm-miniprogram/pages/files/index.wxml:79` |
| 申请扩容 关闭 当前配额 可申请上限 申请后总配额（GB） 申请理由 取消 提交申请 | `closeStorageApply` | `{{storageApplyView.visible}}` | `aigicrm-miniprogram/pages/files/index.wxml:86` |
| 申请扩容 关闭 当前配额 可申请上限 申请后总配额（GB） 申请理由 取消 提交申请 | `noop` | `` | `aigicrm-miniprogram/pages/files/index.wxml:87` |
| 关闭 | `closeStorageApply` | `` | `aigicrm-miniprogram/pages/files/index.wxml:90` |
| 取消 | `closeStorageApply` | `` | `aigicrm-miniprogram/pages/files/index.wxml:108` |
| 提交申请 | `submitStorageApply` | `` | `aigicrm-miniprogram/pages/files/index.wxml:109` |
| 上传到文件夹取消 根目录 上传到这里 | `cancelFolderSelect` | `{{folderSelect.visible}}` | `aigicrm-miniprogram/pages/files/index.wxml:115` |
| 上传到文件夹取消 根目录 上传到这里 | `noop` | `` | `aigicrm-miniprogram/pages/files/index.wxml:116` |
| 取消 | `cancelFolderSelect` | `` | `aigicrm-miniprogram/pages/files/index.wxml:117` |
| 根目录 | `selectFolderItem` | `` | `aigicrm-miniprogram/pages/files/index.wxml:120` |
| (button) | `selectFolderItem` | `` | `aigicrm-miniprogram/pages/files/index.wxml:122` |
| 上传到这里 | `confirmFolderSelect` | `` | `aigicrm-miniprogram/pages/files/index.wxml:124` |
| 重命名文件取消 确认重命名 | `cancelRenameModal` | `{{renameModal.visible}}` | `aigicrm-miniprogram/pages/files/index.wxml:129` |
| 重命名文件取消 确认重命名 | `noop` | `` | `aigicrm-miniprogram/pages/files/index.wxml:130` |
| 取消 | `cancelRenameModal` | `` | `aigicrm-miniprogram/pages/files/index.wxml:131` |
| 确认重命名 | `confirmRename` | `` | `aigicrm-miniprogram/pages/files/index.wxml:133` |
| (button) | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:6` |
| 全部应用 › | `openAppCenter` | `` | `aigicrm-miniprogram/pages/home/index.wxml:53` |
| 新增客户 | `openNewCustomer` | `` | `aigicrm-miniprogram/pages/home/index.wxml:56` |
| 四项服务 | `openIncentive` | `` | `aigicrm-miniprogram/pages/home/index.wxml:59` |
| 激励上传 | `openIncentiveUpload` | `` | `aigicrm-miniprogram/pages/home/index.wxml:62` |
| 审批管理 | `openApproval` | `` | `aigicrm-miniprogram/pages/home/index.wxml:65` |
| 提醒管理 | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:68` |
| 统计分析 | `openStatistics` | `` | `aigicrm-miniprogram/pages/home/index.wxml:71` |
| 全部提醒 › | `openReminder` | `` | `aigicrm-miniprogram/pages/home/index.wxml:82` |
| › | `openCustomer` | `` | `aigicrm-miniprogram/pages/home/index.wxml:87` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:8` |
| 回到今天 | `goToday` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:42` |
| ▶ | `previewAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:134` |
| (button) | `previewAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:144` |
| 打开 | `previewAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:156` |
| 下载 | `downloadAttachment` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:157` |
| 编辑 / 追加文件 | `editRecord` | `{{item.showEditAction}}` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:164` |
| 删除记录 | `removeRecord` | `{{item.showDeleteAction}}` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:165` |
| 关闭视频 | `closeVideoPreview` | `` | `aigicrm-miniprogram/pages/incentive/detail/index.wxml:175` |
| ↻ 刷新 | `refresh` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:11` |
| ＋ 上传 | `goUpload` | `{{canUploadEvidence}}` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:12` |
| (button) | `searchRecords` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:19` |
| 清空 | `resetRecordFilters` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:20` |
| (button) | `chooseIndustry` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:27` |
| (button) | `chooseProduct` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:36` |
| 按时间 | `switchListMode` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:49` |
| 按客户 | `switchListMode` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:50` |
| · · 个文件 › | `openFlatRecord` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:71` |
| · › | `openCustomer` | `` | `aigicrm-miniprogram/pages/incentive/home/index.wxml:96` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:9` |
| ＋ 上传 | `goUpload` | `{{canUploadEvidence}}` | `aigicrm-miniprogram/pages/incentive/index.wxml:10` |
| (button) | `switchTab` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:26` |
| (button) | `searchRecords` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:33` |
| 清空 | `resetRecordFilters` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:34` |
| (button) | `openRecordDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:85` |
| 全 全部分类查看所有激励记录 全部 › | `onCategoryAllTap` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:113` |
| 条 筛选中 › | `onCategoryRowTap` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:120` |
| 文件 › | `openGroupStatDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:146` |
| 文件 › | `openStatDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:150` |
| 明细 收起 ⌕ 清空 明细加载中... ▶ 打开 下载 0}}" page="" pageSize="" total="" loading="" bind:change="onStatDetailPagerChange" /> | `closeStatDetail` | `{{statDetail.visible}}` | `aigicrm-miniprogram/pages/incentive/index.wxml:158` |
| 明细 收起 ⌕ 清空 明细加载中... ▶ 打开 下载 0}}" page="" pageSize="" total="" loading="" bind:change="onStatDetailPagerChange" /> | `noop` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:159` |
| 收起 | `closeStatDetail` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:166` |
| 清空 | `clearStatDetailKeyword` | `{{statDetail.keyword}}` | `aigicrm-miniprogram/pages/incentive/index.wxml:171` |
| ▶ | `previewStatDetailAttachment` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:195` |
| (button) | `previewStatDetailAttachment` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:199` |
| 打开 | `previewStatDetailAttachment` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:205` |
| 下载 | `downloadStatDetailAttachment` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:206` |
| 客户排行 | `switchRankTab` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:225` |
| 个人积分榜 | `switchRankTab` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:226` |
| 部门排行 | `switchRankTab` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:227` |
| ↻ | `loadScoreboard` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:291` |
| ↻ | `loadReadStats` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:388` |
| 点击重试 | `loadReadRanking` | `` | `aigicrm-miniprogram/pages/incentive/index.wxml:403` |
| ⌄ | `openCustomerSheet` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:39` |
| (button) | `previewSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:116` |
| 预览 | `previewSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:124` |
| 下载 | `downloadSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:125` |
| 移除 | `removeSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:126` |
| 拍照/相册 | `chooseImage` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:132` |
| 手机文件 | `pickFromDevice` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:133` |
| 从聊天选择 | `chooseFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:134` |
| (button) | `previewSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:158` |
| 预览 | `previewSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:166` |
| 下载 | `downloadSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:167` |
| 移除 | `removeSelectedFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:168` |
| 拍照/相册 | `chooseImage` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:174` |
| 手机文件 | `pickFromDevice` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:175` |
| 从聊天选择 | `chooseFile` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:176` |
| (button) | `cancelUpload` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:218` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:219` |
| 选择客户 ✕ 清空 个客户 加载中… 没有更多了 没有匹配「」的客户 | `closeCustomerSheet` | `{{customerSheet.open}}` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:227` |
| 选择客户 ✕ 清空 个客户 加载中… 没有更多了 没有匹配「」的客户 | `noop` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:228` |
| ✕ | `closeCustomerSheet` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:231` |
| 清空 | `clearCustomerKeyword` | `{{customerSheet.keyword}}` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:235` |
| (button) | `pickCustomer` | `` | `aigicrm-miniprogram/pages/incentive/upload/index.wxml:239` |
| (button) | `sendCode` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:18` |
| (button) | `next` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:28` |
| (button) | `back` | `` | `aigicrm-miniprogram/pages/login/forgot/index.wxml:29` |
| 记住账号 | `toggleRememberAccount` | `` | `aigicrm-miniprogram/pages/login/index.wxml:19` |
| 记住密码 | `toggleRememberPassword` | `` | `aigicrm-miniprogram/pages/login/index.wxml:23` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/login/index.wxml:28` |
| (button) | `toggleAgreed` | `` | `aigicrm-miniprogram/pages/login/index.wxml:31` |
| 《用户服务协议》 | `openAgreement` | `` | `aigicrm-miniprogram/pages/login/index.wxml:36` |
| 《隐私政策》 | `openPrivacyDoc` | `` | `aigicrm-miniprogram/pages/login/index.wxml:37` |
| 忘记密码？ | `openForgot` | `{{loginView.showForgotAction}}` | `aigicrm-miniprogram/pages/login/index.wxml:40` |
| 陕ICP备2024024412号 | `copyBeianUrl` | `` | `aigicrm-miniprogram/pages/login/index.wxml:43` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:8` |
| 清空 | `clearDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:18` |
| 保存草稿 | `saveDraft` | `` | `aigicrm-miniprogram/pages/profile/feedback/index.wxml:19` |
| ◉我的客户› | `openCustomers` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:29` |
| ★我的四项服务› | `openIncentive` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:30` |
| ↻我的捞取记录› | `openClaims` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:31` |
| ⚿修改密码› | `openPassword` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:34` |
| ◇数据权限说明› | `openScope` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:35` |
| ◌微信绑定 | `openWechat` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:39` |
| □清除缓存› | `clearCache` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:40` |
| ⓘ关于我们› | `openAbout` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:41` |
| ▤用户协议› | `openAgreement` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:42` |
| ▧隐私政策› | `openPrivacy` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:43` |
| 意见反馈 | `openFeedback` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:47` |
| 退出登录 | `logout` | `` | `aigicrm-miniprogram/pages/profile/index.wxml:48` |
| (button) | `submit` | `` | `aigicrm-miniprogram/pages/profile/password/index.wxml:11` |
| 查看接入说明 | `bindWechat` | `` | `aigicrm-miniprogram/pages/profile/wechat/index.wxml:7` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/ranking/detail/index.wxml:8` |
| (button) | `onMemberTap` | `` | `aigicrm-miniprogram/pages/ranking/detail/index.wxml:33` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:5` |
| (button) | `switchMainTab` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:20` |
| (button) | `switchScoreTab` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:40` |
| 分 | `openScoreDetail` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:44` |
| (button) | `switchTab` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:74` |
| . 分 激励类型 条数 得分 合计 | `openDetail` | `` | `aigicrm-miniprogram/pages/ranking/index.wxml:79` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:8` |
| 待处理 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:13` |
| 到期客户 | `switchView` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:14` |
| 查询 | `search` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:19` |
| 一键清空 | `processAll` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:31` |
| 详情 | `openDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:50` |
| 客户档案 | `openCustomer` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:51` |
| 申请延期 | `extend` | `{{item.canExtend}}` | `aigicrm-miniprogram/pages/reminder/index.wxml:52` |
| (button) | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:53` |
| 关闭 | `closeDetail` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:70` |
| 查看客户完整档案 | `openSelectedCustomer` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:91` |
| 申请延期 | `extend` | `{{selectedReminder.canExtend}}` | `aigicrm-miniprogram/pages/reminder/index.wxml:92` |
| (button) | `process` | `` | `aigicrm-miniprogram/pages/reminder/index.wxml:93` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/sea/claim-detail/index.wxml:8` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:8` |
| 搜索 | `searchTap` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:16` |
| 编号 原负责人 入池原因 入池时间 可操作权限： | `openCustomer` | `` | `aigicrm-miniprogram/pages/sea/index.wxml:29` |
| 捞取 | `claim` | `{{item.canClaimNow}}` | `aigicrm-miniprogram/pages/sea/index.wxml:40` |
| 编辑 | `editSeaCustomer` | `{{item.canEdit}}` | `aigicrm-miniprogram/pages/sea/index.wxml:41` |
| 删除 | `deleteSeaCustomer` | `{{item.canDeleteNow}}` | `aigicrm-miniprogram/pages/sea/index.wxml:42` |
| 最新申请 | `openClaimDetail` | `{{seaView.showLatestClaim}}` | `aigicrm-miniprogram/pages/sea/index.wxml:51` |
| 返回 | `goBack` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:9` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:10` |
| (button) | `switchSection` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:14` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/detail/index.wxml:58` |
| (button) | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:6` |
| 展开 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:65` |
| ↻ | `refresh` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:72` |
| 明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:79` |
| 客户 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:116` |
| 分布 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:129` |
| 趋势 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:148` |
| 排行 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:162` |
| 明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:174` |
| 全部 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:179` |
| 查看统计明细 | `openDetail` | `` | `aigicrm-miniprogram/pages/statistics/index.wxml:190` |

## PC API Functions

| Function | Method | Path | Source |
| --- | --- | --- | --- |
| `getApprovalList` | `GET` | `/approval/list` | `aigicrm-ui/src/api/approval.js` |
| `getApprovalDetail` | `GET` | `/approval/${id}` | `aigicrm-ui/src/api/approval.js` |
| `submitApproval` | `POST` | `/approval/submit` | `aigicrm-ui/src/api/approval.js` |
| `approve` | `POST` | `/approval/${id}/approve` | `aigicrm-ui/src/api/approval.js` |
| `reject` | `POST` | `/approval/${id}/reject` | `aigicrm-ui/src/api/approval.js` |
| `getApprovalStatistics` | `GET` | `/approval/statistics` | `aigicrm-ui/src/api/approval.js` |
| `transferApprovalNode` | `PUT` | `/approval/${approvalId}/nodes/${nodeId}/transfer` | `aigicrm-ui/src/api/approval.js` |
| `getApprovalConfigList` | `GET` | `/approval/config/list` | `aigicrm-ui/src/api/approval.js` |
| `createApprovalConfig` | `POST` | `/approval/config` | `aigicrm-ui/src/api/approval.js` |
| `updateApprovalConfig` | `PUT` | `/approval/config/${id}` | `aigicrm-ui/src/api/approval.js` |
| `deleteApprovalConfig` | `DELETE` | `/approval/config/${id}` | `aigicrm-ui/src/api/approval.js` |
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
| `saveCustomerWithIndicator` | `POST` | `/customer/full` | `aigicrm-ui/src/api/customer.js` |
| `updateCustomerWithIndicator` | `PUT` | `/customer/${id}/full` | `aigicrm-ui/src/api/customer.js` |
| `getStageAmounts` | `GET` | `/customer/${id}/stage-amounts` | `aigicrm-ui/src/api/customer.js` |
| `saveStageAmount` | `POST` | `/customer/${id}/stage-amounts` | `aigicrm-ui/src/api/customer.js` |
| `deleteCustomer` | `DELETE` | `/customer/${id}` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerLogs` | `GET` | `/customer/${id}/logs` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerEvents` | `GET` | `/customer/${id}/events` | `aigicrm-ui/src/api/customer.js` |
| `addCustomerLog` | `POST` | `/customer/${id}/logs` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerStatistics` | `GET` | `/customer/statistics` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerListCounts` | `GET` | `/customer/list/counts` | `aigicrm-ui/src/api/customer.js` |
| `getCustomerListCountsByFilter` | `GET` | `/customer/list/counts-by-filter` | `aigicrm-ui/src/api/customer.js` |
| `getSimpleUserList` | `GET` | `/customer/users/simple` | `aigicrm-ui/src/api/customer.js` |
| `releaseCustomer` | `POST` | `/customer/${id}/release` | `aigicrm-ui/src/api/customer.js` |
| `getInvoices` | `GET` | `/customer/${id}/invoices` | `aigicrm-ui/src/api/customer.js` |
| `getInvoiceSummary` | `GET` | `/customer/${id}/invoices/summary` | `aigicrm-ui/src/api/customer.js` |
| `archiveCustomer` | `POST` | `/customer/${id}/archive` | `aigicrm-ui/src/api/customer.js` |
| `restoreCustomer` | `POST` | `/customer/${id}/restore` | `aigicrm-ui/src/api/customer.js` |
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
| `importCustomers` | `POST` | `/customer/import` | `aigicrm-ui/src/api/customer.js` |
| `getFolderTree` | `GET` | `/file/folders` | `aigicrm-ui/src/api/file.js` |
| `getFileList` | `GET` | `/file/list` | `aigicrm-ui/src/api/file.js` |
| `createFolder` | `POST` | `/file/folders` | `aigicrm-ui/src/api/file.js` |
| `renameFolder` | `PUT` | `/file/folders/${id}` | `aigicrm-ui/src/api/file.js` |
| `deleteFolder` | `DELETE` | `/file/folders/${id}` | `aigicrm-ui/src/api/file.js` |
| `deleteFile` | `DELETE` | `/file/${id}` | `aigicrm-ui/src/api/file.js` |
| `getStorageInfo` | `GET` | `/file/storage` | `aigicrm-ui/src/api/file.js` |
| `getStorageApplyInfo` | `GET` | `/file/storage/apply-info` | `aigicrm-ui/src/api/file.js` |
| `shareFile` | `POST` | `/file/${fileId}/share` | `aigicrm-ui/src/api/file.js` |
| `getSharedToMe` | `GET` | `/file/shared-to-me` | `aigicrm-ui/src/api/file.js` |
| `getSharedToMeCount` | `GET` | `/file/shared-to-me/count` | `aigicrm-ui/src/api/file.js` |
| `getFolderAcl` | `GET` | `/file/folders/${id}/acl` | `aigicrm-ui/src/api/file.js` |
| `getAclRoles` | `GET` | `/file/folders/acl-roles` | `aigicrm-ui/src/api/file.js` |
| `saveFolderAcl` | `POST` | `/file/folders/${id}/acl` | `aigicrm-ui/src/api/file.js` |
| `removeFolderAcl` | `DELETE` | `/file/folders/${id}/acl/${roleId}` | `aigicrm-ui/src/api/file.js` |
| `setFolderDownloadable` | `PUT` | `/file/folders/${id}/download` | `aigicrm-ui/src/api/file.js` |
| `getFolderPermissions` | `GET` | `/file/folders/${id}/permissions` | `aigicrm-ui/src/api/file.js` |
| `getFolderTypeDefaults` | `GET` | `/file/folders/type-defaults` | `aigicrm-ui/src/api/file.js` |
| `saveFolderTypeDefault` | `POST` | `/file/folders/type-defaults` | `aigicrm-ui/src/api/file.js` |
| `removeFolderTypeDefault` | `DELETE` | `/file/folders/type-defaults/${folderType}/${roleId}` | `aigicrm-ui/src/api/file.js` |
| `replaceFile` | `POST` | `/file/${id}/replace` | `aigicrm-ui/src/api/file.js` |
| `renameFile` | `PUT` | `/file/${id}/rename` | `aigicrm-ui/src/api/file.js` |
| `getCategoryTree` | `GET` | `/incentive/categories` | `aigicrm-ui/src/api/incentive.js` |
| `createCategory` | `POST` | `/incentive/categories` | `aigicrm-ui/src/api/incentive.js` |
| `updateCategory` | `PUT` | `/incentive/categories/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `deleteCategory` | `DELETE` | `/incentive/categories/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `getRecords` | `GET` | `/incentive/records` | `aigicrm-ui/src/api/incentive.js` |
| `addRecord` | `POST` | `/incentive/records` | `aigicrm-ui/src/api/incentive.js` |
| `deleteRecord` | `DELETE` | `/incentive/records/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `updateRecord` | `PUT` | `/incentive/records/${id}` | `aigicrm-ui/src/api/incentive.js` |
| `removeRecordFile` | `POST` | `/incentive/records/${recordId}/remove-file` | `aigicrm-ui/src/api/incentive.js` |
| `getScoreboard` | `GET` | `/incentive/scoreboard` | `aigicrm-ui/src/api/incentive.js` |
| `getCategoryStats` | `GET` | `/incentive/category-stats` | `aigicrm-ui/src/api/incentive.js` |
| `getScoreByCustomer` | `GET` | `/incentive/score/customer/${customerId}` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveRanking` | `GET` | `/incentive/ranking` | `aigicrm-ui/src/api/incentive.js` |
| `getCustomerRecords` | `GET` | `/incentive/customer-records` | `aigicrm-ui/src/api/incentive.js` |
| `markIncentiveRead` | `POST` | `/incentive/records/${recordId}/read` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveReadStats` | `GET` | `/incentive/read-stats` | `aigicrm-ui/src/api/incentive.js` |
| `getIncentiveReadRanking` | `GET` | `/incentive/read-ranking` | `aigicrm-ui/src/api/incentive.js` |
| `checkIncentivePermission` | `GET` | `/incentive/permission/check` | `aigicrm-ui/src/api/incentive.js` |
| `getDeletedCustomers` | `GET` | `/system/recycle/customers` | `aigicrm-ui/src/api/recycle.js` |
| `getDeletedUsers` | `GET` | `/system/recycle/users` | `aigicrm-ui/src/api/recycle.js` |
| `restoreDeletedCustomer` | `POST` | `/system/recycle/customers/${id}/restore` | `aigicrm-ui/src/api/recycle.js` |
| `restoreDeletedUser` | `POST` | `/system/recycle/users/${id}/restore` | `aigicrm-ui/src/api/recycle.js` |
| `getDeletedFiles` | `GET` | `/system/recycle/files` | `aigicrm-ui/src/api/recycle.js` |
| `restoreDeletedFile` | `POST` | `/system/recycle/files/${id}/restore` | `aigicrm-ui/src/api/recycle.js` |
| `purgeDeletedFiles` | `POST` | `/system/recycle/files/purge` | `aigicrm-ui/src/api/recycle.js` |
| `getDeletedCustomerDetail` | `GET` | `/system/recycle/customers/${id}` | `aigicrm-ui/src/api/recycle.js` |
| `getDeletedUserDetail` | `GET` | `/system/recycle/users/${id}` | `aigicrm-ui/src/api/recycle.js` |
| `getDeletedFileDetail` | `GET` | `/system/recycle/files/${id}` | `aigicrm-ui/src/api/recycle.js` |
| `hardDeleteCustomer` | `POST` | `/system/recycle/customers/${id}/purge` | `aigicrm-ui/src/api/recycle.js` |
| `hardDeleteUser` | `POST` | `/system/recycle/users/${id}/purge` | `aigicrm-ui/src/api/recycle.js` |
| `hardDeleteFile` | `POST` | `/system/recycle/files/${id}/purge` | `aigicrm-ui/src/api/recycle.js` |
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
| `getScoreDeptMembers` | `GET` | `/score/ranking/dept-members` | `aigicrm-ui/src/api/score.js` |
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
| `getScopeUsers` | `GET` | `/common/scope/users` | `aigicrm-ui/src/api/system.js` |
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
| `getAuditLogList` | `GET` | `/system/audit-logs` | `aigicrm-ui/src/api/system.js` |
| `getPermCenterPreview` | `GET` | `/system/perm-center/preview` | `aigicrm-ui/src/api/system.js` |
| `getRuleVersions` | `GET` | `/system/rule-versions` | `aigicrm-ui/src/api/system.js` |
| `getTaskList` | `GET` | `/task/list` | `aigicrm-ui/src/api/task.js` |
| `getTaskStatistics` | `GET` | `/task/statistics` | `aigicrm-ui/src/api/task.js` |
| `createTask` | `POST` | `/task` | `aigicrm-ui/src/api/task.js` |
| `completeTask` | `POST` | `/task/${id}/complete` | `aigicrm-ui/src/api/task.js` |
| `cancelTask` | `POST` | `/task/${id}/cancel` | `aigicrm-ui/src/api/task.js` |
| `transferTask` | `POST` | `/task/${id}/transfer` | `aigicrm-ui/src/api/task.js` |

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
| `logout` | `POST` | `/api/auth/logout` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordLookup` | `POST` | `/api/auth/forgot-password/lookup` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordSendCode` | `POST` | `/api/auth/forgot-password/send-code` | `aigicrm-miniprogram/api/auth.js` |
| `forgotPasswordReset` | `POST` | `/api/auth/forgot-password/reset` | `aigicrm-miniprogram/api/auth.js` |
| `getCustomerList` | `GET` | `/api/customer/list` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomer` | `GET` | `/api/customer/' + id` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerCounts` | `GET` | `/api/customer/list/counts` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomer` | `POST` | `/api/customer/add` | `aigicrm-miniprogram/api/customer.js` |
| `updateCustomer` | `PUT` | `/api/customer/' + id` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerWithIndicator` | `POST` | `/api/customer/full` | `aigicrm-miniprogram/api/customer.js` |
| `updateCustomerWithIndicator` | `PUT` | `/api/customer/' + id + '/full` | `aigicrm-miniprogram/api/customer.js` |
| `deleteCustomer` | `DELETE` | `/api/customer/' + id` | `aigicrm-miniprogram/api/customer.js` |
| `checkCustomerName` | `GET` | `/api/customer/check-name` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerContacts` | `GET` | `/api/customer/' + id + '/contacts` | `aigicrm-miniprogram/api/customer.js` |
| `addCustomerContact` | `POST` | `/api/customer/' + id + '/contacts` | `aigicrm-miniprogram/api/customer.js` |
| `updateCustomerContact` | `PUT` | `/api/customer/' + id + '/contacts/' + contactId` | `aigicrm-miniprogram/api/customer.js` |
| `deleteCustomerContact` | `DELETE` | `/api/customer/' + id + '/contacts/' + contactId` | `aigicrm-miniprogram/api/customer.js` |
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
| `deleteInvoice` | `DELETE` | `/api/customer/' + id + '/invoices/' + invoiceId + '?reason=' + encodeURIComponent(reason || '')` | `aigicrm-miniprogram/api/customer.js` |
| `addPayment` | `POST` | `/api/customer/' + id + '/payments` | `aigicrm-miniprogram/api/customer.js` |
| `deletePayment` | `DELETE` | `/api/customer/' + id + '/payments/' + paymentId + '?reason=' + encodeURIComponent(reason || '')` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerIndicator` | `GET` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerIndicator` | `POST` | `/api/customer/' + id + '/indicator` | `aigicrm-miniprogram/api/customer.js` |
| `getCustomerStageAmounts` | `GET` | `/api/customer/' + id + '/stage-amounts` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerStageAmount` | `POST` | `/api/customer/' + id + '/stage-amounts` | `aigicrm-miniprogram/api/customer.js` |
| `saveCustomerRelationship` | `PUT` | `/api/customer/' + id + '/relationship` | `aigicrm-miniprogram/api/customer.js` |
| `releaseCustomer` | `POST` | `/api/customer/' + id + '/release` | `aigicrm-miniprogram/api/customer.js` |
| `archiveCustomer` | `POST` | `/api/customer/' + id + '/archive` | `aigicrm-miniprogram/api/customer.js` |
| `getFormDraft` | `GET` | `/api/draft/' + draftType` | `aigicrm-miniprogram/api/draft.js` |
| `saveFormDraft` | `PUT` | `/api/draft/' + draftType` | `aigicrm-miniprogram/api/draft.js` |
| `forceSaveFormDraft` | `PUT` | `/api/draft/' + draftType + '/force` | `aigicrm-miniprogram/api/draft.js` |
| `deleteFormDraft` | `DELETE` | `/api/draft/' + draftType` | `aigicrm-miniprogram/api/draft.js` |
| `encodeDownloadPath` | `GET` | `/api/file/list` | `aigicrm-miniprogram/api/file.js` |
| `getStorageInfo` | `GET` | `/api/file/storage` | `aigicrm-miniprogram/api/file.js` |
| `getStorageApplyInfo` | `GET` | `/api/file/storage/apply-info` | `aigicrm-miniprogram/api/file.js` |
| `getDownloadToken` | `GET` | `/api/file/download-token` | `aigicrm-miniprogram/api/file.js` |
| `getFolders` | `GET` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `createFolder` | `POST` | `/api/file/folders` | `aigicrm-miniprogram/api/file.js` |
| `renameFolder` | `PUT` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `deleteFolder` | `DELETE` | `/api/file/folders/' + id` | `aigicrm-miniprogram/api/file.js` |
| `deleteFile` | `DELETE` | `/api/file/' + id` | `aigicrm-miniprogram/api/file.js` |
| `renameFile` | `PUT` | `/api/file/' + id + '/rename` | `aigicrm-miniprogram/api/file.js` |
| `getSharedToMe` | `GET` | `/api/file/shared-to-me` | `aigicrm-miniprogram/api/file.js` |
| `getSharedToMeCount` | `GET` | `/api/file/shared-to-me/count` | `aigicrm-miniprogram/api/file.js` |
| `shareFile` | `POST` | `/api/file/' + id + '/share` | `aigicrm-miniprogram/api/file.js` |
| `getCustomerRecords` | `GET` | `/api/incentive/customer-records` | `aigicrm-miniprogram/api/incentive.js` |
| `getRecords` | `GET` | `/api/incentive/records` | `aigicrm-miniprogram/api/incentive.js` |
| `getCategoryTree` | `GET` | `/api/incentive/categories` | `aigicrm-miniprogram/api/incentive.js` |
| `addRecord` | `POST` | `/api/incentive/records` | `aigicrm-miniprogram/api/incentive.js` |
| `markIncentiveRead` | `POST` | `/api/incentive/records/' + recordId + '/read` | `aigicrm-miniprogram/api/incentive.js` |
| `getReadStats` | `GET` | `/api/incentive/read-stats` | `aigicrm-miniprogram/api/incentive.js` |
| `getCategoryStats` | `GET` | `/api/incentive/category-stats` | `aigicrm-miniprogram/api/incentive.js` |
| `getReadRanking` | `GET` | `/api/incentive/read-ranking` | `aigicrm-miniprogram/api/incentive.js` |
| `getScoreboard` | `GET` | `/api/incentive/scoreboard` | `aigicrm-miniprogram/api/incentive.js` |
| `getScoreByCustomer` | `GET` | `/api/incentive/score/customer/' + customerId` | `aigicrm-miniprogram/api/incentive.js` |
| `getRanking` | `GET` | `/api/incentive/ranking` | `aigicrm-miniprogram/api/incentive.js` |
| `getUsers` | `GET` | `/api/incentive/users` | `aigicrm-miniprogram/api/incentive.js` |
| `getGrantCheck` | `GET` | `/api/incentive/permission/check` | `aigicrm-miniprogram/api/incentive.js` |
| `updateRecord` | `PUT` | `/api/incentive/records/' + id` | `aigicrm-miniprogram/api/incentive.js` |
| `removeRecordFile` | `POST` | `/api/incentive/records/' + recordId + '/remove-file` | `aigicrm-miniprogram/api/incentive.js` |
| `deleteRecord` | `DELETE` | `/api/incentive/records/' + id` | `aigicrm-miniprogram/api/incentive.js` |
| `getReminderList` | `GET` | `/api/reminder/list` | `aigicrm-miniprogram/api/reminder.js` |
| `getExpiringList` | `GET` | `/api/reminder/expiring-list` | `aigicrm-miniprogram/api/reminder.js` |
| `ensureExpiring` | `POST` | `/api/reminder/ensure-expiring` | `aigicrm-miniprogram/api/reminder.js` |
| `processReminder` | `POST` | `/api/reminder/' + id + '/process` | `aigicrm-miniprogram/api/reminder.js` |
| `getPendingCount` | `GET` | `/api/reminder/pending-count` | `aigicrm-miniprogram/api/reminder.js` |
| `getScoreRanking` | `GET` | `/api/score/ranking` | `aigicrm-miniprogram/api/score.js` |
| `getRankingStats` | `GET` | `/api/score/ranking/stats` | `aigicrm-miniprogram/api/score.js` |
| `getScoreGrid` | `GET` | `/api/score/grid` | `aigicrm-miniprogram/api/score.js` |
| `getScoreDeptMembers` | `GET` | `/api/score/ranking/dept-members` | `aigicrm-miniprogram/api/score.js` |
| `getSeaList` | `GET` | `/api/sea/list` | `aigicrm-miniprogram/api/sea.js` |
| `getSeaStatistics` | `GET` | `/api/sea/statistics` | `aigicrm-miniprogram/api/sea.js` |
| `getVisibleSeaConfigs` | `GET` | `/api/sea/configs/visible` | `aigicrm-miniprogram/api/sea.js` |
| `getClaimRecords` | `GET` | `/api/sea/claim-records` | `aigicrm-miniprogram/api/sea.js` |
| `deleteSeaPool` | `DELETE` | `/api/sea/pool/' + id` | `aigicrm-miniprogram/api/sea.js` |
| `getIndicatorStats` | `GET` | `/api/statistics/indicator-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getDashboardData` | `GET` | `/api/statistics/dashboard` | `aigicrm-miniprogram/api/statistics.js` |
| `getAnalysis` | `GET` | `/api/statistics/analysis` | `aigicrm-miniprogram/api/statistics.js` |
| `getTeamActivities` | `GET` | `/api/statistics/team-activities` | `aigicrm-miniprogram/api/statistics.js` |
| `getDailyStats` | `GET` | `/api/statistics/daily-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getWeeklyStats` | `GET` | `/api/statistics/weekly-stats` | `aigicrm-miniprogram/api/statistics.js` |
| `getCustomerRankings` | `GET` | `/api/statistics/customer-rankings` | `aigicrm-miniprogram/api/statistics.js` |
| `getScopeDepts` | `GET` | `/api/common/scope/depts` | `aigicrm-miniprogram/api/system.js` |
| `getDepts` | `GET` | `/api/system/depts` | `aigicrm-miniprogram/api/system.js` |
| `getDictData` | `GET` | `/api/system/dict/data/' + dictCode` | `aigicrm-miniprogram/api/system.js` |
| `getScopeUsers` | `GET` | `/api/common/scope/users` | `aigicrm-miniprogram/api/system.js` |
| `getTaskStatistics` | `GET` | `/api/task/statistics` | `aigicrm-miniprogram/api/task.js` |
| `getTaskList` | `GET` | `/api/task/list` | `aigicrm-miniprogram/api/task.js` |

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
| `PUT` | `/api/approval/{id}/nodes/{nodeId}/transfer` | `hasAuthority('approval:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java` |
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
| `GET` | `/api/auth/permission-explain` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/PermissionExplainController.java` |
| `GET` | `/api/draft/{draftType}` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/FormDraftController.java` |
| `PUT` | `/api/draft/{draftType}` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/FormDraftController.java` |
| `PUT` | `/api/draft/{draftType}/force` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/FormDraftController.java` |
| `DELETE` | `/api/draft/{draftType}` | `isAuthenticated()` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/FormDraftController.java` |
| `GET` | `/api/common/scope/users` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/ScopeController.java` |
| `GET` | `/api/common/scope/depts` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/ScopeController.java` |
| `GET` | `/api/customer/list` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/list/counts-by-filter` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/list/counts` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/group-names` | `hasAuthority('customer:add') or hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/rankings` | `hasAuthority('score:view') or hasAuthority('statistics:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/add` | `hasAuthority('customer:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/full` | `hasAuthority('customer:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `PUT` | `/api/customer/{id}/full` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/archive` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/restore` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `DELETE` | `/api/customer/{id}` | `hasAuthority('customer:delete')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/logs` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/events` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/logs` | `hasAuthority('customer:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/statistics` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/invoices/summary` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `GET` | `/api/customer/{id}/stage-amounts` | `hasAuthority('customer:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
| `POST` | `/api/customer/{id}/stage-amounts` | `hasAuthority('finance:manage') or hasAuthority('customer:finance:edit') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java` |
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
| `PUT` | `/api/file/{id}/rename` | `hasAuthority('file:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/storage` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/storage/apply-info` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/{id}` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/public/**` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/download/**` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `POST` | `/api/file/{id}/share` | `hasAuthority('file:share')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/shared-to-me` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/shared-to-me/count` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java` |
| `GET` | `/api/file/reconciliation` | `hasAuthority('file:folder:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileReconciliationController.java` |
| `GET` | `/api/file/folders/{id}/acl` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `POST` | `/api/file/folders/{id}/acl` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `DELETE` | `/api/file/folders/{id}/acl/{roleId}` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `PUT` | `/api/file/folders/{id}/download` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/file/folders/{id}/permissions` | `hasAuthority('file:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/file/folders/acl-roles` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/file/folders/type-defaults` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `POST` | `/api/file/folders/type-defaults` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `DELETE` | `/api/file/folders/type-defaults/{folderType}/{roleId}` | `hasAuthority('file:acl:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java` |
| `GET` | `/api/incentive/categories` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/categories` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `PUT` | `/api/incentive/categories/{id}` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `DELETE` | `/api/incentive/categories/{id}` | `hasAuthority('incentive:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/records` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records` | `hasAuthority('incentive:add')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `PUT` | `/api/incentive/records/{id}` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records/{id}/remove-file` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `DELETE` | `/api/incentive/records/{id}` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/score/customer/{customerId}` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/scoreboard` | `hasAnyAuthority('incentive:stats:view','incentive:rank:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/ranking` | `hasAnyAuthority('incentive:stats:view','incentive:rank:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/customer-records` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `POST` | `/api/incentive/records/{id}/read` | `hasAuthority('incentive:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/read-stats` | `hasAuthority('incentive:read-stats')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/read-ranking` | `hasAuthority('incentive:read-ranking:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
| `GET` | `/api/incentive/category-stats` | `hasAuthority('incentive:stats:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java` |
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
| `GET` | `/api/score/ranking/dept-members` | `hasAuthority('score:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
| `GET` | `/api/score/export/grid` | `hasAuthority('score:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java` |
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
| `GET` | `/api/system/perm-center/preview` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/PermCenterController.java` |
| `GET` | `/api/system/recycle/customers` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/customers/{id}` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/customers/{id}/restore` | `hasAuthority('system:recycle:restore')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/customers/{id}/purge` | `hasAuthority('system:recycle:purge')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/users` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/users/{id}` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/users/{id}/restore` | `hasAuthority('system:recycle:restore')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/users/{id}/purge` | `hasAuthority('system:recycle:purge')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/files` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/files/{id}` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/recycle/files/{id}/content` | `hasAuthority('system:recycle:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/files/{id}/restore` | `hasAuthority('system:recycle:restore')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/files/{id}/purge` | `hasAuthority('system:recycle:purge')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `POST` | `/api/system/recycle/files/purge` | `hasAuthority('system:recycle:purge')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java` |
| `GET` | `/api/system/perm-scopes/{roleCode}` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `PUT` | `/api/system/perm-scopes/{roleCode}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `GET` | `/api/system/perm-scopes/assignee/{type}/{key}` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `PUT` | `/api/system/perm-scopes/assignee/{type}/{key}` | `hasAuthority('system:config:edit')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RolePermScopeController.java` |
| `GET` | `/api/system/audit-logs` | `hasAuthority('system:log:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysAuditLogController.java` |
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
| `GET` | `/api/system/rule-versions` | `hasAuthority('system:config:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysRuleVersionController.java` |
| `GET` | `/api/system/users` | `hasAnyAuthority('system:user:view','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `GET` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:view','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users` | `hasAnyAuthority('system:user:add','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `PUT` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:edit','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users/{id}/reset-password` | `hasAnyAuthority('system:user:edit','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `POST` | `/api/system/users/change-password` | `` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `DELETE` | `/api/system/users/{id}` | `hasAnyAuthority('system:user:delete','system:org:admin')` | `aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java` |
| `GET` | `/api/task/list` | `hasAuthority('task:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `GET` | `/api/task/statistics` | `hasAuthority('task:view')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `POST` | `/api/task` | `hasAuthority('task:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `PUT` | `/api/task/{id}` | `hasAuthority('task:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `POST` | `/api/task/{id}/complete` | `hasAuthority('task:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `POST` | `/api/task/{id}/cancel` | `hasAuthority('task:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `POST` | `/api/task/{id}/transfer` | `hasAuthority('task:manage')` | `aigicrm-backend/src/main/java/com/aigicrm/module/task/controller/TaskController.java` |
| `POST` | `/api/system/task/check-expire` | `` | `aigicrm-backend/src/main/java/com/aigicrm/task/ScheduledTaskController.java` |

## Local Migration Permission Sources

| Permission | Source |
| --- | --- |
| `approval:view` | approval/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `contact:manage` | 联系人管理 (V2026.09.19.6__permission_separation.sql) |
| `contact:view` | 客户联系人查看 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `customer:add` | customer/add (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `customer:date:edit` | 客户日期编辑 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `customer:finance:edit` | 开票回款编辑 (V2026.11.03.3__customer_finance_edit_permission.sql) |
| `customer:invoice-date:edit` | 最近开票日编辑 (V2026.11.02.3__invoice_date_edit_permission.sql) |
| `customer:view` | customer/list (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `dashboard:view` | dashboard/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `file:acl:manage` | 文件ACL管理 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `file:delete` | 文件删除 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:edit` | 文件编辑 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:folder:manage` | 文件夹管理 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:share` | 文件分享 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:upload` | 文件上传 (V2026.09.11.3__restore_file_mutation_permission_bindings.sql) |
| `file:view` | 文件ACL管理 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `finance:manage` | 财务管理 (V2026.09.19.6__permission_separation.sql) |
| `finance:view` | 客户财务查看 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `incentive:add` | 激励新增 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| `incentive:manage` | 激励管理(他人记录) (V2026.11.08.2__drop_dead_incentive_manage.sql) |
| `incentive:person-rank:export` | 个人排行导出 (V2026.09.23.7__person_rank_export_permission.sql) |
| `incentive:rank:export` | 客户排行导出 (V2026.11.05.8__customer_rank_export_permission.sql) |
| `incentive:rank:view` | 激励排行查看 (V2026.11.04.1__incentive_stats_rank_permission_codes.sql) |
| `incentive:read-ranking:view` | 本月激励查询阅读排行榜 (V2026.09.13.4__add_incentive_read_ranking_menu.sql) |
| `incentive:read-stats` | 激励查询已读统计 (V2026.09.13.1__register_missing_used_permission_codes.sql) |
| `incentive:stats:view` | 激励统计查看 (V2026.11.04.1__incentive_stats_rank_permission_codes.sql) |
| `incentive:view` | 激励新增 (V16__add_sys_user_security_columns_and_menu_permissions.sql) |
| ~~`ranking:customer:view`~~ | 客户排行查看 (V2026.09.11.4 登记; 2026-11-12 裁决A退役, V2026.11.12.1 删行, rankings 端点改 score:view ∨ statistics:view 双码) |
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
| `system:recycle:purge` | 清空文件回收站 (V2026.10.18.2__file_recycle_retention_and_purge.sql) |
| `system:recycle:restore` | 恢复已删除数据 (V2026.10.18.1__recycle_bin_menu_and_permissions.sql) |
| `system:recycle:view` | /system/recycle (V2026.10.18.1__recycle_bin_menu_and_permissions.sql) |
| `system:role:view` | system/role (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:rule:view` | system/rule/index (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `system:user:view` | system/user (V2026.09.11.6__seed_permission_dictionary_and_roles.sql) |
| `task:manage` | 任务管理 (V2026.09.19.8__task_module.sql) |
| `task:view` | 任务查看 (V2026.09.19.8__task_module.sql) |

## Permission Drift Candidates

### Backend Permissions Missing In Dictionary Or Local Migrations

- None

### Frontend Code Permission Tokens Missing In Known Sources

- None

### Runtime Dictionary Permissions Not Directly Seen In Backend Annotations

- `contact:manage`: 客户联系人管理
- `contact:view`: 客户联系人查看
- `customer:date:edit`: 修改客户保护日期
- `incentive:manage`: 激励管理(他人记录)
- `incentive:person-rank:export`: 个人排行导出
- `sea:claim`: 公海捞取

### Runtime Dictionary Permissions Referenced Outside Backend Annotations

- `contact:view`: 客户联系人查看
- `customer:date:edit`: 修改客户保护日期
- `sea:claim`: 公海捞取

### Runtime Dictionary Permissions Not Seen In Backend Annotations Or Code References

- `contact:manage`: 客户联系人管理
- `incentive:manage`: 激励管理(他人记录)
- `incentive:person-rank:export`: 个人排行导出

### Compatibility Or Deprecated Dictionary Permissions Excluded From Runtime Drift

- `customer:share`: 历史兼容项, 不作为独立运行时授权(沿用客户访问/编辑守卫)
- `incentive:delete`: 历史兼容项, 不作为独立运行时授权(由授权表与服务范围控制)
- `incentive:view-all`: 激励查看(全部)（历史残留, 不作为运行时授权(改由授权表按人核定)）
- `incentive:view-shared`: 激励查看(已分享)（历史残留, 不作为运行时授权(改由授权表按人核定)）
- `score:ranking`: 历史残留, 不作为独立运行时授权(统一使用 score:view)
- `sea:pool:delete`: 历史残留, 不作为独立运行时授权(运行时由池级 PERM_DELETE 守卫控制)
- `statistics:scoreboard`: 历史残留, 不作为独立运行时授权(统一使用 statistics:view)
- `system:menu:edit`: 历史残留, 不作为运行时授权(菜单由迁移维护)

### Backend Code References For Dictionary-Only Permissions

| Permission | Code Reference | Context |
| --- | --- | --- |
| `contact:view` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java:467` | `if (!SecurityUtils.hasAuthority("contact:view")) return Result.ok(List.of());` |
| `customer:date:edit` | `aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java:731` | `return SecurityUtils.isAdmin() \|\| SecurityUtils.hasAuthority("customer:date:edit");` |
| `sea:claim` | `aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java:286` | `&& !SecurityUtils.hasAuthority("sea:claim")) {` |

### UI Permission Tokens Missing In Known Sources

- None

## API Drift Candidates

### PC API Wrappers Without Backend Endpoint Match

- None

### Mini Program API Wrappers Without Backend Endpoint Match

- `POST /api/customer/{id}/invoices` from `aigicrm-miniprogram/api/customer.js` (`addInvoice`)
- `DELETE /api/customer/{id}/invoices/{id}?reason={id}(reason || '')` from `aigicrm-miniprogram/api/customer.js` (`deleteInvoice`)
- `POST /api/customer/{id}/payments` from `aigicrm-miniprogram/api/customer.js` (`addPayment`)
- `DELETE /api/customer/{id}/payments/{id}?reason={id}(reason || '')` from `aigicrm-miniprogram/api/customer.js` (`deletePayment`)

## Notes

- A dictionary permission can be valid even when it is not directly seen in annotations if enforcement happens in service-layer checks.
- Compatibility/deprecated dictionary rows are kept visible but excluded from runtime drift alerts when the permission dictionary documents them as non-runtime authorization.
- A backend permission missing in both the generated dictionary and local Flyway sys_menu migrations is high-risk because users may not be able to receive that authority through normal role-menu binding.
- This report is local-source based. For production sign-off, regenerate from live database configuration.
