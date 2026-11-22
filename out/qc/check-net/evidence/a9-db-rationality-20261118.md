# A9 数据库合理性（新快照 `aigicrm_prod_20261118`，2026-11-18，只读）

> information_schema 只读分析；生产零写入。证据：`ddl-a9.tsv`（data-hygiene 目录）。

## 一、引擎与主键

- 引擎：93 张 BASE TABLE **全部 InnoDB**。
- 无主键表 **1 张**：`sys_role_menu_admin_bak_20261114`——2026-11-14 的 admin 角色菜单**备份表**残留生产库，无主键。建议确认后清理或移出（P3，非功能缺陷）。

## 二、字符集/排序规则（**P2 观察项**）

- `utf8mb4_general_ci`：**57 张**（历史表）；
- `utf8mb4_0900_ai_ci`：**36 张**（MySQL 8 默认，较新迁移）。
- 连接层 `connectionCollation=utf8mb4_general_ci`（application.yml:14）。
- 风险：跨两组的**字符串列 JOIN/子查询**可能触发 "Illegal mix of collations" 或隐式转换丢索引；当前主要 JOIN 走数字 id（customer↔user↔file），字符串跨组比较少 → 风险低-中，**未发现实际报错**（需慢查询/错误日志侧证）。
- 建议：新表统一 `utf8mb4_0900_ai_ci` 或迁移对齐（大工程，登记即可）。

## 三、行数分布（TOP）

| 表 | 估算行数 |
|---|---|
| sys_log | 13,264 |
| biz_file | 2,544 |
| biz_incentive_read | 1,655 |
| sys_audit_log | 1,219 |
| biz_customer | 625 |
| biz_incentive_record | 536 |
| biz_invoice | 282 |
| biz_file_grant | 279 |

- 规模小（<2 万行），当前无慢查询压力面；**零压测**（A15 未做）。

## 四、未覆盖

- 慢查询日志/执行计划抽检：未做（生产 `slow_query_log` 状态未查；本地库无真实负载）。
- 冗余索引/前缀索引：未逐表分析（93 表，留 W3）。

## 五、结论

- **A9 = PASS（结构层：引擎/主键/规模无阻断）**，附 2 项观察：collation 混用（P2）、备份表残留（P3）。
