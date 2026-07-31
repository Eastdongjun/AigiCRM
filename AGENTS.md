# AGENTS.md — YCMN 客户管理系统（权限升级）

面向 Codex 的项目级指引。与 CLAUDE.md 并存（CLAUDE.md 给 Claude Code，本文件给 Codex 环境）。

## 项目一句话

YCMN（运筹马年）内部 CRM：客户/审批/激励/文件/公海池/提醒 + 新权限模型（组织/岗位/权限规则/集团/法人）。UI 全中文。

## 技术栈与常用命令

- 后端：Spring Boot 3.2.5 / Java 17 / Maven / MyBatis-Plus；`cd ycmn-backend && mvn clean spring-boot:run`（端口 8080）；打包 `mvn clean package -DskipTests`
- 前端：Vue 3 / Vite / npm；`cd ycmn-ui && npm run dev`（端口 3000，/api 代理到 8080）；`npm run build`
- 全栈：`docker-compose up -d`（MySQL:3308 / Redis:6379 / 后端:8080 / 前端:80）
- 数据库导入：`mysql -h localhost -P 3308 -u root -p<redacted> --default-character-set=utf8mb4 ycmn < sql/init.sql`

## 数据库事实（排查前必读）

- `sys_org` 不是表，是基于 `sys_dept` 的**视图**（见 ycmn-backend/src/main/resources/db/migration-permission-upgrade-mysql8.sql，视图创建在加列之后）。看到 "table sys_org doesn't exist" 先确认是不是视图/执行顺序问题。
- schema 真相链：`sql/init.sql` = 基线；`ycmn-backend/src/main/resources/db/` = V2/V3 + 迁移增量；`ycmn_database_test.sql` = 真实库 dump（最接近线上）。
- 全新部署执行顺序：基线 → V2 → V3 → migration，缺步或乱序会挂。
- `biz_incentive_record.file_pre_id` 已从 BIGINT 改为 VARCHAR（存 JSON 数组），新代码不要再按数字处理。

## 代码审查协议（用户要求检查/审查代码时强制执行）

先跑交叉核对脚本，再叠手工清单，最后报告：

```bash
python3 ~/.codex/skills/code-review/scripts/check_schema_entity.py \
  --java-dir ycmn-backend/src/main/java \
  --sql ycmn_database_test.sql  # 真实库 dump 为准（勿后接旧基线 init.sql）
python3 ~/.codex/skills/code-review/scripts/check_api_wiring.py \
  --frontend-api ycmn-ui/src/api --backend ycmn-backend/src/main/java
python3 ~/.codex/skills/code-review/scripts/check_permission_strings.py \
  --backend ycmn-backend/src/main/java --sql ycmn_database_test.sql
```

报告规则：
- 每条结论给 文件+行号+证据；脚本 ERROR 必须全部进报告（可标注“已验证/需确认”），不许静默跳过。
- 本机 JDK 与 Lombok 不兼容导致编译失败时，说明环境即可，不得当作代码 bug。
- 已修复项（approval:edit、sys_org 视图顺序、V2 种子列名、岗位/规则/集团菜单权限）不得重复上报为新 bug。

## 约定速查

- 接口统一返回 `Result<T>`（code=200 成功），分页 `PageResult<T>`
- MyBatis-Plus：逻辑删除 `deleted`；驼峰↔下划线；分页 `Page<T>`
- 表名前缀 sys_=系统、biz_=业务；时间戳 created_at/updated_at
- 客户状态用中文标签（正常/预警/超期/注意/开发）
- 权限注解 `@PreAuthorize("hasAuthority('xxx')")`，权限字符串必须在 sys_menu 有对应行，否则全用户（含 admin）403
- 侧边栏徽标用 `.menu-count` 类，不用 el-badge
