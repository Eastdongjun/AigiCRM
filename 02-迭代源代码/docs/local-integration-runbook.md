# 本地联调手册

本文用于在本机复现 AIGICRM 的 PC 端、小程序端与后端联调。默认只使用本地或测试数据，不填写生产凭据。

## 1. 启动后端

后端默认监听 `http://localhost:8080`，配置入口是 `aigicrm-backend/src/main/resources/application.yml`。

```bash
cd aigicrm-backend
mvn spring-boot:run
```

也可以从源码根目录使用本地启动脚本；它会固定默认本地文件存储并在启动前检查 `JWT_SECRET`：

```bash
JWT_SECRET='仅本机临时值' bash scripts/start-local-backend.sh
```

对隔离的本地 QA 数据库可显式启用 Flyway 自动迁移；`LOCAL_QA_MODE=1` 不应用于共享测试库或生产库：

```bash
LOCAL_QA_MODE=1 \
DB_PASSWORD='本地 QA 数据库密码' \
JWT_SECRET='仅本机临时值' bash scripts/start-local-backend.sh
```

`LOCAL_QA_MODE=1` 会自动使用隔离 QA 容器的 MySQL `127.0.0.1:3307`、Redis
`127.0.0.1:6379`、数据库 `aigicrm` 和用户 `root`；如本机端口或库名不同，再显式覆盖对应环境变量。

生成本地数据字典和权限字典时，密码既可以通过 `LOCAL_DB_PASSWORD` 注入，也可以放在权限为
`600` 的本机临时文件中，通过 `LOCAL_DB_PASSWORD_FILE=/path/to/password` 指定。脚本不会输出密码，临时文件不应放入仓库。

该模式只改变本次进程的 `FLYWAY_ENABLED`，不会修改仓库默认配置；QA 账号和权限矩阵应使用临时容器数据，不复制真实用户数据。

如果本机 Maven 的 Java 版本不满足项目要求，先执行：

```bash
bash ../scripts/ensure-java21.sh -- mvn spring-boot:run
```

后端启动前需要准备项目配置中的数据库与 Redis。无法连接真实依赖时，使用自动化测试验证业务，不把启动失败误判为功能失败。

本地启动还需要通过环境变量提供 `JWT_SECRET`。文件服务使用阿里云 OSS 时，还需要
`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME` 等配置；缺少这些配置时，
后端会在文件服务初始化阶段安全失败，不应填入占位值绕过。日志默认写入后端工作目录的
`logs/`，容器部署通过 `LOG_DIR=/app/logs` 指向持久化挂载目录。

默认 `STORAGE_TYPE=local` 时，文件服务使用 `UPLOAD_DIR`（默认 `./uploads`）落盘，
不创建 OSS 客户端，因此本地免费联调不需要 OSS 凭据。只有切换到
`STORAGE_TYPE=oss` 时，才必须提供完整 OSS 配置。

## 2. 启动 PC 端

PC 端默认监听 `http://localhost:3000`，Vite 已将 `/api` 代理到 `http://localhost:8080`。若使用其他端口，设置 `AIGICRM_FRONTEND_URL` 后再运行 `bash scripts/dev-verify.sh frontend-http`；HTTP 预检优先使用 `curl`，未安装时自动回退到 Node 原生客户端。

```bash
cd aigicrm-ui
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`。登录、权限、切换组织和文件操作均应通过后端接口返回结果验证，不使用浏览器 mock 数据。

## 3. 配置小程序真实后端

免费本地联调可直接以 `config/env.local.example.js` 为模板，将其复制为
`config/env.js`；该模板指向 `http://127.0.0.1:8080`、关闭演示模式且不包含任何凭据。

编辑 `aigicrm-miniprogram/config/env.js`：

```js
module.exports = {
  appName: 'AIGICRM',
  apiBaseUrl: 'http://127.0.0.1:8080',
  demoMode: false
}
```

在微信开发者工具中导入源码根目录：
`/Users/dongjun/东来/项目/01-进行中/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram`。
导入前确认该目录直接包含 `app.json` 和 `project.config.json`；不要选择上一级
`02-迭代源代码`，也不要选择包含其他项目的父目录，否则模拟器会提示“项目根目录未找到 app.json”。
项目配置中的 `miniprogramRoot` 必须保持 `./`，AppID 必须是 AIGICRM 的登记值；可运行
`bash scripts/check-wechat-project-root.sh` 做本地检查。开发阶段勾选“**不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**”。真机调试时不能使用手机无法访问的 `localhost`，应改成同一局域网内可访问的测试地址，并按微信平台要求配置合法域名。

真实后端模式的最小验收顺序：

1. 登录并确认 `/api/auth/info` 返回权限、`groupIds`、`currentGroupId`。
2. 在个人资料切换组织，确认后续客户、提醒、文件请求沿用新的组织上下文。
3. 修改密码后确认 token、用户信息和组织上下文清除，并回到登录页。
4. 使用两个账号验证文件“共享给我”只显示当前接收人可见且未删除的文件。

## 4. 交付前本地检查

从 `02-迭代源代码` 目录执行：

```bash
bash scripts/dev-verify.sh miniprogram
bash scripts/dev-verify.sh backend-http
bash scripts/dev-verify.sh contracts
bash scripts/dev-verify.sh backend-compile
bash scripts/dev-verify.sh backend-package
cd aigicrm-ui && npm test && npm run build
cd ../aigicrm-backend && mvn test
```

`contracts` 是不需要启动后端的本地契约总检，覆盖权限、导航、原型状态、演示数据和后端端点审计；VS Code 任务 **“自动化开发: 本地契约全检”** 使用同一入口。`backend-compile` 和 `backend-package` 会从共享运行环境解析 JDK 21/Maven，不依赖某台机器的 Homebrew 路径。

`scripts/dev-verify.sh backend-test` 默认会把后端复制到临时验证目录后运行测试，避免与正在运行的 `spring-boot:run` 共享 `target`。如需明确复用当前工作区的构建目录，仅限本地诊断时使用：

```bash
AIGICRM_BACKEND_TEST_SHARED=1 bash scripts/dev-verify.sh backend-test
```

`backend-http` 只检查本地后端是否可达、公开 API 文档是否返回 200，以及无凭据访问 `/api/auth/info` 是否被 401/403 拒绝；它不会登录、打印凭据或修改数据。

查看当前全项目源码进度口径（不需要启动后端或连接数据库）：

```bash
bash scripts/dev-verify.sh progress
```

该命令读取统一功能审计和原型状态矩阵中的去重汇总，并显示 PC 路由、小程序页面/API、后端 endpoint 数量、原型页面覆盖、通用状态实现度以及最近中央证据。它是进度观测，不替代测试或真实联调；输出中的外部阻塞项必须保持可见。

在 VS Code 中也可以运行任务 **“自动化开发: 项目进度总览”**，结果会进入 Terminal/Problems 关联的本地开发闭环。

### VS Code 诊断进入中央证据

VS Code 工作区的 Problems 面板由免费 SonarLint、Java/Vue/Shell 等语言服务持续提供诊断。工作区配置把诊断导出到中央 current 通道：

`/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/current/`

完成一次修复后，先等待 Problems 面板刷新，再执行：

```bash
bash scripts/check-vscode-diagnostics-export.sh
```

该检查同时确认原始诊断为空、规范化状态为 `verified:no-vscode-diagnostics`，以及导出请求已经 `completed`。如果仍有问题，检查会失败并保留问题数量；不得通过关闭规则、扩大排除范围或删除诊断来清零。

生成数据库字典时使用测试环境入口：

```bash
AIGICRM_DEPLOY_ENV="$HOME/.aigicrm-deploy.env" bash scripts/gen-data-dictionary.sh test
```

测试服 SSH 账号不能代替数据库账号；运行前需在本机凭据文件中配置
`TEST_DB_ADMIN_USER` 与 `TEST_DB_ADMIN_PASSWORD`，真实值不得写入仓库或日志。

本地 Docker 数据库不依赖部署凭据文件时，使用 `local` 模式。先确保本机容器已经运行，并通过环境变量传入一次性本地密码：

```bash
LOCAL_DB_HOST=127.0.0.1 \
LOCAL_DB_PORT=3306 \
LOCAL_DB_NAME=aigicrm \
LOCAL_DB_USER=root \
LOCAL_DB_PASSWORD='仅本机临时值' \
LOCAL_DB_CONTAINER=aigicrm-mysql \
bash scripts/gen-data-dictionary.sh local

LOCAL_DB_PASSWORD='仅本机临时值' \
LOCAL_DB_CONTAINER=aigicrm-mysql \
bash scripts/gen-permission-dict.sh local
```

本地要检查自动生成字典的新鲜度时，显式使用同一套本地数据库参数：

```bash
DOC_FRESHNESS_MODE=local \
LOCAL_DB_PASSWORD='仅本机临时值' \
LOCAL_DB_CONTAINER=aigicrm-mysql \
bash scripts/check-doc-freshness.sh
```

`check-doc-freshness.sh` 默认仍检查 `prod`，不会因为本地模式而降低生产交付门禁。

`local` 模式不会加载 `deploy-lib.sh`，也不会连接远程主机；如果本地 schema 没有完整的角色、菜单和绑定种子，权限字典脚本必须失败，不能把空结果当作有效字典。

仅在一次性本地库需要权限字典验证时，可手动加载最小 fixture（不会被 Flyway 自动执行）：

```bash
docker exec -i aigicrm-mysql mysql -uroot -p --default-character-set=utf8mb4 aigicrm \
  < scripts/seed-local-permission-fixture.sql
```

该 fixture 明确标记为 local-only，禁止用于共享测试库或生产库。

这些检查证明代码、契约和自动化测试通过；它们不替代真实数据库、微信开发者工具和浏览器中的人工联调。人工联调完成前，蓝图中的“本地一体化部署与全流程验收”仍保持未完成。

## 5. 常见边界

- `demoMode: true` 只用于原型浏览，不作为真实权限或数据隔离证据。
- 不要把 `~/.aigicrm-deploy.env`、数据库密码、JWT 或 Cookie 写入日志、截图或提交。
- SonarQube 服务端分析若返回 403/`NOT_COMPUTED`，只能记录为质量验证未完成；本地 secrets 检查和项目测试不能替代服务端质量门禁。
- Sonar 状态复核可执行 `bash scripts/check-sonar-status.sh`；退出码 `0` 表示局部扫描干净，退出码 `2` 表示认证或分析能力/服务端门禁仍不完整。需要指定诊断桥时使用 `VSCODE_MCP_URL=<url> bash scripts/quality-gate.sh`。
