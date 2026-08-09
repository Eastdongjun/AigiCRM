# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YCMN (运筹马年) is an internal CRM system for an industrial sealing/lubricant/wear-resistant materials sales company. It manages customers, approvals, incentives, file storage, and sales team performance. The UI language is Chinese.

## Development Commands

### Backend (Spring Boot 3.2.5 / Java 17 / Maven)
```bash
cd ycmn-backend
mvn clean spring-boot:run          # Start backend on port 8080
mvn clean package -DskipTests      # Build JAR without tests
mvn test                           # Run tests
```

### Frontend (Vue 3 / Vite 8 / npm)
```bash
cd ycmn-ui
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
# 凭据经 ~/.ycmn-deploy.env 注入（DB_PASSWORD），勿硬编码密码
source ~/.ycmn-deploy.env && mysql -h localhost -P 3308 -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 ycmn < sql/init.sql
```

## Architecture

### Backend (`ycmn-backend/src/main/java/com/ycmn/`)

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

### Frontend (`ycmn-ui/src/`)

- `views/` – 17 page components organized by feature (customer/, system/, statistics/, etc.)
- `api/` – 9 API modules matching backend modules; all use the Axios instance from `utils/request.js`
- `router/index.js` – Route definitions with auth guard (redirects to `/login` without token)
- `store/user.js` – Pinia store: user info, roles, permissions (Set), `hasPermission()` method
- `layout/index.vue` – Sidebar (dark theme, collapsible) + main content area
- `utils/request.js` – Axios instance: base URL `/api`, Bearer token injection, 401 redirect, 30s timeout

### Database (MySQL 8.0.28)

19 tables defined in `sql/init.sql`. All tables use utf8mb4, soft delete (`deleted` column), and auto-managed `created_at`/`updated_at` timestamps. Tables prefixed `sys_` for system, `biz_` for business.

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

Swagger UI available at `http://localhost:8080/swagger-ui.html` when backend is running (Knife4j / OpenAPI 3).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
