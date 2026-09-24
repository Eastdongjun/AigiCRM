# 审批工作流

> 来源锚点:`ApprovalService.submit/approve/reject/applyApprovalAction/transferNode`、`ApprovalConfigService.createNodes`。核实日期 2026-09-24。

## 1. 单据模型

- 单号 `P{年份}-NNN`(按租户分组取号,如 P2026-079)。
- 状态机:`reviewing(审批中) → approved / rejected`;节点实例状态 `pending / waiting / ...`。
- 单据携带:`applicant_id`、`customer_id`、`action_after_approve`(通过后动作)、`extra_data`(动作参数 JSON)。

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
| `storage` 存储配额 | 调整申请人配额 | extraData.quota |

## 5. 提交侧校验(以 claim 为例)

- 客户必须在公海(available/pending_approval)且本人有池级捞取权限;非 all 范围用户必须**可见**该池(防绕过前端捞不可见池客户)。
- pending_approval 状态=他人已在申请中,拒绝。
- 审批中的转移单,接收人失效会在**审批人端**报错回滚(提交侧已前置拦截,执行侧为最终防线)。

## 6. 数据范围

- 审批列表按 申请人/审批人/组织 范围可见;statistics 接口按范围聚合。
