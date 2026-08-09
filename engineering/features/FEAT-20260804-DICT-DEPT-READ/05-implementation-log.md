# 实现记录

## 变更文件

| 文件 | 对应 REQ/AC | 行为变化 |
|---|---|---|
| `ycmn-backend/.../SysDictController.java` | REQ-002/AC-003 | `GET /dict/data/{code}`、`GET /dict/data/type/{id}` 权限 `hasAuthority('system:config:view')` → `isAuthenticated()`（登录即可读；含说明注释） |
| `ycmn-backend/.../SysDeptController.java` | REQ-001/AC-002 | `GET /depts`、`GET /depts/tree` 权限 → `isAuthenticated()`（含说明注释） |
| `ycmn-backend/.../CustomerService.java` | REQ-006/AC-010~012 | 新增公共方法 `getCurrentScopeUserIds()`：按 dataScope 解析可见用户 ID（all→null；self→本人；dept/dept_and_children/org/custom→部门解析），复用既有私有辅助，与 `applyDataScope` 同口径 |
| `ycmn-backend/.../StatisticsQueryService.java` | REQ-006/007/AC-010~013 | `analysis()` 全部 customer/file 查询、`dailyStats()`/`weeklyStats()` file 查询注入范围过滤（applyCustomerScope/applyFileScope） |
| `ycmn-backend/src/test/.../CustomerServiceTest.java` | REQ-006 | 新增 4 个测试：getCurrentScopeUserIds 的 all/self/null/dept_and_children 分支（MockedStatic SecurityUtils） |
| 生产库 `sys_role_menu` | REQ-001/002（辅助） | INSERT (11,98),(11,99),(9,98),(9,99)：销售员/行政勾选隐藏权限「基础数据-部门/字典」 |
| 测试库 `sys_role_menu` | REQ-006（验证） | INSERT (11,21)：测试环境销售员补绑统计分析权限（模拟生产现状，仅测试环境） |

## 实现决策

- 最终按用户确认采用方案 B（登录即可读）+ 保留 98/99 勾选（双保险，无冲突）。历程：方案 B → 发现隐藏权限载体后改勾选方案 → 用户最终确认保留方案 B。
- 统计范围过滤复用 `CustomerService` 的既有数据范围体系（不重复实现 dept 解析），新增 `getCurrentScopeUserIds()` 公共出口。
- 字典/部门写操作、其他系统管理接口、登录认证逻辑零改动。

## 新增或更新的测试

- `CustomerServiceTest`：+4 测试（getCurrentScopeUserIds 各分支），全量 38 tests 通过。

## 实际执行命令

- `mvn clean compile` / `mvn test` / `mvn package -DskipTests`（JDK 17：`MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home`）
- 测试环境部署：scp jar → docker build（Dockerfile-run）→ docker run（网络 shopjoy-net，DB=shared-mysql）
- 生产部署：deploy-backend.sh 提取测试 jar 上传 → 手动 docker run 新镜像（部署脚本 [4/5] 卡住，改为手动完成）
- 生产数据修复：`INSERT INTO sys_role_menu (role_id, menu_id) VALUES (11,98),(11,99),(9,98),(9,99);`

## 失败与纠正

1. **mvn 无法运行**：`~/.mavenrc` 指向 `/tmp/jdk-17.0.20+8`（被清理）→ 用 `MAVEN_SKIP_RC=true` + brew openjdk@17 绕过（不改用户配置）。
2. **测试环境容器启动失败（JWT_SECRET 缺失）**：compose 无该变量 → docker run 手动注入；再失败（OSS_ACCESS_KEY_ID 为空）→ 补 OSS 占位变量后启动成功。
3. **生产部署未生效**：deploy-backend.sh 后台卡住；手动部署时发现运行中的仍是旧容器（新镜像未使用）→ 停旧容器启动新镜像；**容器内 jar md5 与旧 jar 一致**（Dockerfile-run COPY 的 jar 与上传目标路径差异）→ 确认生产 Dockerfile-run 为 `COPY ycmn-backend-1.0.0.jar`，将新 jar 置于 /root/ycmn/ 后重建镜像重启，md5 校验通过（fda32552...）。
4. **JWT_SECRET 空值风险**：/root/ycmn/.env 不存在 → 首启 JWT_SECRET 为空 → 用本地 `~/.ycmn-deploy.env` 的 JWT_SECRET 重启（与生产原配置一致，token 无缝）。
5. **测试环境统计接口 403**：测试库销售员缺 statistics:view（生产销售员经菜单 108 持有）→ 测试库补绑菜单 21（仅测试环境）。

## 未完成与残余风险

- 生产 Dockerfile-run 与本地源码不一致（生产 `COPY ycmn-backend-1.0.0.jar`、本地 `COPY target/...`）——部署脚本与 Dockerfile 路径约定需后续统一（本次已按生产实际处理）。
- 测试环境 wangleilei 补绑菜单 21 为验证所需，测试环境数据保留。
- 生产库 98/99 勾选保留（与放开接口并存，无冲突；回滚语句：`DELETE FROM sys_role_menu WHERE role_id IN (9,11) AND menu_id IN (98,99)`）。
- `dashboard()` 的 seaStats 仍为公海全量（业务常态，未纳入范围过滤，见 04 风险与盲区）。
