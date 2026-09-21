# IDOR 对象级权限审计(审计产出 2/4,2026-09-21)

> 65 个按 ID 端点全量核查(customer 31/file 13/incentive 6/approval 4/task 4/sea 3/score 2/reminder 2)。
> 结论: 守卫完整 53(81.5%) / 风险 5 / 全局配置类可接受 7。
> 基建结论: 租户拦截器 MybatisPlusConfig.java:35-42 白名单 24 表,生产未开启则拒绝启动(L63-66),admin 跳过租户条件(L126)。**biz_task 不在租户白名单**【2026-09-21 已修复: G2 批次进白名单+列守卫迁移, 见 05 裁定】; biz_incentive_read/grant 仍不在白名单(靠手工集团过滤, 待评估)。

## 风险清单(重点)

### R1 [P1] 客户财务确认/作废 4 端点无对象归属校验
- 位置: CustomerController.java:251(confirm)/259(void)/268(payment confirm)/276(payment void) → CustomerService.java:1195-1264
- 现象: 服务层仅校验 invoice.customerId==路径id,**未调 accessService.assertCanEdit(customerId)**;同批的 deleteInvoice(CC:287)/deletePayment(CC:443)有校验——属遗漏非设计
- 影响: 持有 customer:edit+finance:view 的用户可对**数据范围外任意同租户客户**的发票/回款执行确认/作废,污染财务台账
- 修复: 4 端点补 assertCanEdit(与 delete 对齐);回归: 范围外客户 confirm → 403

### R2 [P1] task 列表参数型越权 + task 模块无租户隔离
- TaskService.page:147-151: assigneeId==null 才默认自己,显式传他人 id 即可读他人任务(title/detail/customerName)
- TaskService.statistics:172-174 接受任意 userId(计数泄露,低危)
- **biz_task 不在租户白名单 → task 模块完全无集团隔离**(跨集团任务可读)
- 修复: 非-admin 时强制 assigneeId∈可见集合;biz_task 补进租户白名单(需评估现有数据 group_id 列)

### R3 [低] /api/incentive/score/customer/{customerId} 未校验客户可见性(IncentiveService:763-770,仅聚合本人可见记录分数)
### R4 [低] /api/file/folders/{id}/permissions 无 requireEdit(FolderAclService:105-111,仅泄露权限元数据)

## 特别核查项(16 项关注之 #4/#12 静态结论,与行为实测一致)
- markRead: **已修复/无IDOR** — IncentiveService:992-1000 写前 canViewRecord(L1007-1031): 不存在→false;非admin且tenant≠当前→false;统一404防探测。行为实测: 他人记录→404 ✓
- file 下载链(download/{id}/thumbnail/download-token/share/acl): **守卫完整** — canDownloadFile(FileService:504,800-818)先于缩略图分支;token绑定userId+scope限下载路径(JwtAuthFilter:136-153);share有P_SHARE/上传者校验(845-861);acl系requireEdit(FolderAclService:113-118)。行为实测: 他人私有文件→403 ✓
- approval: 审批人**不能**越看业务对象 — canViewApproval(申请∨节点∨数据范围,ApprovalService:186-194);approve/reject 限本人 pending 节点(375-381);转移后客户有集团边界校验(683-692)

## 守卫完整的关键路径(证据索引)
- customer 族: CustomerAccessService 三断言 assertCanView(48-77)/assertCanEdit(80-98)/assertCanDelete(116-131),31 端点中 27 个覆盖(缺口即 R1)
- incentive PUT/DELETE records: Controller 内 admin∨canEdit∨本人(IC:85-93,111-120)+服务层拦 customerId 变更(IncentiveService:365-369)——"view码+方法内守卫"设计属实且闭环
- sea deletePool: 池 PERM_DELETE+可见池二次校验(SeaService:253-276)
- reminder/task 单对象: assertVisible(ReminderService:294-299)/getOwnOrManaged(TaskService:195-204)
