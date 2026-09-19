# AIGICRM Agent 规则

本项目是独立工作区。Agent 只读取本项目的需求、源码和规则，不跨项目引用 ShopJoy Mall 的上下文。

## 主要入口

- 源码：`02-迭代源代码/`
- 后端：`02-迭代源代码/aigicrm-backend/`
- 前端：`02-迭代源代码/aigicrm-ui/`
- 测试与质量：`05-测试与质量/`
- 部署运维：`06-部署与运维/`
- 生产备份：`08-备份索引/`，只读处理，禁止修改或提交其中内容。

## 修改规则

1. 修改前先确认需求、影响范围和构建入口。
2. 不触碰生产备份、SSH 密钥和真实环境凭据。
3. 后端使用 Maven，前端根据 `package.json` 执行检查和测试。
4. 完成后先运行最小相关测试，再运行 SonarQube 检查。

## 标准代码质量闭环（每次改代码必须执行）

代码修改不是完成的终点，必须把整个 VS Code 项目文件夹及其子文件夹中的所有受支持源码文件纳入 SonarQube/SonarLint 问题面板；不能只检查当前打开的单个文件：

1. 修改前记录当前 Git 状态，确认只处理本项目和本次需求范围。
2. 先运行最小相关测试，确认基线；再编辑代码。
4. 编辑后等待 VS Code SonarQube/SonarLint 对整个工作区分析完成，读取 Problems 面板和 SonarQube 面板的实际问题；按文件逐条定位，不凭感觉判断质量。
5. 按问题逐条修复：优先 Blocker/Critical/High，再处理 Major/Minor/Info；每轮只处理已定位的根因，不得通过关闭规则、降低级别、屏蔽文件、删除断言或吞异常来清零。
6. 修复后重新触发全工作区分析：变更文件问题必须为 0，项目全量问题不能新增；若历史问题存在，必须区分“本次新增问题”和“存量问题”，不能用全局总数掩盖新增问题。
7. 再运行前端测试/构建、后端 Maven 测试/编译和 SonarQube 项目问题查询，保存命令、退出码、扫描文件范围和问题数到中央执行目录 `/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/runs/<run-id>/evidence/`，不得在业务源码内新增 agent 管理材料。
8. 交付前检查 `git diff --check`、未跟踪文件和迁移文件；报告必须链接到项目内文件，禁止把微信、浏览器或系统临时目录作为唯一证据来源。

### 本项目质量检查入口

```bash
# 前端
cd 02-迭代源代码/aigicrm-ui
npm test
npm run build

# 后端
cd 02-迭代源代码/aigicrm-backend
mvn test
mvn -DskipTests compile

# SonarQube（项目 key 必须使用已验证的 key）
sonar list issues -p Eastdongjun_AigiCRM --statuses OPEN --format json
```

SonarQube 面板出现问题时，必须回到“读取问题 → 修改 → 重分析 → 验证为 0”的循环；不能仅以测试通过、构建成功或 CLI 无输出替代 SonarQube 质量验证。

## 10/10 工程化执行链路

每次项目开发、诊断或审查前，先运行本机工程能力自检，并按共享工程体系生成项目级全景蓝图：

```bash
python3 /Users/dongjun/东来/Agent/04-共享能力/engineering-system/local_capability_audit.py
python3 /Users/dongjun/东来/Agent/04-共享能力/engineering-system/blueprint_planner.py --project AIGICRM.COM --requirement "<用户需求>" --output "/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/runs/<run-id>/blueprint.json"
```

开发必须消费 `blueprint.json` 中的 panorama 和当前 To-Do。每个 To-Do 完成前必须具备：原检查或最小相关测试、相关回归、VS Code Problems/SonarQube/SonarLint 诊断、review、handoff、修改前后 baseline 证据。缺任一项时使用 `task_progress_engine.py` 保持 blocked，不能进入下一项。

交付前运行中央 `engineering_loop.py` 与 `delivery_gate.py`。`delivery_gate.py` 允许只代表本地工程证据足够，不代表生产发布；若用户要求发布级门禁，必须另行要求服务端 Sonar Quality Gate 与部署验证通过。

### 文件范围原则

- 工作区根目录是一次工程质量检查的边界，下面所有业务子项目、前端、后端、脚本和配置文件按 SonarQube 支持范围纳入扫描。
- `node_modules`、`target`、`dist`、上传目录、生产备份和密钥目录必须通过排除规则明确排除；排除必须有理由，不能为了清零随意扩大排除范围。
- 修改一个文件后先处理该文件的 Sonar 问题，再执行工作区级复查；新增文件也必须经过同样流程。

<!-- sonar:begin:codex-secrets-on-read -->
# SonarQube secrets scanning for files protocol

Before reading any file in this workspace, scan it for secrets with the deterministic scanner:

```bash
sonar analyze secrets <path/to/file>
```

If the command reports that the file contains a secret, **do not read the file**. Instead:

1. Inform the user that the file appears to contain a secret or credential and that reading it would expose the value in chat history, logs, and any downstream telemetry.
2. Advise them to rotate the leaked credential at its source of truth and remove it from the file.
3. Do not proceed with the original request until the secret has been removed.
<!-- sonar:end:codex-secrets-on-read -->
