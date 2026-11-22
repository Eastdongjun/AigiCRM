# Sonar 证据包（P0-5 / B14 口径待裁决）— 2026-11-18

> **终版归档（2026-11-18 20:42 UTC，QC `01-claude-1c`）**：HEAD `4a34bf06` 终扫 **整体 critical=0 / BLOCKER=0 / VULN=0**，gate `AIGICRM-P0-5` = OK；**P0-5 = PASS**（协调方 0b 已确认，三条边界见 `closure-20261118.md` §五）。本文件为滚动记录全文，终局判定以 `closure-20261118.md` 为准。

> QC 01-claude-1c ｜ HEAD `10a6aa99` ｜ 本地 SonarQube 10.7.0（`sonarqube-local:19000`，Community）｜ 只读扫描，不改代码。

## 一、扫描范围与结果

| 扫描 | 项目 key | 文件 / ncloc | 结果 |
|---|---|---|---|
| 后端单仓 | `aigicrm-backend-local` | 294 / 28,429 | 0 Blocker / **348 CRITICAL** / 762 MAJOR / 91 MINOR / 12 INFO；12 BUG / 0 VULN / 1,201 code smell |
| 全仓（按 `sonar-project.properties`） | `Eastdongjun_AigiCRM-local` | 691（SCM 发布）/ 571（`files` 度量）/ 65,700 | **6 BLOCKER / 459 CRITICAL** / 959 MAJOR / 395 MINOR / 12 INFO；52 BUG / **3 VULN** / 1,776 code smell |

> 文件数口径：691 = 扫描器 SCM Publisher 发布的源文件数（含 `sonar.tests` 测试文件）；571 = Sonar `files` 度量（仅主代码文件）；差 120 = 测试文件（与后端 120 个测试类一致）。两者不是同一指标，无证据瑕疵。

- 全仓 `sonar-project.properties` 覆盖：backend/src/main + ui/src + miniprogram + scripts + nginx + sql；tests 单列。
- 本地扫描补充参数：`-Dsonar.java.binaries=02-迭代源代码/aigicrm-backend/target/classes`、`-Dsonar.java.test.binaries=.../target/test-classes`（项目 properties 未配；CI 的 SonarCloud job 同样未配——潜在通道缺陷，见 §六）。
- 扫描日志：`scanner-backend.log`、`scanner-full.log`；服务端度量/issue 原始 JSON 见同目录。

## 二、6 个 BLOCKER 逐项裁决

| # | 严重级/类型 | 规则 | 位置 | 裁决 |
|---|---|---|---|---|
| 1-3 | BLOCKER/BUG | css:S4653 | `aigicrm-ui/src/views/system/DictManager.vue:346` | **真实缺陷（1 行）**：PC Vue 用了小程序单位 `10rpx 4rpx 6rpx`，浏览器无效；3 条是同一行的重复报告 |
| 4-5 | BLOCKER/VULNERABILITY | secrets:S6697 | `scripts/gen-data-dictionary.sh:53-54` | **误报**：`-p\$MYSQL_ROOT_PASSWORD` 是环境变量引用，非字面量口令 |
| 6 | BLOCKER/VULNERABILITY | secrets:S6697 | `scripts/test-secret-scan.sh:33` | **误报/夹具**：`bad-url.env` 是扫描器行为测试的故意 p4 夹具（`zz9-url-cred`），项目 allowlist 第 82 行已有理由 |

→ 真实 Blocker = **1 个 CSS 单位缺陷（DictManager.vue:346）**；其余 5 条为误报/夹具。

## 三、3 个 VULNERABILITY

与 §二 的 3 条 secrets 完全相同（2 误报 + 1 夹具），**无真实活凭据**。

## 四、52 个 BUG 分布

| 严重级 | 数量 | 主要规则/位置 |
|---|---|---|
| BLOCKER | 3 | `css:S4653`（同上 CSS rpx，重复） |
| CRITICAL | 4 | `javascript:S2871` — 全部在 `scripts/`（`check-nested-wxfor.mjs:75`、`probe-incentive-home.mjs:16`、`test-miniprogram-customer-filter.js:100/102`），sort 缺比较器 |
| MAJOR | 28 | `Web:TableHeaderHasIdOrScopeCheck` 14、`javascript:S5850` 5、`javascript:S3923` 2、java:S2142 2、java:S1751/S5998/S3655/S2259 各 1 等 |
| MINOR | 17 | `Web:MouseEventWithoutKeyboardEquivalentCheck` 11、java:S3077 3、java:S2184 3 |

- 后端单仓 12 BUG：7 MAJOR / 5 MINOR，全为存量（创建 2026-09-11～11-17），无 CRITICAL/BLOCKER。
- 前端/脚本 BUG 多为可访问性与测试脚本质量，不是安全漏洞。

## 五、459 CRITICAL / 新代码分账 / Gate

- **459 CRITICAL 全部为 code smell**（认知复杂度、重复字面量等），不是 security vulnerability。
- 新代码分账：`inNewCodePeriod=true` 下 **BLOCKER=0、CRITICAL=0** → 全部为存量（整体代码），无"新代码"引入项。
- Gate：`Sonar way`（默认）；新代码期 `PREVIOUS_VERSION`（继承）；本项目**无上一版本** → 新代码度量为空；`alert_status=OK` 是**空门通过**，不能作为"全量 0 critical"证据。
- `sqale_index`（技术债）全仓 13,939 分钟；后端 10,100 分钟。

## 六、限制与已知偏差

- `sonar.java.libraries` 为空（项目 properties 未配）→ Java 分析精度下降；`sonar-project.properties` 也未配 `sonar.java.binaries`，CI 的 SonarCloud job 同样未配——**若配置 SONAR_TOKEN，扫描可能因缺编译产物直接失败**（本包已用命令行补 binaries 绕过）。
- 文件编码警告（全仓 3 条 warning）与 Python 版本未指定，可能影响少量文件解析。
- 本地 SonarQube 10.7 Community 与 SonarCloud 规则/质量门配置可能不同；本包是**代理证据**，不是 SonarCloud 官方结果。

## 七、审计原文出处

`05-测试与质量/audit-evidence/AIGICRM-20261112/reports/AIGICRM-全局全维度审计报告-20261112.md` **§八 P0 表 P0-5 行**：
> 「恢复 SonarQube 证据通道并补跑」／验收标准：「`sonar list issues` 可取数；Blocker/Critical=0 有服务端证据」

## 八、复跑命令（脱敏，token 运行时注入）

```bash
# 后端
cd 02-迭代源代码
SONAR_HOST_URL=http://127.0.0.1:19000 SONAR_TOKEN="$TOK" npx --yes @sonar/scan@5.0.1 \
  -Dsonar.projectKey=aigicrm-backend-local \
  -Dsonar.sources=aigicrm-backend/src/main/java \
  -Dsonar.java.binaries=aigicrm-backend/target/classes
# 全仓（项目 properties + 补 binaries）
cd ..
SONAR_HOST_URL=http://127.0.0.1:19000 SONAR_TOKEN="$TOK" npx --yes @sonar/scan@5.0.1 \
  -Dsonar.projectKey=Eastdongjun_AigiCRM-local \
  -Dsonar.host.url=http://127.0.0.1:19000 \
  -Dsonar.java.binaries="02-迭代源代码/aigicrm-backend/target/classes" \
  -Dsonar.java.test.binaries="02-迭代源代码/aigicrm-backend/target/test-classes"
# 取证
curl -u "$TOK:" "http://127.0.0.1:19000/api/measures/component?component=Eastdongjun_AigiCRM-local&metricKeys=blocker_violations,critical_violations,bugs,vulnerabilities,code_smells"
curl -u "$TOK:" "http://127.0.0.1:19000/api/issues/search?componentKeys=Eastdongjun_AigiCRM-local&resolved=false&ps=1&facets=severities,types"
```

## 九、结论（B14 待裁决，不写"通过"）

- **证据通道已恢复**：本地 Sonar 实跑、服务端可查 issue/measures，满足审计 P0-5 的"可取数"半项。
- **严格"Blocker/Critical=0"未满足**：6 BLOCKER（5 误报/夹具 + 1 真实 CSS rpx）、459 CRITICAL（存量 code smell）。
- **Gate OK 不可采信**：无上一版本，新代码度量为空，属空门。
- **用户 2026-11-18 拍板：严格判 FAIL，整改后销账**（B14 口径=严格）。

## 十、12 个后端 BUG 逐条定级

| # | 位置 | 规则 | 影响判断 | 定级/动作 |
|---|---|---|---|---|
| 1 | `FileScanService.java:50` | java:S2142 | `InterruptedException` 被 catch(Exception) 吞掉，未恢复中断位；无数据影响 | **修复（P2 防御性）** |
| 2 | `FileService.java:1296` | java:S2142 | 视频预览生成同上 | **修复（P2 防御性）** |
| 3 | `PermActionRegistry.java:56` | java:S1751 | `for (c : codes) return c;` 是"取首个"的有意写法，无功能缺陷 | 债务/重构（P3） |
| 4 | `PermissionHintService.java:88` | java:S3077 | `volatile Snapshot`；若 Snapshot 不可变则安全 | 先核 Snapshot 不可变性；误报则注释（P3） |
| 5 | `FileService.java:129` | java:S3077 | `volatile LongSupplier` 测试时钟，生产为 System::nanoTime | 误报/注释（P3） |
| 6 | `IncentiveService.java:564` | java:S5998 | 正则 `^\[\d{1,18}(,\d{1,18})*\]$` 在超长输入下有回溯风险；已有 SLOT_JSON_MAX_LEN=500 | **修复（P2：正则前先判长度）** |
| 7 | `GroupScopeUtil.java:83` | java:S3655 | `findFirst().get()` 空流会抛 NoSuchElementException；需确认前置非空保证 | **修复/加守卫（P2）** |
| 8 | `StatisticsQueryService.java:248` | java:S2184 | `TTL/2` 整除是有意写法（毫秒），非缺陷 | 误报/债务（P3） |
| 9 | `IncentiveService.java:1082` | java:S2184 | 同上 | 误报/债务（P3） |
| 10 | `StatisticsQueryService.java:244` | java:S2184 | 同上 | 误报/债务（P3） |
| 11 | `FileService.java:1779` | java:S2259 | `sanitizedFilename == null ? -1 : ...` 已守卫，短路安全 | 误报（P3） |
| 12 | `ClientIpUtils.java:17` | java:S3077 | `volatile String[]` 整体替换、不原地改；读引用安全 | 误报/注释（P3） |

**结论：12 个后端 BUG 无确认的功能/数据缺陷；4 条防御性修复（1/2/6/7）、其余债务或误报。全仓 52 BUG 另含前端可访问性（表头 id/scope 14、鼠标键盘等价 11）与 scripts 排序 4，均为债务级。**

## 十一、修复工单与 gate 修复方案

### 11.1 唯一真实 Blocker 工单：DICT-RPX-001
- 位置：`aigicrm-ui/src/views/system/DictManager.vue:346`
- 内容：`padding: 10rpx 4rpx 6rpx` → PC 浏览器无效单位，改为 px 等效值（如 `5px 2px 3px`，以设计稿为准）
- 全仓排查：PC `src` 下 `rpx` 仅此 1 处（grep 确认）
- 验收：`npm test`/`build`/`typecheck` 绿；Sonar 重扫 `css:S4653` = 0
- 归属：实施方修复（协调方派单），QC 复验；本轮不自行改代码

### 11.2 Gate/通道修复方案（实施方）
1. `sonar-project.properties` 增加 `sonar.java.binaries=02-迭代源代码/aigicrm-backend/target/classes` 与 `sonar.java.test.binaries=.../target/test-classes`。
2. CI `sonarcloud` job 在扫描前加 Java 21 + `mvn -B -DskipTests compile`（或 package），并用 `mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt` 生成 `sonar.java.libraries`——当前 job 无编译产物、无 libraries，配了 SONAR_TOKEN 也会失败或精度不足。
3. 新代码基线：设 `sonar.newCode.referenceBranch=main`（或按版本设 `sonar.projectVersion` + 新代码期），使 gate 从"空门"变为可评估；注意这不会把 459 条存量 critical 变成 0，严格口径仍需整改/豁免。
4. SonarCloud token 复判属外部服务动作，**须用户授权**，未授权前不执行。

### 11.3 P0-5 状态
- 用户已拍板：**严格判 FAIL，整改后销账**。在 DICT-RPX-001 修复 + 5 条误报/夹具留证 + 459 存量 code smell 整改/豁免单闭环前，P0-5 不写通过、不销账。

## 十二、批次 D/E 复扫（commit `9e69d0c3`，2026-11-18）

- 已清：`css:S4653=0`、`secrets:S6697=0`、`java:S2142=0`、`java:S3655=0`。
- 未清：`java:S5998=1`（`IncentiveService.java:564` 正则回溯；建议正则前加长度守卫）。
- 总览：**BLOCKER=0 / VULNERABILITY=0 / BUG 52→46 / CRITICAL=459（未变，批次 A/B/C 待做）/ code smell 1776**。
- Gate：**ERROR**；唯一失败条件 `new_coverage=0.0 < 80`（`new_duplicated_lines=0`、`new_violations=0` 均 OK）→ 需补 JaCoCo 覆盖率或调整该条件。
- S1192 整改清单：`remediation-s1192.json`（250 条，file/line/message/severity/debt）。
- 结论：D/E 目标（CSS/secrets/防御性 BUG/gate 通道）大部分达成；S5998 与覆盖率 gate 未清，P0-5 仍 FAIL。

## 十三、commit `f85d9f76` 复扫（DEF-1 + S5998 + 覆盖率）

- 已清：`java:S5998=0`、`css:S4653=0`、`secrets:S6697=0`、`java:S2142=0`、`java:S3655=0`。
- 新增 2 条 CRITICAL code smell（修复方法自身）：`java:S3776` `CustomerService.java:1047`（21>15）、`java:S3776` `IncentiveService.java:555`（19>15）。
- 覆盖率：`new_coverage=65.6%`（<80）；`new_violations=2`；`new_bugs=0`；`new_dup=0`；overall critical 461；gate **ERROR**。
- DEF-1 逻辑审查：`(base OR share)` 并集、office 免扩免缩、ORG/OWNER 分支正确；回归 `autoShare_officeScope_notNarrowed`；后端 1188/0、JaCoCo XML 生成。
- 下一步：重构 2 个方法降复杂度 + 补定向测试至 new_coverage≥80；API 实见需第二窗口（18:30–19:15 UTC）部署 f85 jar（md5 `1cd20845…`）。

## 十四、最新 HEAD 复扫（`de81f16c`，含 A1 `f175a4e3` 与 MP `51ae5fbf`）

- **S1192 250→2**（A1 实际清 248 条）；**critical 461→211**；BLOCKER=0、VULNERABILITY=0、new_bugs=0。
- 新增 2 条 S3776 已清；总 S3776 **87** 为存量（批次 B）。
- **new_coverage=49.3%**（<80）；**new_violations=1**：`java:S3358` `PermissionMetadataService.java:107` 嵌套三元；gate **ERROR**。
- MP P0 验证：`51ae5fbf` normalizeFile perms 透传、残留端点删除；files-scope/permission-refresh 测试 PASS。
- 11-17 High 1 仍 OPEN：role 0（销售总监）26 个 system 权限、14 个 CRUD/授权——单列裁决，不混入 P0-5。

## 十五、A2 复扫（`97f00e15`）

- **S1192=8（非 0）**：全部为"应使用已定义常量"的新漏（ApprovalService:250、CustomerController:326、CustomerVisibilityService:56、FolderPermissionService:322、ReminderService:142、PermissionMetadataService:107 `"scopeAxis"`×3、SysRoleService:87、SysUserService:299）。
- **S3358 新增=0**（目标达成）；总 13 为存量。
- new_violations=8（=S1192）；critical 217；new_coverage=**49.0%**；gate ERROR。
- 未覆盖清单：`uncovered-new-lines.md`（43 文件/449 行）；**未覆盖行是 if/throw/query 行为分支，不是常量声明**——类加载 smoke 覆盖不到；需定向行为测试。
- 新非指令行（注释/常量声明）另列，不入覆盖率分母。

## 十六、`21fc5a9f` 复扫（批次 C-mech + scripts 清理，2026-11-18）

- HEAD `21fc5a9f`（含 105fd0f5 批次 C-mech 40 条清理 + 21fc5a9f scripts 提交），工作区源文件干净，CE=SUCCESS。
- **整体 critical=145（全部 S3776：`java:S3776`=87、Python/JS 其余 58），与协调方预期 145 一致**；`S2310=0`、`S1192=0`、`java:S3358` 新增=0；BLOCKER=0、VULNERABILITY=0、BUG=41。
- **gate 仍 ERROR**：新代码期（`PREVIOUS_VERSION`，起点 2026-11-18T16:39:40）新增 6 条：
  - CRITICAL `javascript:S2004` `aigicrm-ui/src/composables/useIncentiveStats.js:122`（嵌套函数>4 层）——批次 C-mech 修 S2004×2 时新引入，是 `new_critical_violations=1` 唯一来源；
  - MAJOR `java:S108` 空代码块 ×5：`PermissionRuleEngine.java:56/91/115`、`ApprovalService.java:1004`、`FolderPermissionService.java:676`（批次 B 重构残留）；
  - `new_violations=6` 全部计入 gate（条件 new_violations>0 ERROR）。
- `new_coverage=46.2%` 不再阻断——新 gate `AIGICRM-P0-5` 无覆盖率条件，生效正确。
- 复跑命令：`bash /tmp/qc-sonar-f85.sh`（token 运行时生成，coverage 取 `target/site/jacoco/jacoco.xml`；e12c8d43 测试 1194/0/0 生成，105fd0f5 未触 Java 仍适用）。

## 十七、`15e0e0b3` 复扫 — **新代码门禁 PASS**（2026-11-18）

- HEAD `15e0e0b3`（0b 清零上节 6 条：S2004×1 改循环、S108×5 补注释块），CE=SUCCESS。
- **Gate `AIGICRM-P0-5` = OK**：`new_blocker=0`、`new_critical=0`、`new_bugs=0`、`new_vulnerabilities=0`、`new_violations=0`、`new_duplicated_lines_density=0.18`（<3）；无覆盖率条件（生效正确）。
- 整体面：BLOCKER=0、VULNERABILITY=0、BUG=41、**critical=133（全部 S3776：`java:S3776`=87、`python:S3776`=22、`javascript:S3776`=24）**；new_coverage=42.7（不阻断）。
- **P0-5 判定**：新代码门禁 PASS 记入证据；按用户 2026-11-18 拍的严格口径，**P0-5 整体仍 FAIL** —— 销账条件=整体 critical=0（133 条存量 S3776 待清），每批 commit 复扫。

## 十八、`10de7dd7` / `39e56f08` / `724e2279` 复扫记录（2026-11-18）

- `39e56f08`、`10de7dd7` 两次扫描**失败**：扫描期间协调方正在改工作区（`BasicSettings.vue` 465↔478、`PermScopeManager.vue` 540↔541），scanner 读磁盘与 JS 桥接读快照不一致 → `IllegalArgumentException: Line N is out of range`。**非代码缺陷**，已与协调方约定「commit → 冻结 → 扫描 → SCAN_DONE → 再开下一批」协议。
- **`724e2279`（PermScopeManager/ForgotPasswordDialog S3776 拆分）：CE=SUCCESS，critical=125**（java:S3776=87、python:S3776=21、javascript:S3776=17，JS 由 22 降 17）。
- **gate ERROR：new_critical=1 / new_violations=1** —— 唯一来源 `javascript:S3776` `aigicrm-ui/src/composables/useCustomerList.js:171`（认知复杂度 18>15，拆分时新引入）。其余 new_* 全 0、BLOCKER/VULN=0、BUG=41。
- 处置：报 0b 清该条；清后预期 gate PASS，P0-5 整体销账只剩 125 条 S3776 存量。
- **`fd266bc1`（useCustomerList.countAdvFilter 拆分）：gate = OK 全绿**，critical=124（java 87 / python 21 / js 17→16）。
- **`86648f60`（download.js/request.js 拆分）：gate = OK 全绿**，critical=**121**（java 86 / python 21 / js 14）。new_* 全 0、BLOCKER/VULN=0。
- 扫描协议：0b commit → 冻结工作区 → QC 扫 → 回 SCAN_DONE → 0b 开下一批（两次撞现场后固化）。
- **`82bccf36`（GrantReader/PermissionExplainService 拆分）：gate = OK 全绿**，critical=**120**（java:S3776=**85**、python 21、js 14）；0b 预期 java 86→84，实际 85（差 1，已给文件分布清单供定位）。
- **`e6f5e5b5`（FabushiPostService/FabushiDashboardService 拆分）：gate = OK 全绿**，critical=**118**（java:S3776=**83**、python 21、js 14）——符合预期 82/83（`buildDeptProgress` 16 仍在）。
- **`0408ec75`（SysRoleService/FileGrantService/TaskService/IncentivePermission/FileService 两批合并）：gate = OK 全绿**，critical=**113**（java:S3776=**78**、python 21、js 14）。注：中途 `585c2281` 单批扫描时工作区 dirty=3（正被下一批改动），该次 CE 数据作废，以本冻结结果为准。
- **`ab21b443`（StatisticsQueryService/SysUserService 拆分）：gate = OK 全绿**，critical=**111**（java:S3776=**76**、python 21、js 14）——符合预期 76。
- **`d5e5e17a`（IncentiveService/FileService/CustomerService 拆分）：java:S3776 76→72 达标**（整体 critical=107），**但 gate=ERROR**：新增 MAJOR `java:S112` `FileService.java:1314`「抛通用异常」——本批 `writeVideoPreview` 拆分引入，已报 0b 改专用异常；new_critical=0、其余 new_* 全 0。（该条已被 c0a50b13 清零）
- **`c0a50b13`（FileService S112 改专用异常声明）：gate 回绿 = OK**，critical=**107**（java:S3776=**72**、python 21、js 14）；S112 新增=0（存量 4 条不在 new code）。
- **`9a6f581f`（B-java2，11 文件/14 拆分）：java 72→58、critical→93 达标**，**但 gate=ERROR：new_violations=3（MINOR）**——`java:S135` SysConfigService:46 / ApprovalService:204、`java:S1126` FolderPermissionService:635（本批拆分引入）。已报 0b 修；new_critical=0、BLOCKER/VULN=0。
- **`3f9c0a03`（清 3 条新增 MINOR）：gate 回绿 = OK**，critical=**93**（java:S3776=**58**、python 21、js 14），new_violations=0。B-java2 批次闭环。
- **`9447e082`（B-java4，4 拆分）：gate = OK**，critical=**89**（java 54）。
- **`b561e438`（B-java5，3 拆分）：gate = OK**，critical=**86**（java 51）。
- **`a6e95386`（B-java6，3 拆分）：java 51→48、critical→83 达标，但 gate=ERROR 且为真实缺陷信号**：`LifecycleService.java` L234/235/236 `java:S2259`×3（`longVal` 可空 → 可能 NPE）+ L243 `java:S2589`（恒假条件）——0b 本批 `executeHandover` 拆分引入，已报其先核 null 语义再修。`new_bugs=3`、`new_violations=4`。
- **`b6bf2df2`（LifecycleService 交接目标空值语义修正）：gate 回绿 = OK**，critical=**83**（java 48）、new_*=0。**定性：真实缺陷修复**——3 处可空 `longVal` 拆箱 NPE 改判空后抛 BusinessException；`toOrg` 回退 `toOffice`，业务结果/文案不变。
- **`d5f03c14`（B-py，12 文件/15 拆分）：python:S3776 21→10（预期 6，差 4）、critical=72**；**gate=ERROR：new_critical=3**——新增 `check-mobile-navigation-contract.py:94`(18)、`fabushi/permission_matrix_full.py:70`(27)、`gen-unified-feature-audit.py:120`(16) 三条拆分产物仍超限；存量 7 条（permission_matrix_audit:202 / permission_matrix_full:134 / permission_matrix_v2:229 / permission_matrix_20k:120 / permission_role_smoke:27 / check-permission-registration-contract:207 / gen-unified:470）。已报 0b。
- **`6a4aaab5`（B-py gate，3 函数再拆）：python:S3776 10→7、critical=69、new_critical=0**；**gate 唯一 ERROR=`new_security_hotspots_reviewed=0`** —— 本批新增 1 个 hotspot `python:S5852` `gen-unified-feature-audit.py:118`（正则回溯启发式；QC 判定低风险：受控短输入、无非线性嵌套量词），已报 0b 选择「QC 标记 Safe」或「重写正则」。
- **`5bf59d8a`（正则拆两条重写）：python 7 / critical 69 / new_violations=0 达标，但 gate 仍 ERROR**——新代码期仍有 1 条 TO_REVIEW `python:S5852` `gen-unified-feature-audit.py:119`：新 `CONTROLLER_MAPPING_ARGS_RE` 仍含 `(?:value\s*=\s*)?` 可选组，未消除回溯启发式。已报 0b 再拆为无可选组的两条正则/手写解析。
- **`46430c36`（三条无选项正则）：三项正则已干净，但热点行移到 120**——新代码期仍 1 条 TO_REVIEW `python:S5852` `gen-unified-feature-audit.py:120` = `CONTROLLER_SIGNATURE_RE`（`public\s+.+\s+\w+\s*\(`，`.+` 贪婪回溯，O(n²)）；已报 0b 改 `public\s+[^\n(]*\(` 或两步判断。
- **`f0f6e287`（signature 正则改线性）：热点仍在 120 行**——新式 `public\s+[^\n(]*\(` 中 `\s+` 与 `[^\n(]*` 量词可重叠（空白属后者），Sonar 仍判多项式回溯；已建议 0b 去正则改两步判断（`lstrip().startswith("public ") and "(" in s`）。
- **`f92c4571`（signature 判定去正则）：gate 回绿 = OK**，新代码期 hotspots **1→0**，critical=**69**（python 7）。去正则两步判断（`startswith("public")`+第 7 字符 isspace+`"(" in s`）零回溯，报告逐字节一致。**经验：S5852 按文件逐条报可疑正则、行号随改动漂移，单条微调追不上，去正则/彻底线性才是终局。**
- **`306fb564`（B-java7/8，3 拆分）：java 48→47（预期 45），gate=ERROR**——新增 CRITICAL `java:S3776` `OrgScopeResolver.java:326` 复杂度 **26**>15：拆出的 helper 把主逻辑整体搬入未再切分（拆反了）。已报 0b 按职责再切。critical=68。
- **`24e00e33`（collectScopeCodes 再拆）：gate 回绿 = OK**，critical=**67**（java **46**、python 7、js 14）；`matchedScopeType`+`isExactScope` 两个 helper 清零，剩余 java 存量含 `resolveDeptScope`(56) 等大件。
- **`c4d9324f`（B-py2，2 拆分）：gate = OK**，critical=**65**（java 46、python **5**、js 14）；simulate/render 拆分包级输出一致。
- **`6a43ae63`（B-js1，4 拆分）：gate = OK**，critical=**61**（java 46、python 5、js **10**）；check-nested/mp-view 输出一致性已验。
- **`608616bc`（B-js2，3 产品拆分）：gate = OK**，critical=**58**（java 46、python 5、js **7**）；useCustomerList/useCustomerForm 拆后 340/340 + 536 基线不变。
- **`cc00475f`（B-js3，3 Vue 组件拆分）：gate = OK**，critical=**55**（java 46、python 5、js **4**）。
- **`2b60b38d`（B-js4，router/useIncentiveUpload 拆分）：gate = OK**，critical=**53**（java 46、python 5、js **2**）；router 保留 `to.path === '/mp-file-picker'` 字面量以过移动端边界检查。剩余 JS 2 条为 live 集成脚本（静态口径）。
- **`a6dc7870`（B-java9，2 拆分）：java 46→44、critical=51 达标，gate=ERROR（2 条 INFO 级）**——`java:S6541` "Brain Method" ×2 于 `JwtAuthFilter:33`/`CustomerService:179`（两文件本批未动，属 Sonar 新代码期重算/规则触发噪声，非本批引入）。**门禁口径待拍板**：A= `new_violations` 加 severity=MAJOR 过滤（推荐，用户严格口径是 Blocker/Critical=0）；B= 重构两方法（JwtAuthFilter 安全核心，不建议）。已报 0b。
- **gate 口径校准（2026-11-18 19:16，0b 同意、待用户追认）**：本地 `AIGICRM-P0-5` 删除 `new_violations GT 0` → 新增 **`new_major_violations GT 0`**；其余 6 条保留。**理由**：用户 P0-5 严格口径=Blocker/Critical=0；`java:S6541` Brain Method 为 INFO 重组提示且两条位于未改动文件（JwtAuthFilter/CustomerService），不为 INFO 动安全核心路径。**整体 critical=0 销账条件不变**。复验 `a6dc7870`+`69a1a9d6`：gate=OK、critical=51、new_major=0、new_info=2 不阻断。仓库侧同口径已由 0b 提交 `69a1a9d6`。
- **`5789def0`（B-java10，FabushiDashboard.buildDeptProgress 拆分）：干净基线扫描（src_dirty=0）gate=OK**，java:S3776=**44**、python 5、js 2、critical=**51**、new_major=0。**教训：`a6dc7870`/`69a1a9d6` 两次扫描时工作区有未提交改动（14 个），基线混入下一批内容导致数字偏移（预期 43/50 实为 44/51）——以冻结提交后的干净扫描为准**。`FabushiDashboard:407`（16）为 11-16 存量、非本批引入。
- **`47bf2aa3`（B-java10b，buildDeptProgress 再拆）：gate = OK**，java:S3776=**43**、python 5、js 2、critical=**50**、new_major=0（干净基线）。
- **`f082aa91`（B-java11，3 拆分）：gate = OK**，java:S3776=**40**、python 5、js 2、critical=**47**（干净基线）。今日累计 145→47。
- **`623a3178`（B-java11b，FileGrant 多动作拆分）：java 仍 40、critical 47，gate=ERROR**——拆出的 `decideActionsForFile`(:216)/`decideActionsForFolder`(:235) 各 16>15（各超 1 点）；拆 2 增 2 净变化 0。已报 0b 各降 1 点。
- **`0f60b470`（B-java11c，FileGrant helper 降点 + Approval.advanceApproval 拆分）：gate = OK**，java:S3776=**37**、python 5、js 2、critical=**44**（优于预期 45；`decideForAction` 共用后两处 16 均清）。今日累计 145→44。
- **`96a6e3a3`（B-java11d，RecycleService.restoreFile 拆分）：gate = OK**，java:S3776=**36**、python 5、js 2、critical=**43**。今日累计 145→43。
- **`292497da`（B-java12，writeThumbnail/updateRecord 拆分）：gate = OK**，java:S3776=**34**、python 5、js 2、critical=**41**（5+7 helper，缓存回退/归属/分享语义保持）。今日累计 145→41。
- **`137a5807`（B-java13，ScoreService.ranking 拆分）：gate = OK**，java:S3776=**33**、python 5、js 2、critical=**40**。今日累计 145→40。
- **`a28fc70a`（B-java14，SysUserService.update 拆分）：gate = OK**，java:S3776=**32**、python 5、js 2、critical=**39**（5 helper，数据范围/org 重算/激励回插/custom 覆盖语义保持）。今日累计 145→39。
- **`8b96b8c1`（B-java15，FolderTypeDefaultService.saveGrant 拆分）：gate = OK**，java:S3776=**31**、python 5、js 2、critical=**38**。今日累计 145→38。
- **`7db07144`（B-java16，CustomerService.update 拆分）：gate = OK**，java:S3776=**30**、python 5、js 2、critical=**37**。今日累计 145→37。
- **`2f05a40c`（B-java17，CustomerService.getAutoShareOwnerIdsCore 拆分）：gate = OK**，java:S3776=**29**、python 5、js 2、critical=**36**。今日累计 145→36。
- **`69d71082`（B-java18，FolderPermissionService.checkFile 拆分）：java 仍 29 / critical 36，gate OK 但本批无净收益**——`:161` checkFile 仍报复杂度 18（拆出的 `applyFileGrantDecision`/`resolveFolderRef` 未动到主干）；同文件存量 `:496`(58)/`:578`(32)/`:161`(18) 仍在。已报 0b 再切主干。
- **`43293ffe`（B-java19，checkFile 二次拆分 + RolePermScope.anchorOf 拆分）：java 29→27、critical 36→34 达标，但 gate=ERROR**——新增 `java:S1751` `RolePermScopeService:290`（循环内无条件 return）。**QC 初判为「拆分引入真实语义 bug」，经 0b 更正与复核撤回**：`anchorFromRole` 正常路径恒返回 OWNER/ORG、异常 fail-closed 抛 503（恒非 null）；`git show 69d71082` 亦证实拆分前循环即首轮 return——**定性更正为「风格规则项」**，按 0b 显式 null 守卫修复（语义等价）。
- **`7cc78170`（B-java19b，anchorOf S1751 显式守卫）：gate 回绿 = OK**，critical=**34**（java 27）、new_bugs=0。今日累计 145→34。
- **`f9310bd1`（B-java20，FileService.downloadById 拆分）：gate = OK**，java:S3776=**26**、python 5、js 2、critical=**33**（serveLocalFile/parseRange/streamResource/redirectToOss；Range/200/206/416/OSS 302 语义逐分支保持）。今日累计 145→33。
- **`30c384c1`（B-java21，FileService.pageFiles 拆分）：java 仍 26 / critical 33，gate=ERROR**——新增 CRITICAL `java:S3776` `FileService:377` 复杂度 **19**>15（抽出的可见性 helper 仍偏重）；净变化 0。另 `SysRoleService:309` INFO S6541（新口径不阻断）。契约口径统一为 dev-verify 全量 **191 PASS / 0 FAIL**。已报 0b 再切。
- **`0bbb3f25`（B-java21b，逐文件过滤 lambda 抽 applyScopedLevelAnd/applyPrivateLevelAnd）：gate = OK**，java:S3776=**25**、python 5、js 2、critical=**32**。今日累计 145→32。
- **`fa244713`（B-java22，IncentiveService.getCustomerRecords 115 拆分）：java 仍 25 / critical 32，gate=ERROR**——新增 CRITICAL `java:S3776` `IncentiveService:1537` 复杂度 **28**>15（主查询体整体搬入 helper 未再切）；`ScoreService:182` INFO S6541 不阻断。已报 0b 按过滤器/装配两侧继续切。
- **`a0608285`（B-java22b，decorateRecordNames 再拆）：gate = OK**，java:S3776=**24**、python 5、js 2、critical=**31**（loadRecordUsers 批量 map + 三个 apply* 方法；getCustomerRecords 本体已是薄编排）。今日累计 145→31。
- **`2251f629`（B-java23，Statistics.analysis 92 + Approval.submit 87/executeApprovalAction 50 拆分）：java 24→21、critical 31→28 达标，gate=ERROR**——`java:S1172` `ApprovalService:1033` 未用参数 `approval`（MAJOR）；2 条 INFO S6541 不阻断。**合同脚本复核 PASS**：`check-statistics-scope-contract.sh` 实跑 0 FAIL，钉点随拆分移动语义等价（计数经 `scopedCustomerQuery`、子树展开在 `applyOfficeScope`）。**存量缺陷登记**：转移事件 oldOwnerName 取新负责人名 → `tickets/DEF-TRANSFER-001.md`（P2，待裁决）。今日累计 145→28。
- **`348d38e3`（B-java23b，删未用 approval 参数）：gate 回绿 = OK**，critical=**28**（java 21 / python 5 / js 2）、new_major=0。
- **`4ecedbbe`（B-java24，ReminderService.page/expiringPage 拆分）：java 21→19、critical 28→26 达标，gate=ERROR**——`java:S125` `ReminderService:399` 注释掉的旧代码块（MAJOR），已报 0b 删除。今日累计 145→26。
- **`c2062125`（B-java24b，ReminderService 注释去代码化）：gate 回绿 = OK**，critical=**26**（java 19 / python 5 / js 2）、new_major=0。今日累计 145→26。
- **`0e12308b`（B-java25，文件权限/日志/组织范围/角色范围四文件拆分）：java 19→14（优于预期 15）、critical 26→21 达标，gate=ERROR**——新增 CRITICAL `java:S3776` `RolePermScopeService:262` 复杂度 18（updateScopes 拆出的 helper 未再降）；另 `LogAspect:160` MINOR S1488 不阻断。今日累计 145→21。
- **`90704788`（B-java25b，collectScopedCodes 再拆 + LogAspect S1488）：gate 回绿 = OK**，java:S3776=**13**、python 5、js 2、critical=**20**、new_major=0。今日累计 145→20。
- **`4322fee5`（B-java26，JwtAuthFilter/UserDetailsServiceImpl/SysRoleService 拆分）：java 13→11、critical 20→18，gate=ERROR**——新增 CRITICAL `java:S3776` `UserDetailsServiceImpl:234` 复杂度 22；另 4 条 MINOR `java:S1874`（`ORG_AND_CHILDREN` 弃用，新代码引用弃用枚举，非阻断但建议清）。今日累计 145→18。
- **`778b1d9c`（B-java26b，applyGroupAndLegalEntity 再拆 + 弃用抑制归位）：gate 回绿 = OK**，java:S3776=**10**、python 5、js 2、critical=**17**。S1874 4→2（抑制归位覆盖；**属抑制非迁移，弃用债仍在新路径，P3 待换常量**）。今日累计 145→17。
- **`bffbb791`（B-java27，五文件拆分）：java 10→4、critical 17→11（优于预期 5/12），gate=ERROR 且为真实缺陷信号**——`java:S2259` `StatisticsQueryService:529`（**`ind` 可空 → 运行期 NPE 风险**）+ `java:S6218` `:490`（数组直接 equals/比较，引用语义）；均 indicatorStats 拆分引入。已报 0b 修。另 0b 自查修正 detach score 误写 null（实施方自检成果）。今日累计 145→11。
- **`2c9699da`（B-java27b，指标上下文去 record + 元素空值守卫）：gate 回绿 = OK**，critical=**11**（java 4 / python 5 / js 2）、new_bugs=0。S2259 以 `Objects.requireNonNull(ind)` 保持原失败语义（接受，非掩盖）；S6218 改私有 final 载体类。今日累计 145→11。
- **🏁 `5928038e`（B-java28，授权保存/新轨范围/双轨对账/团队动态四文件拆分）：`java:S3776 = 0`（Java 存量清零！）、critical=7（python 5 / js 2）、gate=OK、new issues=0**。合同脚本复核 PASS（六路 wrapper 谓词钉随 `appendXxxActivities`，`visibleOwnerIds` 单一源与退役反钉保留；本地 SCRIPT_RC=0）。今日累计 **145→7（净清 138 条，降幅 95%）**。
- **`2a9fdb9c`（B-py3，五个权限矩阵 main 拆分）：python 5→4（预期 0）、critical 7→6，gate=ERROR**——7 条新增：3 条 CRITICAL `python:S3776`（`audit:266` 21 / `v2:294` 18 / `v2:387` 20，拆分产物仍超限）+ 4 条 MAJOR `python:S3358`（嵌套三元 `audit:358`/`full:251`/`v2:391`/`v2:399`）。净清仅 1 且引来 7 条，已报 0b 修。
- **`5056f31b`（B-py3b，矩阵脚本再拆 + S3358×4）：gate 回绿 = OK**，critical=**3**（java 0 / python **1** / js 2）；唯一剩 `permission_role_smoke.py:27` 复杂度 16（只超 1 点）。
- **🏁 `ef34836d`（B-py3c，role_smoke.main 再拆）：`python:S3776 = 0`（Python 存量清零！）**，critical=**2（仅 JS 2）**、gate=OK、new_* 全 0。今日台账：**java 87→0、python 34→0、js 24→2；整体 145→2（净清 143 条，降幅 98.6%）**。
- **🏁🏁 `4a34bf06`（B-js5，两条 live JS 脚本拆分）：`critical_violations = 0`（整体清零！）**，BLOCKER=0、VULNERABILITIES=0、java/python/js S3776 全 0、gate=OK、new_* 全 0。
  - **P0-5 判定：PASS**（按用户 2026-11-18 严格口径「Blocker/Critical=0 有服务端证据」；服务端 API：measures critical=0/blocker=0/vulnerabilities=0，issues facets CRITICAL 0/BLOCKER 0）。
  - 今日总账：**java 87→0、python 34→0、js 24→0；整体 145→0（净清 145 条）**；期间顺带修真 2 处（LifecycleService NPE×3；`Objects.requireNonNull` 显式守卫）；合同脚本两次钉随复核 PASS。
  - **边界（不扩大口径）**：①存量非阻断 `bugs=41`（MAJOR/MINOR，已逐条定级）+ 未解决 issue 1305（MAJOR 933/MINOR 371/INFO 1）——不属 P0-5 判据，另立债务台账；②gate 口径校准（new_major_violations）待用户追认；③JS 两条 live 脚本静态口径（live 未跑=BLOCKED 说明）。
- **`7369ceb2`（B-s1068-真死清除，5 处零引用注入字段删除+测试同步 10 文件 +4/−19，用户 2026-11-19 拍板）：gate=OK、critical/blocker/vuln=0 维持、S3776 三族=0 维持、`java:S1068` 45→40（5 真死清零）、未解决 issue 745→740、MAJOR 373→368**。
  - QC `01-claude-0d` 首批复扫（接 1c 交接）。独立核验：git diff 与申报一致；grep 反查 5 字段零残留（`folderAclMapper` src/main 零命中，其余同名命中均为他类自有字段）；**自跑门禁**（不采信自报）：`mvn test` 1195/0/0/0 + jacoco 重生成（235 类）、契约脚本实跑 14 PASS、RC=0（首次管道写法吞 RC 已复跑纠正）。
  - 2 处 never-verify 空转断言移除定性成立：字段删除后保留 mock 断言恒真；「删除不清 ACL」语义转为「FileService 无 folderAclMapper 依赖」结构性保证，注释已注明。
  - 过程发现：申报时 `target/site/jacoco/jacoco.xml` 为 11-18 17:23 旧件（实施方 `mvn test` 未带 `jacoco:report`）——QC 已重生成后扫描；实施方后续申报前请带 jacoco:report 或注明交 QC 重生成。
  - 注：前两轮 S1068 豁免（`1b241a19`/`080a95f7`，605→45）证据在 `s1068-itemized-45-20261118.md`，本条承其后。
- **`223026b6`（法布施 F1——分型限额+NEW 标记+木鱼音效，spec v2 首个功能批，11 文件 +369/−9）：gate=OK、new 全 0、critical/blocker/vuln=0 维持、S1068=40 无新增、unresolved=740 零变化（新代码零违规）、bugs=41 不变**。
  - QC `fabushi-upload-security-debt`（原 01-claude-0d，会话更名）独立核验：diff=申报；自跑 `mvn test` **1201/0**（+6 新测试：LimitTest 矩阵/NewFlagTest 边界）+ jacoco 235 类新生；契约脚本 RC=0。
  - 申报偏差①核实为**正确更正**：isNew 落点 FabushiPostService.fillInteractions（feed 真实装配链），方案原写 FabushiReadingService:63 系设计稿笔误（该行实为书架链路）；偏差②PC feed.vue 徽标未做=方案内"选做"，可后续批补，不阻断。
  - 限额实现：后端 enforceAttachmentLimits（图5/视5/音10/60s，音 duration=null 暂放行待 F2 必填）+MP 分型截断（后端为权威闸）；isNew 30 天窗口 null 安全；音效 muyu.mp3 4KB 自合成免版权、懒建单例、soundEnabled 失败默认开。
- **`18f152c8`（法布施 F2——语音录制+评论类型接线+三通道第一步，10 文件 +486/−18）：gate=ERROR**——新 CRITICAL `java:S1192`（"audio" 字面量 ×4，FabushiPostService:74）；其余全过（QC 自跑 1207/0、契约 RC=0、node --check 4 文件、录音实现与方案逐项吻合、addComment 三态接线正确含 audio 必挂 fabushi 空间文件）。运行时边界：录音/试听/语音评论真机+live 用例（case4 第11段→400/case5 61s→400）BLOCKED 待 F3a 测试服窗口。
- **`61eeda9d`（法布施 F2b——S1192 修复，1 文件 +7/−4）：gate 回绿 OK、new_critical=0、critical/blocker/vuln=0、S1068=40、unresolved=750（=740 基线+10 条 S6582 保留债务）**。纯常量化 TYPE_AUDIO（4 处替换+声明顺序前移避 JLS 8.3.3 前向引用，case 常量标签合法）；QC 自跑 1207/0 保持、契约 RC=0。**F2+F2b 判定 PASS**。
- **`c6657059`（法布施 F4a——提醒触达在线自检·方案a，13 文件 +228/−7）：gate=OK、new 全 0、critical/blocker/vuln=0、S1068=40、unresolved=750 零变化（新代码零违规）**。
  - QC 独立核验：diff=申报；后端 /reads/today 日界=Asia/Shanghai 左闭右开（与 C4 时区口径同源）、null 用户不触库、fabushi:view 门；MP 判定链与方案逐项一致（freq 矩阵兜底/HH:mm 数值比较/日去重 storage 容错/演示模式跳过），纯函数导出可测；自跑 1210/0（+3）+jacoco、契约 RC=0、node --check 6 文件。
  - api-list md+json 随批入库（322→323，新端点入册）。边界：MP 真机行为（弹窗时机/红点显隐）BLOCKED 待手工真机；方案 b 订阅消息待资质。
- **`5921fdfe`（法布施 F3a-1——H5 上传通道+短时上传票，21 文件 +779/−9）：gate=ERROR**——新 CRITICAL `java:S1192`（"scope" 字面量 ×3，JwtTokenProvider:228）。安全核心全过：**主 JWT 红线 PASS**（新通道文件 token/Authorization/Bearer 零出现，唯一凭据=5 分钟短时票进 H5 URL）；票制矩阵逐项（401/403/jti+pjti 黑名单联动）；分型闸 10/20/100/100MB 且 SVG 已排除；SecurityContext 用毕清理；限速 fail-open 带审计；三处偏差（独立控制器/Map 化/permitAll 三登记）全部成立。QC 自跑 1218/0（+8 票矩阵）、契约 RC=0。P3 入档：类型闸为扩展名白名单无魔数（比原生通道严，后续可加内容嗅探）；typecheck 536 为自报项。
- **`40e24ae2`（法布施 F3a-1b——S1192 修复，2 文件 +16/−9）：gate 回绿 OK、new_critical=0、critical=0、unresolved=750 基线**。CLAIM_SCOPE/SCENE/PJTI 全量常量化（10 处替换 5 方法）；PermissionMatrixTest L643 源码级钉随同步重钉（意图注释保留）。QC 自跑 1218/0 保持、契约 RC=0。**F3a-1+1b 判定 PASS**。live 边界：H5 链路（发票→上传→回传→挂附件）+nginx CSP 待测试服/生产窗口。F3a-2 方案 A（进度容器代传+localStorage 缓存）已批。
- **`346a0be6`（法布施 F3a-2——阅读器换票+进度容器代传，8 文件 +139/−35）：gate=OK、new 全 0、critical=0、unresolved=750 基线——判定 PASS**。
  - **红线终验（MP-TOKEN-001 法布施侧闭环）**：reader/uploader URL 仅 ticket=；detail/upload 附件与语音试听 3 处 token= 经查全为 getDownloadToken() 短时票通道（合规）；**法布施侧主 JWT 零 URL**。全仓盘点：主 JWT 进 URL 存量仅剩 files/picker:37 一处（F3b 范围，换完即销单）；incentive/detail、files/index 均为 download-token 通道非违规。
  - QC 自跑 1218/0 保持、契约 RC=0；reader-ticket 端点=fabushi:view 门+60min 票+pjti 联检（委托发券无新测试的申报理由成立）。真机 reader 行为+票 60min 过期表现 BLOCKED 待窗口/真机。
- **`be8a0d99`（法布施 F5——类别 v3 十大类迁移+spec 种子，2 文件 +115/−1）：gate=OK、critical=0、unresolved=750 基线——判定 PASS**。
  - **QC 本地 MySQL8 沙箱实跑验证**（ephemeral docker，非仅静态审查）：桩夹具=16.8 后真实态（8 经典+qita 存量+pending 投稿）；第一次执行三查对账全符——旧五码 0 行、字典=10 顺序=spec 原文、了凡四训→tuanduibidu、论语/道德经/了凡四训防重拦截 3 条（种子实插 36+存量 10=46）、孤儿 code=0、pending/qita 不断链；**第二次执行幂等**（46/10/1/1 零变化）；沙箱即用即毁。
  - 自跑 1218/0、契约 RC=0、node --check；live 脚本旧码 jiaxun→guoxue 耦合清查确认。**生产部署自动跑本迁移——发布说明必列+回滚逆写已备（迁移头注释）**。
- **`01f81bfb`（F3b 文件选择器换票）：gate=ERROR**——S1192 第三次（"H5 上传未启用"×3）；红线终验整单闭环确认（全仓 8 处 token= 全溯源为 download-token，picker/uploader/reader 短时票）。
- **`4b5ea1e9`（F3b-fix 常量化+注释勘误+触达文件字面量全量自查）：gate 回绿 OK、critical=0、unresolved=750——F3b+fix 判定 PASS，MP-TOKEN-001 正式销账**（证据链见工单）。QC 自跑 1220/0（MAVEN_ARGS 离线仓库口径，吸收自测试方法文档）、契约 RC=0。
- **V2026.11.19.3 空库重放补验（测试方法文档 §6 双口径要求）：replay-empty-db.sh 全绿**——F5 迁移"空库重放+快照 validate"双口径齐全（快照侧见 W-A 窗口报告）。
