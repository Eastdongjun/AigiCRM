# AGENTS.md — AIGICRM 客户管理系统（权限升级）

面向 Codex 的项目级指引。与 CLAUDE.md 并存（CLAUDE.md 给 Claude Code，本文件给 Codex 环境）。

## 项目一句话

AIGICRM（运筹马年）内部 CRM：客户/审批/激励/文件/公海池/提醒 + 新权限模型（组织/岗位/权限规则/集团/法人）。UI 全中文。

## 技术栈与常用命令

- 后端：Spring Boot 4.1.0 / Java 21 / Maven / MyBatis-Plus；`cd aigicrm-backend && mvn clean spring-boot:run`（端口 8080）；打包 `mvn clean package -DskipTests`
- 前端：Vue 3 / Vite / npm；`cd aigicrm-ui && npm run dev`（端口 3000，/api 代理到 8080）；`npm run build`
- 全栈：`docker-compose up -d`（MySQL:3308 / Redis:6379 / 后端:8080 / 前端:80）
- 数据库导入（本地）：`source ~/.aigicrm-deploy.env && mysql -h localhost -P 3308 -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 aigicrm < sql/init.sql`（凭据经 env 注入，勿硬编码）

## 生产部署架构（2026-08-15 起，部署前必读）

- **磁盘三区**：系统盘仅 OS；数据盘 `/data`（`/data/docker` 程序根、`/data/database/mysql-data` 数据库、`/data/aigicrm` 部署目录，`/root/aigicrm` 是其软链）；NAS `/Aigicrm/files` 放用户文件
- 部署一律走 `scripts/deploy-*.sh`（内置架构自检 check_deploy_arch），禁止手工 docker run 偏离标准；完整规范见 `docs/生产部署规范.md`
- 前端源码目录为 `02-迭代源代码/aigicrm-ui`（scripts/deploy-frontend.sh 已修正）
- 数据库必须 bind 挂载 `/data/database/mysql-data`，禁止命名卷挂库

## 数据库事实（排查前必读）


- `sys_org` 视图已于 0809 sys_dept/sys_org 双表收口时删除——物理数据只在 `sys_dept` 表（含 dept_type），无 sys_org。
- schema 真相链：**Flyway 迁移** = `aigicrm-backend/src/main/resources/db/migration/V1__baseline.sql`（**V1' 重建基线**，2026-08-11 生产 schema 快照，56 表）+ V12~V18 后续迁移；历史日期脚本归档 `sql/migration/archive/`（不再登记）；`aigicrm_database_test.sql` = **审查基准 dump**（2026-08-16 起从生产导出：全库 schema + `sys_menu`/`sys_role_menu`/`sys_user_role`/`sys_role` 四表数据行，不含业务数据；旧 8-3 过期版已备份为 `aigicrm_database_test.sql.bak-20260816`）。
- 数据库变更：**必须经 Flyway**（新迁移 `V10+__xxx.sql` 放 `db/migration/`，用 `scripts/deploy-db.sh` 部署）；`sql/migration/` 下已有日期目录仅作历史参考，不得再手工执行。
- **迁移链例外（2026-08-11 已确认）**：V1 已被原地重建为生产快照（V1'，含 FOREIGN_KEY_CHECKS=0 包装修复 DB-001 前向 FK）；旧 V2-V9 归档 `sql/migration/archive/`。**此后不得再原地修改 V1**——任何变更一律走新迁移 V10+（V2 编号已弃用，避免与归档/未合并线的版本号冲突）。
- `biz_incentive_record.file_pre_id` 已从 BIGINT 改为 VARCHAR（存 JSON 数组），新代码不要再按数字处理。

## 编译与测试（强制，代码改动必须过这一关）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd aigicrm-backend && mvn -q test    # 全部单测
mvn -q compile                    # 快速编译检查
```

- 本机默认 JDK 已切 21（2026-08-16，~/.zshenv）；JDK 25（Lombok 不兼容）不再是默认。若 shell 里 java -version 不是 21，先检查 ~/.zshenv 的 JAVA_HOME。
- 任何代码改动交付前必须 `mvn compile` + `mvn test` 全绿；改动涉及积分/规则引擎时须补充对应单测。

## 代码审查协议（用户要求检查/审查代码时强制执行）


先跑交叉核对脚本，再叠手工清单，最后报告：

```bash
# 基准 = dump + 迁移链并集（2026-08-16 起强制，勿只用 dump 单文件：
# dump 可能早于最新迁移，单用会系统性假警报——8-3 旧 dump 曾误报 25 条 schema + 3 条权限）
python3 ~/.codex/skills/code-review/scripts/check_schema_entity.py \
  --java-dir aigicrm-backend/src/main/java --sql aigicrm_database_test.sql \
  --migrations-dir aigicrm-backend/src/main/resources/db/migration
python3 ~/.codex/skills/code-review/scripts/check_api_wiring.py \
  --frontend-api aigicrm-ui/src/api --backend aigicrm-backend/src/main/java
python3 ~/.codex/skills/code-review/scripts/check_permission_strings.py \
  --backend aigicrm-backend/src/main/java --sql aigicrm_database_test.sql \
  --migrations-dir aigicrm-backend/src/main/resources/db/migration
```

报告规则：
- 每条结论给 文件+行号+证据；脚本 ERROR 必须全部进报告（可标注“已验证/需确认”），不许静默跳过。
- **ERROR 定性前必须对照 `db/migration/` 迁移链 grep 验证**（缺失列/行可能由 V10+ 迁移补入；2026-08-16 实例：过期 dump 的 25 条 schema + 3 条权限报错经迁移链复核全部为假阳性，其中 3 条权限已被 V16/V17 修复）。
- 本机 JDK 与 Lombok 不兼容导致编译失败时，说明环境即可，不得当作代码 bug。
- 已修复项（approval:edit、sys_org 视图顺序、V2 种子列名、岗位/规则/集团菜单权限）不得重复上报为新 bug。

## 约定速查

- 接口统一返回 `Result<T>`（code=200 成功），分页 `PageResult<T>`
- MyBatis-Plus：逻辑删除 `deleted`；驼峰↔下划线；分页 `Page<T>`
- 表名前缀 sys_=系统、biz_=业务；时间戳 created_at/updated_at
- 客户状态用中文标签（正常/预警/超期/注意/开发）
- 权限注解 `@PreAuthorize("hasAuthority('xxx')")`，权限字符串必须在 sys_menu 有对应行，否则全用户（含 admin）403
- 侧边栏徽标用 `.menu-count` 类，不用 el-badge

## 工程规则(与 CLAUDE.md 同等强制)

### 编码原则
- 禁止复制粘贴重复业务逻辑;禁止吞异常/空 catch
- 禁止硬编码密码、密钥、生产地址
- 禁止绕过权限校验(所有写接口必须 @PreAuthorize,权限码先查 sys_menu)
- 禁止删除已有测试来让构建通过;禁止关闭规则消除错误
- 优先修复根因,不掩盖错误

### 数据库原则
- 所有数据库变更必须用迁移脚本(见 sql/migration/),已在生产执行的迁移不得修改
- 删除字段/表必须单独审批;迁移前必须备份+回滚方案
- 唯一性由数据库约束保证,不靠应用层

### 测试要求(每次修改至少)
- 单元测试/静态检查/构建(后端 mvn,前端 npm run build)
- 涉及接口:冒烟回归(scripts/smoke-test.sh)
- 涉及权限:越权测试(销售员访问管理接口应 403)
- 涉及数据库:迁移演练(先临时库再生产)
- 涉及页面:浏览器实测+截图

### 完成定义(DoD,全部满足才算完成)
- 验收标准全部满足(需求确认=是)
- 冒烟+回归+门禁通过(scripts/pre-delivery-check.sh)
- 知识资产同步(数据字典/权限字典/API清单/配置矩阵)
- 残余风险披露;回滚方案明确
- 独立代码审查通过(code-reviewer/security-reviewer)

## 硬性纪律(违反=事故,2026-09-24 起,源自实战踩坑)

1. **迁移序列化**:新建迁移前必须 `ls aigicrm-backend/src/main/resources/db/migration | grep 当天版本号`——并行会话可能已占用该号(2026-09-23 撞 23.4 实证,flyway 拒跑);迁移定位一律按 permission 码/唯一键,**禁止硬编码行 id**(21.1 事故:两库 id 错位→误删权限行→全员 403;StabilityInvariantTest.INV-MIG-001 机器拦截,历史文件祖父豁免)。
2. **口径单源**:同一业务的可见性/去重判据全库只允许一处定义,复用而非重写(激励=applyShareVisibility 单源;提醒去重双写方同判据;统计侧不得自造过滤)。
3. **人工文档同步**:功能交付必须同步更新 docs/unified-feature-manifest.md 对应行与 docs/business/ 对应篇——`scripts/gate.sh` 第4项做新鲜度检查,落后源码 3 天以上即门禁失败(上线周零回流的根因就是没这道检查)。
4. **交付前一键门禁**:`bash scripts/gate.sh`(完整档)——后端全量(含不变量钉)+前端测试构建+文档新鲜度+工作区检查;CI/发布前必跑。
5. **产品裁决只进台账**:业务口径变化一律记 docs/REQUIREMENTS.md 未决事项→裁决→business/ 落档,不散在会话里(这是最大的熵源)。
