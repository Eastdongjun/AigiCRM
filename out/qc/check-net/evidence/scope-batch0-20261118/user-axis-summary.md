# 18 轴 × 28 活跃用户授权矩阵（Batch 0 扩展）

- 数据集 `aigicrm_prod_20261118`；活跃用户 28；轴 18

| 轴 | explicit | default | none |
|---|---|---|---|
| customer:view | 28 | 0 | 0 |
| customer:edit | 28 | 0 | 0 |
| customer:delete | 28 | 0 | 0 |
| customer:finance_edit | 27 | 1 | 0 |
| file:view | 28 | 0 | 0 |
| file:manage | 28 | 0 | 0 |
| file:delete | 28 | 0 | 0 |
| file:version_rollback | 28 | 0 | 0 |
| approval:view | 26 | 2 | 0 |
| reminder:view | 26 | 2 | 0 |
| sea:view | 27 | 1 | 0 |
| incentive:stats_view | 28 | 0 | 0 |
| task:view | 26 | 2 | 0 |
| score:view | 28 | 0 | 0 |
| system:view | 26 | 2 | 0 |
| fabushi:stats_view | 28 | 0 | 0 |
| fabushi:file_view | 28 | 0 | 0 |
| fabushi:file_delete | 28 | 0 | 0 |

说明：explicit=角色/USER 档命中；default=角色 data_scope 兜底；none=无授权。默认不判 PASS，仅描述配置面。
