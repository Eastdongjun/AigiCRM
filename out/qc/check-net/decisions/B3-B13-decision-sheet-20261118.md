# B3-B13 口径决策单（2026-11-18，QC 建议稿）

> 用途：把登记册 B 类（口径未定）做成可批量拍板的决策单。**未裁决项不判缺陷、不销账**（B 类原则）。
> 已拍板：B1 数据范围优先级（2026-11-18，用户）、B2 法布施语义（阶段 A）。

| # | 口径问题 | 现状事实（只读核） | 选项 | QC 建议 | 不决的后果 |
|---|---|---|---|---|---|
| B3 | 公海：掉海条件/保护期/捞取上限/重复捞取 | `biz_sea_config` 3 个集团配置（各含部门/角色/可见范围）；掉海由 `CustomerExpireTask` 按 `sea.claim.lock.days`（默认 3）释放锁；保护期字段在配置表 | ①按现配置冻结为基线 ②出具规则文档并回归 | ①+②：先冻结现有 3 集团配置为基线，规则文档由产品补 | 公海链 L3 无法闭环，测试无判据 |
| B4 | 激励：period 口径/排行/积分计算 | `biz_incentive_record.period` 存字符串；排行读 `biz_incentive_read`（1655 行）；积分表 `biz_score_*` | ①定义 period 格式与跨月规则 ②排行并列/最小单量规则 | 产品给 period 字典；QC 按字典对账 | 激励/统计对账无参照，D4 无法判 |
| B5 | 客户：阶段金额/五项指标/财务审计口径 | `biz_customer_stage_amount` 存在；`customer.core.minor/major.wan` 配置（50/100 万）；发票 `biz_invoice` 282 行 | ①阶段金额谁可写/何时锁 ②五项指标定义 ③财务字段审计粒度 | 出财务字段口径表（读写点+审计） | 财务数据正确性无法判定 |
| B6 | 审批：自审自批/代理/转办/超时 | `biz_approval_config` 4 类：claim→USER[11]、storage→ROLE[1]、handover→ROLE[10]、org_move→ROLE[10] | ①禁止自审自批？②代理/转办是否需要 ③超时策略 | ①禁止自审（加守卫）②代理/转办列为后续 ③超时=锁释放（已有天数配置） | 审批链 L4 边界缺陷无法判 |
| B7 | 离职交接范围 | `LifecycleService` 有 handover 审批类型；转移字段 transferIncentives/transferReports（LifecycleController 参数） | ①客户+文件+激励+审批单全转 ②部分转 | 全转（与现有参数一致） | 交接遗漏属数据丢失风险 |
| B8 | 组织移动后历史数据归属 | `OrgAdminService`/`SysDeptService` 有组织移动；`KernelOrgSyncService` 任职闭包重建 | ①历史客户留原部门 ②随人迁移 | 留原部门（范围按数据 owner 而非组织树） | 移动后范围回退无法判 |
| B9 | 回收站保留期/彻底删除/导出 | `FileRecycleCleanupTask` 03:30 跑；保留天数在配置（`file.version.retention-days=180` 是版本，不是回收站）；RecycleExport 有导出 | ①回收站保留 N 天 ②彻底删除权限 | 明确保留天数（如 30 天）+ 彻底删除仅 admin | 合规删除口径不明 |
| B10 | 多集团边界 | 租户插件生产开启（25 表白名单）；当前单集团 | ①保持单集团 fail-closed ②多集团启用路线 | 保持单集团，文档写明"多集团未验证" | 被误认为已支持多集团 |
| B11 | PII 保留/删除/导出合规 | 审计日志含 username/ip 无保留策略（A11）；客户 PII 无字段级删除 | ①保留策略 ②导出脱敏 ③被遗忘权 | 先定保留策略（180 天）+ 导出审计 | 合规风险（P2） |
| B12 | 统计口径（工作台/rank/monthly/team/dept） | `StatisticsQueryService` 多口径；`biz_customer_indicator` 指标表 | ①每口径出具指标字典 ②对账 SQL | 指标字典+QC 对账 SQL 逐口径 | 统计数字对不上无法判 |
| B13 | 法布施审核标准/版权 | 法布施 12 表；内容审核走审批？`biz_fabushi_book.status`（approved）；UGC 评论无审核状态 | ①UGC 先审后发/先发后审 ②版权确认书 | 评论先发后审+举报；书目录入需权属声明 | UGC 合规风险 |

## 建议拍板顺序

1. **B3、B6、B9**（链上可见、影响 L3/L4/L10）——先冻结现状为基线，别改行为；
2. **B5、B12**（财务/统计对账）——需要产品给字典；
3. **B11、B13**（合规）——保留策略与审核口径；
4. **B4、B7、B8、B10** 随后。

## 说明

- 本单只列事实与选项，**不替用户做 B 类决定**；每项拍板后 QC 转不变量+测试并更新矩阵。
- 现状事实基于新快照 `aigicrm_prod_20261118` 只读查询与 HEAD `fd266bc1` 代码。
