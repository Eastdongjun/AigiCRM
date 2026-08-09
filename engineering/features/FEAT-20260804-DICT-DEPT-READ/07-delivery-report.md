# 交付报告

## 结果

- 销售员（NM005/王磊磊）、行政等业务角色打开「新增客户」页不再报「没有访问权限，请联系管理员授权」，行业/开发类型/大区/办事处下拉正常加载，可正常新增客户。
- 统计分析页按角色数据范围展示：销售员只看到个人数据，上级角色看到部门及下级数据，管理员全量不变（用户补充规则「统计数据分析里数据是个人的；上一级能看到下一级」）。

## 交付范围

- REQ-001/002（字典/部门只读接口登录即可读）：`SysDictController`、`SysDeptController` 的 4 个 GET 接口权限 `hasAuthority('system:config:view'/'system:dept:view')` → `isAuthenticated()`；写操作与其余系统管理接口严格不变。
- REQ-003（权限边界）：POST/PUT/DELETE 仍要求 `system:config:edit`/`system:dept:edit`；未登录拒绝；users/roles/logs 等接口不变（生产实测 403 确认）。
- REQ-004：生产库销售员/行政已勾选隐藏权限「基础数据-部门/字典」（sys_menu 98/99，visible=0），与放开接口并存无冲突。
- REQ-006/007（统计数据分析个人/上下级数据范围）：`CustomerService.getCurrentScopeUserIds()` + `StatisticsQueryService` 的 analysis/dailyStats/weeklyStats 范围过滤。
- 全部 AC-001~014 PASS。

## 变更清单

- 代码（后端 4 文件 + 测试 1 文件）：`SysDictController.java`、`SysDeptController.java`、`CustomerService.java`（+getCurrentScopeUserIds）、`StatisticsQueryService.java`（analysis/dailyStats/weeklyStats 过滤）、`CustomerServiceTest.java`（+4 单测）。
- 数据：生产库 `INSERT INTO sys_role_menu (role_id,menu_id) VALUES (11,98),(11,99),(9,98),(9,99)`；测试库 `(11,21)`（仅测试环境验证用）。
- 无前端改动、无接口结构变化、无数据表变更。

## 安装与启动

- 构建：`MAVEN_SKIP_RC=true JAVA_HOME=<JDK17> mvn package -DskipTests`（ycmn-backend）。
- 部署（测试）：scp jar → docker build（Dockerfile-run）→ docker run（网络 shopjoy-net，DB=shared-mysql，需 JWT_SECRET/OSS 变量）。
- 部署（生产）：scp jar 至 `/root/ycmn/ycmn-backend-1.0.0.jar` → `docker build -t ycmn-backend:2.7.0 -f Dockerfile-run .` → `docker run`（环境变量同原容器，JWT_SECRET 用 `~/.ycmn-deploy.env`）。

## 验证证据

- `mvn test`：38 tests 0 failures（含新增 4 个数据范围单测）。
- run-gates：`gate-report-20260804-224627-683546.json` 0 failed；preflight 通过。
- 生产实测（2026-08-04 22:43 部署后）：
  - NM005：depts 200 / dict/industry 200 / POST dict/types 403 / GET users 403；统计页客户总数 12（=名下客户）、办事处仅内蒙办销售部、行业分布个人范围；新增客户页无弹窗、下拉正常、控制台 0 error。
  - admin：组织架构页正常、analysis 全量 249/3 办事处（无回归）、控制台 0 error。
- 测试环境：wangleilei（org_and_children）98 客户 vs admin 254（范围过滤）；daily 文件 8 vs 72。

## 部署状态与步骤

- **已部署生产**：www.aigicrm.com 后端已更新（新 jar md5=fda32552...，容器 a145d3a3c7ad，Started 2026-08-04 22:43:51），NM005/admin 生产实测通过。JWT_SECRET 与生产原值一致，存量登录无影响。
- 测试环境（111.229.37.220）同步部署新 jar 并验证通过。
- 前端未改动，无需部署。

## 回滚与恢复

- 代码回滚：git revert 本次 4 个文件改动 → 重新打包部署（原 jar 保留于生产 `/root/ycmn/` 备份与 NAS `/Aigicrm/files/backup/`）。
- 数据回滚：`DELETE FROM sys_role_menu WHERE role_id IN (9,11) AND menu_id IN (98,99);`（生产库）。
- 回滚后验证：NM005 重新登录后访问新增客户页恢复原 403 行为（严格授权），admin 不受影响。
- 统计范围回滚后，销售员统计页恢复全量视图（修复前越权行为）。

## 已知限制与残余风险

- `dashboard()` 的 seaStats（公海统计）仍为全量——公海为共享池业务常态，未纳入范围过滤；如需按范围另立需求。
- 生产 `deploy-backend.sh` 本次后台卡住（[4/5] 重启步骤），改为手动完成；脚本与生产 Dockerfile-run 的 jar 路径约定需后续排查统一。
- 测试环境 wangleilei 补绑菜单 21 为验证所需（测试库数据，保留）。
- AC-011（行政 dept_and_children）以测试环境 org_and_children 用户验证范围语义，生产行政角色未用真实账号实测（生产行政账号权限同源，风险低）。

## 版本与追踪

- Feature：`FEAT-20260804-DICT-DEPT-READ`，状态 VERIFIED → DELIVERED。
- 代码库：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`（改动未提交 git，含用户/其他 agent 的既有未提交改动，未动）。
- 模型/工具：本次会话（DeepSeek V4 Flash）；JDK 17（brew openjdk@17）；Maven 3.9.16。
