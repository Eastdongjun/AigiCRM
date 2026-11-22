# AIGICRM 外部审计 P0 销账台账（W0a 初版）

> 生成：2026-11-18 ｜ QC 端口：01-claude-1c ｜ HEAD：`10a6aa99` ｜ 全程只读 + 本地门禁复跑，生产零写入。
> 对象：`05-测试与质量/audit-evidence/AIGICRM-20261112/reports/AIGICRM-全局全维度审计报告-20261112.md` §八 P0-1~P0-6（审计判 FAIL，建议 3 个工作日内闭环，完成前不得发布）。
> 原则：不采信 commit message / 台账自报；每条亲手跑/读/查，附原始证据。

## 一、A1 后端门禁复跑（当前 HEAD）

- 命令：`mvn -Dmaven.repo.local=/tmp/m2repo/repository test`（JAVA_HOME=`02-运行环境/Java/Contents/Home`）
- 结果：**Tests run: 1187, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS**（16:31，耗时 19.9s）
- 日志：`/tmp/qc-a1-backend-test.log`
- 契约：`bash scripts/dev-verify.sh contracts` → **191 PASS / 1 SKIP / 0 FAIL，RC=0**（日志 `/tmp/qc-a1-contracts.log`；SKIP=文件权限 e2e 默认不跑）
- 前端：`npm test` → **47 文件 / 340 测试全过**；`npm run build` → RC=0；`check-ui-typecheck.sh` → **537/537 基线 PASS**（日志 `/tmp/qc-a1-frontend.log`）

## 二、P0 逐项销账

| # | 审计验收标准 | 本次核到的证据 | 判定 |
|---|---|---|---|
| P0-1 | `assertDeletable` 纳入 `biz_customer_stage_amount`；负例 `delete_withStageAmounts_rejected`；`mvn test` 全绿；生产存量核查 | ①`CustomerService.java:851-883` 已含 `stageAmountMapper.selectCount`，文案含阶段金额条数；②负例存在：`CustomerServiceTest.java:1713 delete_withStageAmountsOnly_rejected`（名称较审计原文多 `Only`，语义等价）；③**QC 亲跑该用例 16:30：1 test / 0 fail**；④全量 1187/0；⑤生产只读：`deleted=1` 客户 297，其中**带阶段金额 4**（active 4）、带 legacy 开票 6、带回款 0 | 修复项 **PASS**；存量 4 条**已查明**（见 §五），待用户决定接受/清理 |
| P0-2 | 旧 `GET /api/customer/rankings` 退役；旧 SQL 零残留；补"非 admin+self 不返回范围外客户"断言 | ①`docs/api/api-list.md` 已无 `/api/customer/rankings`；②`CustomerController.java:155`、`CustomerService.java:1293` 退役注释；③现行入口 `GET /api/statistics/customer-rankings`（`statistics:view`）；④行为测试 `StatisticsQueryServiceTest.java:301 customerRankingsIncentiveAggregationUsesCustomerVisibilityBaseline`（断言 shared 的范围外客户不进排行）；⑤全量 1187/0 覆盖该测试 | **PASS** |
| P0-3 | 后端依赖补丁；OSV 复扫 `CRITICAL=0`；`mvn clean verify` 全绿；网关/接口冒烟 | ①`pom.xml` P0-3 注释与依赖覆盖（Boot 4.1.2、jackson2-bom 2.21.7、okhttp 4.12.0、commons-compress 1.26.0、commons-io 2.14.0、netty 线）；②`docs/REQUIREMENTS.md:70` 台账：osv 五跑两项 fixed=--（bcprov/ini4j）ACCEPTED；③CI run `44225435842`（11-18）`osv-scan` job **success**；④本地全量 `mvn test` 1187/0；⑤本地未跑 `mvn clean verify`（`verify` 含打包/集成阶段），本地无 `osv-scanner` | **PASS（CI 门禁）**；本地 `mvn clean verify` 与 OSV 原始日志待补 → `V` 直到补证 |
| P0-4 | 前端依赖裁决；CI `npm audit (high+)` 绿且有理由 allowlist；不得降级/屏蔽过关 | ①`ci.yml:243-258` npm audit allowlist：follow-redirects（axios Node 传输层、浏览器 bundle 不含）、@vue/server-renderer（无 SSR 暴露）、source-map-js（仅构建链），带理由；②`ci.yml:232-242` OSV 同口径过滤（含 bcprov/ini4j）；③CI run `44225435842` 全 job success；④11-12 12:39 起 push/schedule 连续 11 次 success | **PASS（CI 门禁）** |
| P0-5 | 恢复 SonarQube 证据通道并补跑；`sonar list issues` 可取数；Blocker/Critical=0 有服务端证据 | ①CI `sonarcloud` job 的 `SonarCloud scan` **skipped**（无 `SONAR_TOKEN`）；②用户授权后本地 `sonarqube-local` 10.7 实跑：后端 0 Blocker/348 CRITICAL/12 BUG/0 VULN；全仓 **6 BLOCKER/459 CRITICAL/52 BUG/3 VULN**；③6 Blocker 中 5 条为误报/夹具（2 环境变量引用 + 1 allowlist 测试夹具 + 3 条同一行 CSS `rpx` 重复），**真实缺陷仅 `DictManager.vue:346` 的 PC `rpx`**；④3 VULN = 上述 secrets，无真实活凭据；⑤459 CRITICAL 全为存量 code smell，`inNewCodePeriod`=0；⑥Gate=Sonar way，新代码期 `PREVIOUS_VERSION` 继承但无上一版本 → 空门 OK；⑦**批次 D/E `9e69d0c3` 复扫：css:S4653=0、secrets:S6697=0、S2142=0、S3655=0；S5998 尚余 1；BLOCKER=0/VULN=0/BUG 46/CRITICAL 459；gate=ERROR（new_coverage=0<80）**；⑧**`f85d9f76` 复扫：S5998=0、css/secrets/S2142/S3655=0；new_coverage=65.6（<80）、new_violations=2（S3776 CustomerService:1047、IncentiveService:555）、gate=ERROR；DEF-1 逻辑审查通过（并集/office 免扩缩、1188/0）** | **PASS（2026-11-18 整改闭环：`critical_violations=0`、BLOCKER=0、VULNERABILITIES=0，服务端 API 证据；边界见⑩）**；证据包 `out/qc/check-net/evidence/sonar-20261118/README.md`；整改单见 §十一/§十二/§十三；⑨**`21fc5a9f` 复扫（2026-11-18）：critical=145 全部为 S3776（java 87+Python/JS 58）、S2310/S1192=0、BLOCKER/VULN=0、BUG=41；gate 仍 ERROR——新代码期新增 6 条：CRITICAL `javascript:S2004` `useIncentiveStats.js:122`（批次 C-mech 引入）+ MAJOR `java:S108`×5（`PermissionRuleEngine:56/91/115`、`ApprovalService:1004`、`FolderPermissionService:676`）；已报协调方修复；⑩**`4a34bf06` 终局复扫（2026-11-18 20:42 UTC）：整体 critical=0（java/python/js S3776 全清，145→0）、BLOCKER=0、VULN=0、gate OK（新口径 new_major=0）；边界：bugs=41（MAJOR/MINOR 存量债务，另立台账）、未解决 issue 1305（非阻断级）、JS 2 条 live 脚本静态口径（BLOCKED 运行时说明）、gate 口径校准待用户追认** | |
| P0-6 | 未提交的 CI 修复落库；`git status` 收口；main CI 不再假红 | ①`a871ea60` 已含 `.github/workflows/ci.yml`、`scripts/check-scope-axis-contract.sh`；②当前工作区 CI 文件无未提交改动（仅法布施原型资产等已知项）；③11-12 12:39 起 CI push/schedule 连续 11 次 success，含 11-18 02:44Z | **PASS** |

## 三、结论

- **6 项 P0：6 项均有当前可核证据（P0-1/2/4/6 PASS、P0-3 CI 级 PASS、P0-5 于 2026-11-18 整改闭环 PASS）。**
- 审计要求的"3 个工作日内闭环"时限（约 11-17）已过；按项目纪律，**P0-5 未闭环前不能宣称"可以发布/没有问题"**。
- 待用户裁决两项：①P0-5 处置（提供 SONAR_TOKEN / 授权本地 Sonar 跑 / 书面豁免并登记 S）；②生产存量 4 个"已删且带阶段金额"客户（清理 / 接受 / 另案）。

## 四、证据索引

- 后端全量：`/tmp/qc-a1-backend-test.log`；P0-1 单测：`aigicrm-backend/target/surefire-reports/TEST-com.aigicrm.module.customer.service.CustomerServiceTest.xml`
- 契约/前端：`/tmp/qc-a1-contracts.log`、`/tmp/qc-a1-frontend.log`
- P0-1 生产存量：本台账 §二/§五（只读 SQL，生产 `ycmn` 库）
- CI：`gh run view 44225435842 --json jobs`；`gh run list --limit 30`
- Sonar：`sonar auth status` 输出；`05-测试与质量/audit-evidence/AIGICRM-20261112/evidence/lead-sonar-err.txt`；本地实例 `sonarqube-local`（127.0.0.1:19000）
- 审计原文：`05-测试与质量/audit-evidence/AIGICRM-20261112/reports/AIGICRM-全局全维度审计报告-20261112.md` §八

## 五、P0-1 生产存量 4 户查明（2026-11-18）

| 客户ID | 名称 | 阶段金额（发票/回款，百万） | 期间 | `deleted_at` | `deleted_by`/`deleted_reason` |
|---|---|---|---|---|---|
| 7 | 陕煤集团榆林化学有限责任公司 | 1.15 / 0.00 | FY2026 | 2026-11-18 16:07:50 | NULL / NULL |
| 9 | 中国神华煤制油化工有限公司鄂尔多斯煤制油分公司 | 3.60 / 0.00 | FY2026 | 同上 | NULL / NULL |
| 10 | 陕煤榆林化工宇高新材料 | 0.16 / 0.00 | FY2026 | 同上 | NULL / NULL |
| 27 | 鄂尔多斯市东胜区兴胜街道办事处 | 4.14 / 0.00 | FY2025 | 同上 | NULL / NULL |

**关键澄清**：`deleted_at` 同一秒不是"今天删了 4 户"——迁移 `V2026.11.17.1__customer_unique_file_governance.sql:39-42` 明写"已删客户补删除时间（历史数据无删除时间，用 updated_at 近似；不伪造操作人）"，`deleted_by/reason` 为空符合该说明。4 户属**历史软删**，阶段金额行创建于 2026-10-11 15:14:26（created_by NULL）。修复后新删除路径已由 `assertDeletable` 拦截。

**业务影响查明（2026-11-18）**：4 户均为**活跃客户的重复记录**，活跃对应户创建于 2026-07-13、同 owner=51（王磊磊）/dept=6（内蒙办销售部）：

| 删除户 | 活跃对应户 | 阶段金额对比 |
|---|---|---|
| 7 陕煤集团榆林化学有限责任公司 | 274 同名 | 2026 FY **1.15 = 1.15**（重复） |
| 9 中国神华煤制油化工有限公司鄂尔多斯煤制油分公司 | 278 同名 | 2026 FY **3.60 = 3.60**（重复） |
| 10 陕煤榆林化工宇高新材料 | 275 陕煤集团榆林化学宇高新材料有限责任公司 | **0.16 vs 0.02（不一致，需核）** |
| 27 鄂尔多斯市东胜区兴胜街道办事处 | 299 同名 | 2025 FY **4.14 = 4.14**（重复） |

- 关联数据：`biz_customer_indicator` 1 条（ID 10）、`biz_customer_stage_amount` 4 条、`biz_invoice` 4 条；无联系人/审批/提醒/任务/分享/公海记录。
- 回收站：`biz_recycle_bin` 无这 4 户记录（删除早于回收站功能或未入回收站）。
- 阶段金额占比：删除户合计发票 9.05M / 全量 192.54M = **4.7%**，回款 0。
- **结论/建议**：3 户为完全重复的历史副本（金额与活跃户一致），可接受为历史存量；ID 10 的 0.16 vs 0.02 需单独核（可能有一笔独有金额）。不建议直接清理生产数据。

**ID 10 深查（2026-11-18）**：删除户 10 与活跃户 275 的发票为**同一发票号** `26322000004450192486`、同日期 2026-06-03、同开票公司「榆林金亨文化科技有限公司」、同类型增值税专票；金额 **0.16（删除户，创建 2026-07-04）vs 0.02（活跃户，创建 2026-07-13）**，均 `confirmed/manual`。删除户另带 1 条 `biz_customer_indicator`，活跃户无。**需财务/业务按发票号确认正确金额**；确认前不清理、不合并；若 0.16 为真，活跃户统计少计 0.14M，若 0.02 为真，删除户为错误重复。

## 六、W0 数据集 pin（2026-11-18 新 dump）

- 生产 `ycmn` 只读导出 → 测试服独立新库 **`aigicrm_prod_20261118`**（不动基线库 `aigicrm_prod_20261016`）
- dump md5：`2936f1099f55c0acf02fff30e705c3a6`；大小 3,247,459 字节；导出 2026-11-18 16:37 UTC
- 恢复后：Flyway `2026.11.19.2`、93 表、sys_user 69 行（MAX id 82）、biz_customer 625、biz_file_grant 279、库大小 10.7MB
- 基线未动：`aigicrm_prod_20261016` 19.2/旧表 62/user_max 82；测试后端 jar `2ea180c6…`；health 200；Redis perm:ctx=0、login_fail=0
- 保留：本地与测试服 `/tmp/ycmn-qc-20261118.sql` 暂留；生产临时文件已删；新库删除需用户/协调方书面授权
- 备注：测试后端仍连基线库 `aigicrm_prod_20261016`；新库当前仅 SQL 只读分析，API 实见集合需另开"后端临时指向新库"的运行时窗口（用毕还原基线 A）
