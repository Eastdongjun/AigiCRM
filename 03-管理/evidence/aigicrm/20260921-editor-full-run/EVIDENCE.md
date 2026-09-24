# AIGICRM 全量编辑器验证证据（2026-09-21）

目标：把 AIGICRM 的全部代码在编辑器里跑一遍，确认无报错。
范围：`/Users/dongjun/东来/项目/01-进行中/01-AIGICRM`，git HEAD `e195ddfe` 基础上，本轮两处改动。

## 本轮改动（最小修复）

| 文件 | 改动 | 原因 |
|---|---|---|
| `02-迭代源代码/aigicrm-ui/src/App.vue` | `.route-loading-box` 的 `color: #409eff` → `#2a76cf` | SonarQube 规则 css:S7924：白底上 #409eff 对比度仅 2.78:1，低于 WCAG 正文 4.5:1 |
| `02-迭代源代码/aigicrm-miniprogram/pages/files/index.js` | `buildFileView` 内重新计算 `showUploadAction` | 该变量原先只定义在 `buildFileRowView` 局部作用域，被 `buildFileView` 引用时触发 `ReferenceError` |

## 验证结果（全部为本机实跑）

### 1. 微信开发者工具（小程序，46 页全量）
- 入口：`Agent/04-共享能力/engineering-system/miniprogram_console_verify.py`
- 门禁：`versionRelation: equal`、`loginExpired: false`、`tokenRequired: false`、已登录"天池登山客"
- 结果：**46/46 页面 compiled=true，with_console_error=0，退出码 0**
- 证据：
  - `03-管理/evidence/aigicrm/20260920-162242-console-verify/miniprogram-console-evidence.json`（修复 `showUploadAction` 后首轮全量）
  - `03-管理/evidence/aigicrm/20260920-163202-console-verify/miniprogram-console-evidence.json`（架构修复复跑确认）

### 2. VS Code 工作区诊断（AIGICRM.code-workspace）
- 入口：`vscode_diagnostics_unattended.py`（隔离 profile，导出后关闭窗口并删除 profile）
- 第 1 轮：`current/aigicrm-editor-run/` → `issues: 1`（sonar css:S7924 @ App.vue）
- 第 2 轮（修复后）：`current/aigicrm-editor-run-r2/` → `issues: 0`
  - 采集范围非空：`diagnosticFiles: 6`、`diagnosticCount: 0`
  - 工作区身份：`workspaceFolders` 两项匹配、`isTrusted: true`
  - 打开文档覆盖 java/vue/python/dockerfile/dockercompose/shellscript
  - → 是**真实 0 问题**，不是空导出

### 3. 前端 aigicrm-ui
- `npx vitest run`：**30 文件 / 188 用例 全部通过**
- `npm run build`：**成功**（vite build，退出码 0）

### 4. 后端 aigicrm-backend
- JDK：VS Code Java 扩展内置 Temurin 21.0.12.1（本机无独立 JDK）
- `./mvnw -o compile`：成功
- `./mvnw -o test`：**Tests run 566, Failures 0, Errors 0, Skipped 0，BUILD SUCCESS**
  - 含 ArchUnit `ArchitectureTest`(3) 与 `PermissionMatrixTest`(27) 架构/权限闸门

## 未覆盖与已知缺口（如实标注）

1. **running 服务未起**：后端 `java` 进程（8080）与前端 dev server 未在本轮启动，因此 VS Code 的
   Terminal / Ports / Debug Console 三个运行时面板没有本项目运行态证据——属"编辑器静态 + 测试"验证，
   不含"服务实际运行"验证。导出中这些面板为 `blocked:api-not-readable` 或仅元数据。
2. **`ycmn-backend` / `ycmn-ui` 旧目录**：疑似历史残留，本轮未纳入验证，未确认是否仍在使用。
3. **内蒙商城**等其他项目不在本轮范围。
