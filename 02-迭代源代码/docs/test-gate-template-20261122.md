# AIGICRM 严格测试门禁模板 v1

> 【草案·待用户确认 2026-11-22】确认后转正为交付强制标准。
> 单一规则源：donglai-engineering-loop 技能（失败回流=其附录A状态机；完成条件=其完成条件章节）+ 项目 CLAUDE.md「P0 强制质量门禁」。
> 本文只收口与补缺，**不另立第二套规则**；所有命令均为仓库现有真实入口。
> 两个既有强制件的分工（2026-11-22 核实）：`gate.sh` = 交付全集门禁（契约全集+MP+文档新鲜度+INV+类型基线）；`quality-gate.sh` = P0 单任务报告表出口（修改范围→三端→VSCode 诊断→验证报告表，PASS/FAIL/BLOCKED 三态）。两者互不调用，收口建议见待建C。

## 0. 八条硬规则（不可协商，违反即门禁失败）

| # | 规则 | 依据 |
|---|---|---|
| H1 | **交付唯一裁决 = `bash scripts/gate.sh` 完整档退出码 0**。`--quick` 仅限迭代中间自检，不得作为交付依据 | gate.sh 头注；auto-pipeline 退役教训（绕门禁直通生产的通道已封死） |
| H2 | **fail-closed**：预检缺工具（rg/python3 等）= FAIL；任一门禁工具不可用 = 记「未通过」，不得视为通过 | gate.sh [0]；技能跳转规则 |
| H3 | **完成=证据，不是声明**：每个「已完成/已修复」必须附新鲜证据（原失败检查重跑 + 相关回归 + 退出码）。无证据 → 状态保持 `blocked/未验证` | 技能 before-completion-claim；45 会话复盘 57.8% 假完成 |
| H4 | **基线只减不增**：类型检查基线（当前 536，以 gate.sh 内基线数为准）只减不增；契约因功能改动变红时，按 dev-verify.sh「契约更新责任表」同提交更新 | gate.sh [9] 转正注记；[5b] 责任表 |
| H5 | **禁止换绿清单**：禁用测试、删断言、`@ts-ignore`/吞异常、降低门禁、跳过用例——任何一条出现即违规，走回流修复而非绕行 | 技能自动修复原则 |
| H6 | **逃逸门显式化**：`AIGI_TSC=0`、`--quick`、跳过任一必需层的每次使用，必须在证据里记录【谁批准+原因+补救时间】 | gate.sh 逃生门设计 |
| H7 | **失败必回流**：任一门禁失败 → 附录A状态机（SCAN→CLASSIFY→REPRODUCE→CHECKPOINT→REPAIR→REVALIDATE），每轮只修一个根因；不得以重试次数用尽为由放弃有证据支撑的修复 | 技能附录A |
| H8 | **证据归档且无密钥**：全部证据进 `东来/Agent/00-治理/项目执行/01-AIGICRM/runs/<run-id>/evidence/`，禁止散落项目目录；凭据只经 `~/.aigicrm-deploy.env` 注入 | 技能状态文件规则 |
| H9 | **三态输出**：任务最终状态只能是 `PASS / FAIL / BLOCKED`（quality-gate.sh 语义）；「基本通过 / 应该没问题 / 大概率没问题」为禁用表述。环境缺失 → BLOCKED ≠ PASS，如实标注 | CLAUDE.md P0 第一节 |
| H10 | **AI 代码验收线六条**（AGENTS.md 同名单一篇章）：证据可复现（file:line+命令+退出码）、生成物优先（权限码/api-list 走生成器）、**负例自证**（新增/修改的检查必须先证明「改坏会红」）、**证据阶梯不降级**（登录/权限/文件/财务/审批关键路径须集成或运行态证据，不得停在 mock+文本契约）、迁移双口径（空库重放+快照 validate）、变更面收口 | AGENTS.md AI 代码验收线 |

## 1. 分层门禁 G0–G7

### G0 · 预检（每次运行开始，fail-closed）
- **命令**：gate.sh [0] 段（内含依赖预检）+ `git status` 记录基线
- **判据**：工具齐备（rg/python3/node/mvn/JDK21）；分支明确；工作区用户改动已登记保护
- **失败**：缺工具直接 FAIL，不进入后续段（避免契约假红）

### G1 · 静态与契约层
- **命令**：`bash scripts/dev-verify.sh contracts`（70+ 契约脚本全集 + F1 全量 secret 扫描）
- **判据**：退出码 0；类型基线不增（gate.sh [9] 默认跑）
- **失败**：契约红 → 判断是「代码错」还是「契约需随功能更新」（H4 责任表）；secret 扫描红 → 一律代码侧修

### G2 · 单测层（三端）
- **后端**：`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` → `cd aigicrm-backend && MAVEN_SKIP_RC=true mvn -B clean verify`（编译+单测/集成+打包一体，含 ArchUnit 分层约束、权限矩阵架构钉、稳定性不变量钉；必须 JDK21 与 pom 一致）
- **前端**：`cd aigicrm-ui && npm run test`（vitest run）全绿，**0 Failed、无未经说明的 skipped**；lint 未配置 → 如实标 `N/A`，不得虚报 PASS
- **小程序**：`bash scripts/dev-verify.sh miniprogram`（语法 + JSON + 空视图 + 视图绑定契约 + 静态类名 + app.json 页面完整性）
- **失败**：附录A回流；先复现再修，补回归用例后原失败检查必须重跑

### G3 · 构建与启动层
- **命令**：`npm run build`（gate.sh [3]）+ vite preview 实际加载 dist（只编译能跑但启动不了 ≠ PASS）
- **启动验证**（CLAUDE.md P0 第2.4）：`docker-compose up -d` 或 `mvn spring-boot:run`——启动日志无致功能失效 ERROR、健康检查与关键接口正常、MySQL/Redis 连接正常
- **判据**：构建退出码 0；产物含 index.html 与入口资源；文档新鲜度（gate.sh [4]：manifest/business/REQUIREMENTS 落后三端源码 ≤3 天）

### G4 · 专项层（按变更类型触发，见 §3 路由）
| 专项 | 命令/方式 | 触发条件 |
|---|---|---|
| 权限矩阵回归 | `AIGI_MATRIX=1 bash scripts/gate.sh`（verify-perm-matrix.sh，需测试环境账号） | 改权限/角色/组织/菜单/数据范围 |
| 小程序真机渲染走查 | `AIGI_MP_WALK=1`（需微信开发者工具+自动化端口 9424） | 改 MP 页面/视图绑定；43 页全量走查按守则 D7/D8 |
| 业务不变量 | gate.sh [5] INV 系列（清单 docs/business/invariants.md） | 改业务流转/停用逻辑 |
| **web-view 内核兼容** | 【待建A，见 §5】产物级 grep + Playwright 删 API 模拟最低内核 | 改 web-view 加载的 H5（pdfjs/新 API 用点） |
| 数据库迁移 | `check-migration-chain-contract.sh`（在契约全集内）+ 破坏性变更**人工确认** | 任何 db/migration 改动；deploy-db.sh 前演练 |
| 性能 | `check-prod-perf.sh` | 统计/列表类大查询改动 |
| SonarQube 服务端 | `check-sonar-status.sh` | **可选发布增强**：不可用不阻断本地闭环，但必须记「服务端门禁未完成」 |

### G5 · 交付门禁（提交/合并前）
- **本地**：`bash scripts/gate.sh` 完整档（含 [6] 工作区检查）+ `bash scripts/quality-gate.sh`（P0 报告表出口，退出码 0=PASS/1=FAIL/2=BLOCKED）
- **判据**：两者退出码均 0；交付级工作区必须干净（[6] 迭代中仅告警，交付时人工确认）；VS Code 诊断导出完成（check-vscode-diagnostics-export.sh）
- **远程**：push 后 CI 必绿（git 根 `.github/workflows/ci.yml`：mvn verify+ArchUnit / schema↔entity / api 接线 / 权限串交叉核对 / vitest / node --check / SonarCloud）
- **证据**：完整输出 + 退出码 + commit hash 存 evidence/

### G6 · 上线门禁（部署时，顺序固定）
```
gate.sh 完整档 0 →  deploy-db.sh(如有迁移)  →  deploy-backend.sh(md5门禁)  →  deploy-frontend.sh
```
- deploy-frontend.sh 内置：env 钉扎断言 → 架构自检 → TLS 只读预检 → 暂存解包校验 → 备份旧 dist → 原子切换入口 → 公网验证（首页/入口资源/健康检查）→ 失败自动回滚
- **【待建B】上线后内容级核验**（本次 K-ENG-FE-001 事故补位）：部署脚本只验 HTTP 200，不验「修复真的在线上」。核验=线上 chunk 与本地 dist 关键标记逐字节比对（如本次 polyfill 标记 ×24/×22/×6）+ 入口 sha256 一致
- **回滚判据**：任一验证失败 → 用 `/Aigicrm/files/backup/dist-rollback-<ts>` 回滚；人工回滚决定记证据

### G7 · 上线后门禁（本次事故暴露的最大真空档）
- **真机回归清单**：按本次变更功能出 3–5 条真机用例（模板：①原故障路径复测 ②主流程 ③数据留存如进度续读 ④退出重进），**用户执行**，结果回填 evidence/ 才算闭环
- **深度冒烟（可选）**：`scripts/smoke-test.sh prod`（需有效冒烟账号；deploy 脚本内置的无凭据探活不算深度冒烟）
- **观察期**：上线后 24h 内产线报错监控（如有 vConsole/日志通道则查；没有则用户侧收集）
- **闭环登记**：事故编号（如 K-ENG-FE-001）→ 修复 → 门禁 → 部署 → 线上核验 → 真机回归 全链证据齐 → 才能标记 verified

## 2. 三端 × 层级矩阵（●=必跑 ○=按触发）

| 层 | 后端 | 前端UI | 小程序 | DB/部署 |
|---|---|---|---|---|
| G0 预检 | ● | ● | ● | ● |
| G1 静态契约 | ● | ● | ● | ● |
| G2 单测 | ● mvn | ● vitest | ● dev-verify | ○ 迁移契约 |
| G3 构建 | ● package | ● build | — | ○ |
| G4 专项 | ○ 矩阵 | ○ 内核兼容 | ○ MP走查 | ● 迁移演练 |
| G5 交付 gate.sh | ● | ● | ● | ● |
| G6 上线 | — | — | — | ● 三脚本链+内容核验 |
| G7 上线后 | ○ | ○ | ● 真机回归 | ● 回滚判据待命 |

## 3. 变更类型路由（改了什么 → 必跑哪层）

| 变更类型 | 必跑层 | 追加专项 |
|---|---|---|
| 后端业务代码 | G0–G5 | 涉积分/规则引擎补单测（AGENTS.md 强制） |
| 前端 UI/阅读器 | G0–G5 + G7 | web-view 内核兼容（待建A） |
| 小程序页面 | G0–G5 + G7 | MP 走查（D7/D8）；真机回归必做 |
| 权限/角色/组织 | G0–G5 | 权限矩阵回归 + 双端绑定契约 |
| DB 迁移 | G0–G6 | 迁移链契约 + 空库重放（replay-empty-db.sh）+ 破坏性人工确认 |
| 仅文档 | G0 + G3 文档新鲜度 | 不跑 G2（不写镜像测试） |
| 部署/配置 | G5 + G6 + G7 | prod-config 契约 + 内容级核验 |

## 4. 证据规范（每轮门禁）

目录：`东来/Agent/00-治理/项目执行/01-AIGICRM/runs/<run-id>/evidence/gate-<timestamp>/`
- `gate-full.txt`：gate.sh 完整输出 + 退出码 + commit hash
- `report.md`：**强制验证报告表**（CLAUDE.md P0 第五节格式：门禁/实际命令/退出码/结果 七行表 + 测试总数/成功/失败/跳过 + 实际验证场景 + **未验证内容** + 剩余风险 + 修改文件清单）
- `special-<name>.txt`：各专项输出（矩阵/走查/内核兼容）
- `deploy.txt`：部署脚本输出（含备份路径、入口 sha256、验证结果）
- `live-verify.txt`：线上内容级核验记录（待建B 脚本化后自动产出）
- `device-regression.md`：真机回归清单 + 用户确认结果 + 日期
- 禁止含密钥；逃逸门使用记录（H6）同目录

## 5. 待建项（本模板转正后立项，各挂事故/守则编号）

| # | 待建 | 内容 | 来源 |
|---|---|---|---|
| A | `verify-webview-kernel-compat.sh` | 产物级 grep 关键 API polyfill 标记 + Playwright 删除 `Map.prototype.getOrInsert*`/`toHex` 等模拟最低内核，跑 PDF 打开正例。**自带负例自证**（H10：删 API 后检查必须先红一次） | K-ENG-FE-001（本次手工专项固化） |
| B | `verify-deployed-content.sh` | 部署后自动比对线上 chunk 与本地 dist 的标记计数 + 入口 sha256 | K-ENG-FE-001（本次手工比对固化） |
| C | 双一键门禁收口 | quality-gate.sh 头注仍写「后端 mvn verify(JDK17)」已过期（现 JDK21）；且与 gate.sh 互不调用、职责重叠——建议 quality-gate.sh 收敛为 gate.sh 的报告表层（或头注注明分工） | 2026-11-22 盘点核实 |

## 6. 角色分工

- **Agent**：G0–G6 全部执行与证据归档；G7 出清单并催办
- **用户**：G7 真机回归执行；H6 逃逸门批准；破坏性 DB 变更确认；生产回滚拍板
