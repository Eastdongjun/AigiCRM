# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AIGICRM (运筹马年) is an internal CRM system for an industrial sealing/lubricant/wear-resistant materials sales company. It manages customers, approvals, incentives, file storage, and sales team performance. The UI language is Chinese.

## Development Commands

### Backend (Spring Boot 4.1.0 / Java 21 / Maven Wrapper)

> 基线唯一事实源为 `pom.xml`(2026-09-22 对齐;历史版本声明曾漂移至 3.2.5/Java17,已修正)。
```bash
cd aigicrm-backend
mvn clean spring-boot:run          # Start backend on port 8080
mvn clean package -DskipTests      # Build JAR without tests
mvn test                           # Run tests
```

### Frontend (Vue 3 / Vite 8 / npm)
```bash
cd aigicrm-ui
npm install                        # Install dependencies
npm run dev                        # Dev server on port 3000 (proxies /api → 8080)
npm run build                      # Production build
```

### Docker Compose (full stack)
```bash
docker-compose up -d               # MySQL:3308, Redis:6379, Backend:8080, Frontend:80
```

### Database
```bash
# Import with explicit charset to avoid Chinese double-encoding
# 凭据经 ~/.aigicrm-deploy.env 注入（DB_PASSWORD），勿硬编码密码
source ~/.aigicrm-deploy.env && mysql -h localhost -P 3308 -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 aigicrm < aigicrm-backend/src/main/resources/db/migration/V1__baseline.sql  # sql/init.sql 是空目录,真基线=V1
```

## Architecture

### Backend (`aigicrm-backend/src/main/java/com/aigicrm/`)

Layered architecture per module: **Entity → Mapper → Service → Controller → DTO**

Business modules live under `module/`:
- `auth` – JWT login/logout, user info
- `customer` – Customer CRUD, follow-up logs, status lifecycle
- `approval` – Multi-step approval workflows (claim/transfer/extend/storage)
- `sea` – Public customer pool (unclaimed customers)
- `reminder` – Invoice/expiration reminders
- `incentive` – Four-category incentive tracking
- `file` – File management with Aliyun OSS
- `statistics` – Analytics and reporting
- `system` – Users, roles, menus, departments, config, logs

Cross-cutting layers:
- `common/` – `Result<T>` response wrapper, `PageResult`, `BaseEntity`, `BusinessException`, `GlobalExceptionHandler`
- `security/` – `JwtTokenProvider`, `JwtAuthFilter`, `SecurityConfig`, `SecurityUtils` (get current user)
- `config/` – `MybatisPlusConfig` (pagination, auto-fill), `RedisConfig`, `OssConfig`, `CorsConfig`
- `task/` – `CustomerExpireTask` scheduled job

### Frontend (`aigicrm-ui/src/`)

- `views/` – ~97 .vue components organized by feature (customer/, system/, statistics/, incentive/, file/, etc.)  # 2026-11-11 纠偏(旧文 17 页为初期规模)
- `api/` – 13 API modules matching backend modules; all use the Axios instance from `utils/request.js`  # 2026-11-11 纠偏
- `router/index.js` – Route definitions with auth guard (redirects to `/login` without token)
- `store/user.js` – Pinia store: user info, roles, permissions (Set), `hasPermission()` method
- `layout/index.vue` – Sidebar (dark theme, collapsible) + main content area
- `utils/request.js` – Axios instance: base URL `/api`, Bearer token injection, 401 redirect, 30s timeout

### Database (MySQL 8.0.28)

56 tables defined in `V1__baseline.sql` (2026-08-11 production schema snapshot; `sql/init.sql` is an empty legacy directory, not a usable import). All tables use utf8mb4, soft delete (`deleted` column), and auto-managed `created_at`/`updated_at` timestamps. Tables prefixed `sys_` for system, `biz_` for business.

## Key Conventions

- **API responses**: All endpoints return `Result<T>` with `code` (200=success), `message`, `data`
- **MyBatis-Plus**: Logic delete on `deleted` field; underscore-to-camelCase mapping; pagination via `Page<T>`
- **Auth flow**: Login → JWT token stored in localStorage → sent as `Authorization: Bearer {token}` header
- **Customer status values**: Use Chinese labels (正常/预警/超期/注意/开发), not English codes
- **Sidebar badges**: Use custom `<span class="menu-count">` CSS class, NOT `el-badge` inside `el-menu-item` (causes layout issues)
- **UI reference**: `img/` directory contains 11 design screenshots that serve as the visual specification

## Ports

| Service  | Port |
|----------|------|
| MySQL    | 3308 |
| Redis    | 6379 |
| Backend  | 8080 |
| Frontend (dev) | 3000 |
| Frontend (prod/Docker) | 80 |

## API Documentation

Swagger UI **默认关闭**(`aigicrm.swagger.enabled=false`); 显式开启后 `http://localhost:8080/swagger-ui.html` 可用(Knife4j / OpenAPI 3)。  # 2026-11-11 纠偏(旧文未提默认值)

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

---

# P0 强制质量门禁：禁止未经验证宣称完成（2026-08-12）

本规则优先级为 **P0**，适用于所有代码修改、缺陷修复、重构、配置修改、数据库迁移和依赖升级任务。机器强制层：`scripts/quality-gate.sh`（本地一键）+ `.github/workflows/ci.yml`（CI）。

## 一、核心铁律

没有本轮新鲜、完整、可复现的验证证据，禁止使用以下表述：
「已完成 / 已修复 / 已通过 / 没有问题 / 可以上线 / 构建成功 / 测试正常 / 应该可以 / 大概率没问题」。

VSCode Problems 面板显示 0 个错误 **只能作为辅助证据**，不得作为项目通过的唯一依据（插件没启动、只检查部分文件、服务没运行时面板也可能是空的）。

最终状态只能是：`PASS`（全部适用门禁通过）/ `FAIL`（至少一门禁失败）/ `BLOCKED`（环境/依赖/服务/权限/验证条件缺失，无法完成验证）。不得用「基本通过 / 应该通过 / 大体没问题」代替明确状态。

## 二、本项目实际门禁（按真实技术栈）

> 本仓库为 **Vue 3(JS) + Spring Boot 4.1.0(Java 21)**；含 Maven Wrapper（`./mvnw`，工具链 3.9.9 与 Dockerfile 冻结同版；本机直跑用 `MAVEN_SKIP_RC=true mvn`）；前端是 **JavaScript 项目**（无 tsconfig、未配 lint/type-check）。

### 1. 修改范围检查（必跑）
```bash
git status --short
git diff --check
git diff
```
确认：没有任务范围外文件、无调试代码/临时文件/硬编码密码、无遗留 TODO/占位/Mock、无无意删除原功能。

### 2. 前端门禁（`aigicrm-ui`，用 npm）
```bash
cd aigicrm-ui
npm run test     # vitest —— 必须 0 Failed、无未经说明的 skipped
npm run build    # 退出码 0
```
**lint / type-check：本项目未配置（JS 项目）→ 门禁中如实标 `N/A` 并说明，不得虚报 PASS。** 后续接入 ESLint/TS 后再加入放行组合。

### 3. 后端门禁（`aigicrm-backend`，必须 JDK 21——与 pom `java.version=21` 一致）
```bash
# 本机多 JDK 时显式指 21(2026-09-22 对齐: 历史文档曾要求 JDK17, 已随 Boot 4.1 升级过期)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd aigicrm-backend
mvn -B clean verify     # 编译 + 单测/集成 0 Failed + 打包退出码 0；无测试崩溃、无未经说明的跳过
```
有 Maven Wrapper（`./mvnw`，工具链 3.9.9 冻结）；本机直跑用 `MAVEN_SKIP_RC=true mvn`。

### 4. 运行与冒烟验证
构建通过后还必须：应用能启动（`docker-compose up -d` 或 `mvn spring-boot:run`）、启动日志无致功能失效的 ERROR/Exception、健康检查与关键接口正常、MySQL/Redis 依赖连接正常。只编译能跑但启动不了，不算 PASS。冒烟：`scripts/smoke-test.sh prod`。

### 5. 本次修改专项验证（按改动类型）
- 缺陷修复：重新复现原问题并验证消失
- 新功能：正常流程 + 异常流程 + 边界条件
- 权限修改：允许/禁止两种访问都验证；租户修改：验证租户间不能越权
- 数据库修改：迁移脚本、约束、索引、数据兼容性（走 Flyway V10+，见 AGENTS.md）
- 接口修改：成功、参数错误、无权限、服务异常
- 前端修改：加载、空数据、错误数据、重复提交

## 三、严禁制造「假通过」

禁止：`@ts-ignore` 隐藏类型错误、随意 `eslint-disable`、删除或跳过失败测试、降低断言强度、注释异常代码、空 catch 吞异常、关 strict、改质量工具配置忽略错误、返回固定成功绕过真实逻辑、Mock 顶替真实实现。确需采用必须先说明原因与风险并获用户批准。

## 四、一票否决条件

任意构建退出码非 0 / 任意测试失败 / 未解释的测试跳过 / 编译或类型错误 / 系统无法启动 / 本次问题无法实际复现验证 / 没有执行验证命令 / 验证输出不完整 / 关键依赖不可用 / 需求验收条件未逐条核对 / 通过屏蔽错误或删测试绕过门禁 → 最终状态必须 `FAIL` 或 `BLOCKED`。

## 五、强制输出验证报告

任务结束时按以下表格报告；只有所有适用门禁均为 PASS 才允许宣布完成。

| 门禁 | 实际命令或验证方法 | 退出码 | 结果 |
|---|---|---|---|
| 修改范围检查 | `git diff --check` | 0 | PASS |
| 代码规范 | 实际执行命令 | 0 | PASS / N/A |
| 类型检查 | 实际执行命令 | 0 | PASS / N/A |
| 自动化测试 | 实际执行命令 | 0 | PASS |
| 项目构建 | 实际执行命令 | 0 | PASS |
| 启动验证 | 实际验证方法 | 0 | PASS |
| 专项功能验证 | 实际测试场景 | - | PASS |

同时报告：测试总数/成功/失败/跳过、构建结果、实际验证过的业务场景、**未验证内容**、剩余风险、修改文件清单。一键执行入口：`bash scripts/quality-gate.sh`。
