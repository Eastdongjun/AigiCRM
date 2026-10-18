# 审批工作流

> 来源锚点:`ApprovalService.submit/approve/reject/executeApprovalAction/transferNode`、`ApprovalConfigService.createNodes`、`SysUserService.applyStorageQuota`。核实日期 2026-10-18。

## 1. 单据模型

- 单号 `P{年份}-NNN`(按租户分组取号,如 P2026-079)。
- 状态机:`reviewing(审批中) → approved / rejected`;节点实例状态 `pending / waiting / ...`。
- 单据携带:`applicant_id`、`customer_id`、`action_after_approve`(通过后动作)、`extra_data`(动作参数 JSON)。
- **类型白名单(D1=A 2026-10-18)**:`claim / transfer / extend / storage`,提交入口未知类型一律 400,不落无法执行的脏单。

## 2. 审批流解析(提交时定节点)

1. **优先走配置流** `biz_approval_config`(flow_type 匹配、status=1):多行=**串行阶段**,首阶段节点 pending、后续 waiting;阶段内支持**或签/会签**(signMode 落节点)。
2. 无配置时走**兜底流** `biz_approval_flow`:按角色+**直属领导**(沿部门树向上找 leader,最多 5 层防循环)。
3. **配置了流程但解析不出任何有效审批人 → 拒绝提交并回滚**(PR-03:不产生无人可办的"审批中"假成功单据)。

## 3. 审批人操作

- 批准 `approval:approve` / 驳回 `approval:reject`:仅当前节点审批人可操作。
- 节点转交 `transferNode`:**仅 admin**;终态单据不可转交。

## 4. 审批通过后的执行动作(applyApprovalAction)

| 类型 | 动作 | 关键规则 |
| --- | --- | --- |
| `claim` 公海捞取 | `completeClaim` 按 customerId 定位池内行 | 提交时已原子锁池行;执行时客户已删会产生悬挂回写保护 |
| `extend` 保护期延期 | 累加 `customer.extend.days` 并按唯一公式重算到期日 | 见 [customer-lifecycle.md](customer-lifecycle.md) §4 |
| `transfer` 客户转移 | owner/部门/转移日落库 | 双重校验(提交时+执行时):接收人在册/启用/未删+同集团,admin 豁免;解析失败显式报错随事务回滚,不静默成功 |
| `storage` 存储配额 | `SysUserService.applyStorageQuota` 调整申请人配额 | 锁申请人行重读当前配额,只升不降+上限复核;extraData=`{quota:new, oldQuota:old, reason}`;并发导致 new<=current 时明确拒绝并回滚 |

### 4.1 storage 存储空间申请(D1=A 2026-10-18)

- 申请资格:系统管理员(role_code=`admin`)始终可申请;非管理员须系统设置 `file.storage.apply.enabled=true` 且 userId 在 `file.storage.apply.users` JSON 数组中。详见 [file-center.md](file-center.md) §3。
- 提交 body:`{"type":"storage","extraData":"{\"quota\":<新总配额bytes>,\"reason\":\"...\"}"}`;不关联客户(storage 单忽略 customerId)。
- 提交侧:校验账户在册/启用、申请资格、quota 为正整数 bytes、**必须大于当前配额**、不超过 `file.storage.max.quota` 生效上限(默认 100GB,钳制 [1GB,1TB]);理由 trim 后 1-200 字;同一申请人仅允许一张 reviewing/pending 单。
- 落单前**忽略客户端 oldQuota**,从库重读当前配额,把 extraData 规范化为 `{"quota":new,"oldQuota":old,"reason":"..."}`。
- 审批流:`biz_approval_config` 预置 `flow_type='storage'`、审批人=admin 角色、ANY 或签;管理员可调整。旧 `biz_approval_flow` 行保留但新 config 优先。
- 执行侧:锁申请人行(`SELECT ... FOR UPDATE`)重读当前配额,复核只升不降与上限后更新;并写 `STORAGE_QUOTA_CHANGED` 审计(申请人/操作人/old/new/approvalId),审计失败不阻断业务。
- 首版**只允许调高**,`allowDecrease=false`;降配不在本版本开放。

## 5. 提交侧校验(以 claim 为例)

- 客户必须在公海(available/pending_approval)且本人有池级捞取权限;非 all 范围用户必须**可见**该池(防绕过前端捞不可见池客户)。
- pending_approval 状态=他人已在申请中,拒绝。
- 审批中的转移单,接收人失效会在**审批人端**报错回滚(提交侧已前置拦截,执行侧为最终防线)。

## 6. 数据范围

- 审批列表按 申请人/审批人/组织 范围可见;statistics 接口按范围聚合。
