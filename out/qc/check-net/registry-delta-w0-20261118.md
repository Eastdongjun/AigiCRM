# 登记册 Delta — W0（2026-11-18，QC 建议稿）

> QC 不直接改登记册；本文件是给协调方 0b 的补丁稿。状态含义沿用登记册 §0（U/V/D/P/C）。
> 依据：W0 收口包、closure-ledger、audit-crosswalk、matrix-v1、pilot 证据。

## A 事实层

| ID | 建议状态 | 证据/说明 |
|---|---|---|
| A1 | **C** | 当前 HEAD `10a6aa99` 三件套亲跑：后端 1187/0、契约 191 PASS/1 SKIP/0 FAIL、前端 340/0+537/537；证据 `/tmp/qc-a1-*.log`、`contract-map-20261118.md` |
| A2 | **P** | crosswalk：11-12 审计 1C+5H 已核、5H 待核；11-13 2 P0、11-17 19 条待核；`audit-crosswalk-20261118.md` |
| A3 | **P** | 旧 B1-B4 未逐条；11-12 P0 表已对账（P0-1/2/4/6 PASS、P0-3 CI 级 PASS、P0-5 FAIL）；`closure-ledger` |
| A4 | **C** | 生产快照七项全符（health/new/jar/Flyway/旧表/legacy/对账/EXPORT_DIR）；测试服基线 A；`closure-ledger` §六 |
| A5 | **P** | 五源清单已出（44 Controller→18 域、322 端点、49 路由、111 vue、51 MP、155 迁移、96 码）；测试映射未做；`module-inventory` |
| A6 | **P** | 仅 customer:view 28 用户（196 项计数、27/28 ID）；其余 17 轴未做；DEF-1 待修 |
| A7 | **U** | 全库数据卫生画像未做（仅客户/法布施/文件部分 SQL） |
| A8 | **P** | reconcile 2632/2632；记录↔卷↔引用三方扫描未做 |
| A9 | **P** | schema drift 已修（测试库 19.2）；约束/索引/慢查询/字符集未做 |
| A10 | **U** | 备份恢复演练未做 |
| A11 | **U** | 审计日志覆盖/PII 未做（仅登录事件观察） |
| A12 | **P** | CI osv-scan success；本地 OSV/CVE 原始日志与密钥轮换未做 |
| A13 | **U** | MP 真机/web-view/订阅消息未做 |
| A14 | **U** | PC 页面实拍覆盖率未做（11-08 54 页为旧证据） |
| A15 | **U** | 性能/容量/压测未做 |
| A16 | **U** | 监控告警/接收人未做 |
| A17 | **V** | 工作区法布施原型资产 M/D 变更：已知未提交，待实施方确认是否有意 |
| A18 | **U** | 11-14 权限 xlsx vs 11-16 24k 矩阵权威性未核 |
| A19 | **V** | 法布施文档漂移（5/9/10 码、11/12 表、12/7 页）已记录，待书面裁决 |
| A20 | **U** | 生产 87 表/新治理表/legacy 列使用画像未做 |

## B/C/D/E

- B1-B15：状态不变（D 待裁决）；B14（Sonar 口径）用户已拍板严格 FAIL/整改；B15（4 户）3 户接受+ID10 待财务核。
- C1-C10：不变（V/U）。
- D1-D14 / E1-E13：不变（未系统执行）。

## 新增登记

| ID | 事项 | 状态 | 证据 |
|---|---|---|---|
| W0-DATASET | 新快照库 `aigicrm_prod_20261118`（md5 2936f109…、93 表、69 用户、625 客户、279 grants） | C（数据集 pin） | `closure-ledger` §六；协调方独立核验 |
| P0-5 | Sonar 严格口径未达标：全仓 6 BLOCKER/459 CRITICAL/52 BUG/3 VULN；真实 Blocker 1 处 CSS rpx；459 全为存量 code smell；gate 空门 | F（用户拍板严格 FAIL，整改后销账） | `evidence/sonar-20261118/README.md` |
| W0-PILOT | 系统配置 public 试点 C@L2（7/7 键值一致） | C@L2（对象级） | `evidence/pilot-config-20261118/README.md` |
| ENDPOINT-COUNT | 端点权威数 322（api-list/guard 一致）；endpoint-parity 的 290 是过滤口径，差 32 待逐条核 | P | `contract-map-20261118.md`、W0 收口 |

## 备注

- A1 的 C 是"门禁基线"闭环，不是"全项目检查网"闭环；全项目 C 数仍为 0（矩阵 v1）。
- 建议协调方在应用本 delta 时保留原表历史行，不覆盖旧证据。
