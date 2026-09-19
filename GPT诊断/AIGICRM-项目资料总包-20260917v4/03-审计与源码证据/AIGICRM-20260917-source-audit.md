# AIGICRM 源码审计报告（2026-09-17）

> 审计对象：`pack-backend.tar.gz`、`pack-pc.tar.gz`、`pack-mini.tar.gz`、`pack-gates.tar.gz`、`SHA256SUMS`。
> 方法：源码静态审计 + 现有契约门禁复核 + 迁移链/部署脚本审计 + 交付包可复验性检查。未连接生产数据库，未进行真实多集团活体攻击测试。

## 一、总评

当前代码库的工程化基础较强：API wiring、权限码登记、PC/小程序 API parity、迁移契约、功能审计等静态门禁可通过；但仍存在若干会影响多集团上线安全边界的真实问题。当前更准确的状态应为：**静态契约成熟，但安全闭环与真实联调验收尚未完成，不建议把“116 PASS”直接等价为生产可上线。**

## 二、真问题

### P0-01 客户排行旧接口通过 JdbcTemplate 绕过租户与数据范围
- 现象：`GET /api/customer/rankings` 对持有 `score:view` / `ranking:customer:view` / `statistics:view` 任一权限的用户开放。
- 代码：`backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java:74-77`。
- 根因：`customerRankings()` 下三段 SQL 直接查询 `biz_customer`、`biz_incentive_record`、`biz_invoice`、`biz_payment`，无 `tenant_id`、当前集团或 Owner/DataScope 条件；JdbcTemplate 不经过 MyBatis-Plus 租户拦截器。
- 代码：`backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java:750-817`。
- 影响：多集团部署时可把其他集团的客户名、负责人、激励分、开票/回款聚合混入排行；这是跨租户信息泄露。
- 修复：如果该旧接口已无客户端调用，直接删除；否则迁移到 tenant-aware Mapper/Service，并叠加当前集团 + OrgScopeResolver 范围。

### P0-02 无集团归属账号 fail-open 到主集团 1
- 现象：非 admin 账号取不到集团时，不是拒绝访问，而是默认按集团 1 处理。
- 代码：`backend/src/main/java/com/aigicrm/common/scope/GroupScopeUtil.java:7-17,32-38,48-57`；`backend/src/main/java/com/aigicrm/config/MybatisPlusConfig.java:123-139`；`backend/src/main/java/com/aigicrm/module/system/service/SysUserService.java:98-115`。
- 根因：兼容历史数据的 fallback 被放进正式授权路径。
- 影响：只要出现“账号有效但集团绑定丢失/未初始化”，就可能默认读取主集团数据，而不是 403/空集。
- 修复：授权层必须 fail-closed；登录时要求明确有效集团，空集团直接拒绝业务访问。历史脏数据通过一次性迁移修复，不能用运行期默认放行兜底。

### P0-03 激励阅读统计/排行榜跨集团聚合
- 现象：`readStats/readRanking` 只做权限码校验，但底层聚合 SQL 从所有 `sys_user` 与全部当月 `biz_incentive_record` 统计。
- 代码：`backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java:167-183`；`backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java:1064-1076`。
- 根因：JdbcTemplate 原生 SQL 绕开 tenant interceptor，SQL 自身又没有集团条件。
- 影响：可泄露其他集团人员姓名、部门与阅读行为；统计口径也会跨集团污染。
- 修复：通过当前集团限定 `sys_user` 与 `biz_incentive_record`；最好改 Mapper 查询并显式加入 tenant/group scope。`biz_incentive_read` 无 tenant_id 时必须通过记录和用户归属 JOIN 收口。

### P0-04 激励 markRead 缺少目标记录可见性校验
- 现象：用户只需 `incentive:view` 即可提交任意 recordId 的已读写入。
- 代码：`backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java:160-164`；`backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java:991-995`。
- 根因：写 `biz_incentive_read` 前未复用记录的可见性/租户判定。
- 影响：可对不可见甚至其他集团的记录写“已读”，污染统计和排行榜，属于 IDOR/完整性问题。
- 修复：写入前用与记录详情一致的 visibility predicate 校验 recordId，校验失败统一 404/403；再 upsert 阅读状态。

### P1-05 激励专项权限列表跨集团泄露用户信息
- 现象：`/api/incentive/permission/list` 只要求 `system:config:view`，SQL 列出所有授权用户；而该权限种子不只给 admin。
- 代码：`backend/src/main/java/com/aigicrm/module/incentive/controller/IncentivePermissionController.java:29-50`；`backend/src/main/resources/db/migration/V2026.09.11.6__seed_permission_dictionary_and_roles.sql:1115-1125`。
- 影响：行政/销售总监等配置查看角色可能读取其他集团的 username/realName 与专项授权标志。
- 修复：列表与 grant/revoke 一样收紧到平台管理员，或按 currentGroupId 限制候选用户。

### P1-06 忘记密码仍可枚举账号状态
- 现象：`lookup` 特意做了中性返回，但匿名 `send-code`/`reset` 又返回“用户不存在 / 账号禁用 / 未绑定手机号”等差异错误。
- 代码：`backend/src/main/java/com/aigicrm/config/SecurityConfig.java:40-42`；`backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java:88-110`；`backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java:268-285,288-313,340-386`。
- 影响：攻击者可批量验证有效用户名及账号状态。
- 修复：匿名链路的外部响应、HTTP 状态和可观察时序应统一；内部照常记录不存在/禁用/未绑手机。限流保留，但不能依赖限流替代防枚举。

### P1-07 JWT 黑名单权威库查询异常时 fail-open
- 现象：注释声明“DB 为权威判定”，但 DB 查询抛异常后返回 false。
- 代码：`backend/src/main/java/com/aigicrm/security/JwtTokenProvider.java:115-136`。
- 影响：Redis miss + 黑名单表查询异常的组合场景下，已注销 token 可能被当作未撤销。
- 修复：黑名单状态未知必须 fail-closed（拒绝该 token / 返回认证服务暂不可用），不可返回“未拉黑”。

### P1-08 role_perm_scope 装载失败会退回可能更宽的旧 dataScope
- 现象：范围配置查询异常时吞异常并返回已加载/空配置，后续解析会回退角色 `dataScope`。
- 代码：`backend/src/main/java/com/aigicrm/module/system/service/RolePermScopeService.java:50-73`；`backend/src/main/java/com/aigicrm/common/scope/OrgScopeResolver.java:142-159`。
- 影响：若某模块通过 role_perm_scope 配得比角色旧范围更窄，配置表故障时权限可能被放宽。
- 修复：授权元数据读取失败应拒绝或给 none/self 的安全范围；至少不能退回到可能更宽的范围。

### P1-09 “all” 数据范围在全局人员选择器里没有集团边界
- 现象：`resolver.resolve(user)` 返回 null 代表 all，`ScopeService.visibleUsers` 随即查询全部启用 `sys_user`，没有 currentGroup 条件。
- 代码：`backend/src/main/java/com/aigicrm/module/common/service/ScopeService.java:25-43`；类似风险还存在于 `CustomerService.getSimpleUserList()` `backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java:259-273`。
- 影响：未来给非 admin 角色配置 `all` 时，其“全量”会变成跨集团全量，而不是“当前集团全量”。
- 修复：定义 all=当前租户/集团全量；平台 admin 才是跨集团全量。全局表 `sys_user/sys_dept` 必须手动套 GroupScope。

### P1-10 生产迁移工具版本不一致 + 失败码被吞
- 现象：运行期/插件 Flyway 12.4.0，生产迁移脚本仍用 9.22.3；脚本用 `|| true` 吞掉 repair/migrate 的真实退出码，再靠 grep 文本判断失败。
- 代码：`backend/pom.xml:35-39`；`gates/scripts/deploy-db.sh:14,64-84`。
- 影响：跨大版本解析/校验行为有偏差；某些非零退出若错误文本未匹配正则，可能被误判为“迁移执行完成”。
- 修复：统一 Flyway 版本；保存真实 exit code，输出可过滤但退出码必须作为第一判据。

## 三、门禁体系本身的问题

### G-01 read-ranking 契约会对错误授权语义误报 PASS
`gates/scripts/check-incentive-read-ranking-contract.sh:43-71` 只 grep 权限码、路由、Service 方法名、测试文件名，完全不验证 SQL 是否带 tenant/group scope。当前跨集团聚合实现仍能通过该门禁。

建议新增“授权语义门禁”：
1. 禁止业务 Service/Controller 对 tenant 表直接使用 JdbcTemplate，除白名单且必须带 scoped helper；
2. 所有 `JdbcTemplate` SQL 对 tenant 表做静态审计；
3. 对多集团 fixture 做真实集成测试：集团 A 用户绝不能读/写集团 B；
4. 对 fail-open 分支加入故障注入测试（Redis/DB/role_perm_scope）。

### G-02 生产租户开关存在两套相反默认
- `application.yml:61-65` 默认 `TENANT_ENABLE=false`；
- `deploy-backend.sh:47` 强制 true；
- `check-prod-config-contract.sh:34-35` 反而要求 production compose 使用 `${TENANT_ENABLE:-false}`。

这意味着不同生产启动路径可能得到完全不同的租户隔离结果。生产应统一为 fail-closed：生产 profile 下未明确开启租户隔离就启动失败。

## 四、交付包问题

### D-01 SHA256SUMS 不可移植且自校验错误
- `/mnt/data/SHA256SUMS:1-6` 使用打包机绝对路径 `/Users/dongjun/Desktop/...`，异机 `sha256sum -c` 直接找不到文件。
- `SHA256SUMS` 又把自己写入自身校验清单；清单记录的 self hash `0c61...` 与本次实际文件 hash `21b3...` 不一致。
- 其余 README + 四个 tar 包按 basename 手工复核均与清单值一致。

修复：清单只使用相对文件名，且不要把 `SHA256SUMS` 自己放进同一份自校验清单。

### D-02 当前交付包无法原样执行完整 contracts
`gates/scripts/dev-verify.sh:462-538` 会执行 workspace/Dockerfile/production-compose 等契约；但当前包缺少：
- `AIGICRM.code-workspace`
- `Dockerfile`
- `docker-compose.prod.yml`
- `nginx/nginx-ssl.conf`
- `.git` 元数据

此外 `check-vscode-workspace-contract.sh:20-34` 还硬编码旧源码目录名与开发者本机绝对路径 `/Users/dongjun/...`。因此历史证据中的 116 PASS 不能由这份交付包在干净机器上原样复现。

### D-03 PC 无 lockfile，依赖构建不可完全复现
`pc/package.json:12-31` 大量依赖使用 `^`，交付包没有 package-lock/pnpm-lock/yarn.lock。相同源码在不同日期安装可能解析到不同依赖补丁/次版本。

修复：选定 npm/pnpm，提交 lockfile，CI 使用 `npm ci` 或 `pnpm --frozen-lockfile`。

### D-04 后端无 Maven Wrapper
包内无 `mvnw/.mvn`，干净机必须预装兼容 Maven 才能构建。建议纳入 Maven Wrapper，并在 CI/交付说明固定 JDK/Maven 版本。

### D-05 脱敏说明与实际包不完全一致
`backend/src/main/resources/db/migration/FROZEN-SHA256.txt:4` 仍写有生产 IP `39.99.35.218`，与任务书“测试 IP 已脱敏”的交付卫生目标不一致。建议证据包统一用 `PROD_SERVER_IP`。

## 五、不是当前问题 / 已修复项

1. **V2–V11 缺口**：当前已有 `V2026.09.11.1__restore_v2_v11_gap_tables.sql` 补齐；`MIGRATION-LINEAGE.md:71-83` 记录了 fresh replay 证据。当前静态 migration contract 也通过，因此不再把“V2–V11 不存在”列为当前缺陷。
2. **V25 finance:view/contact:view 空插入**：V25 历史上确实会在纯结构 V1 新库静默插 0 行，但 `V2026.09.13.1__register_missing_used_permission_codes.sql:23-39` 已明确补偿；当前 permission registration contract 检查 62 个在用权限码、缺失 0。
3. **联系人写操作仅要求 customer:edit**：权限矩阵明确这样设计（查看需要 `contact:view`，新增/编辑/删除由 `customer:edit` 控制），因此按当前产品契约不判为 bug；若业务认为“不可看联系人也不应改联系人”，需产品层改权限模型，而不是当代码漂移处理。

## 六、真实验收缺口

静态门禁不能替代真实联调。`gates/docs/mobile-manual-qa-checklist.md:19-60` 中登录/权限刷新、多组织切换、客户、文件、激励、审批、公海、高风险越权等仍全部标记“待执行”；`gates/docs/full-stack-unified-development-todolist.md:324-345` 也仍保留 PC/小程序真实对账、开发者工具预览、fresh Sonar 等未完成项。

因此上线前必须至少做：
- 两集团、三角色（admin / 集团管理员 / 普通员工）数据隔离矩阵；
- PC 与小程序同账号同数据对账；
- IDOR 负向用例；
- Redis/DB/权限配置故障注入；
- 新库 Flyway 12.4.0 全链重放 + 生产镜像同版本验证；
- 完整交付包在全新 CI runner 上一键复验。

## 七、Top 10 风险排序

1. 客户排行 raw JDBC 跨租户泄露（P0-01）
2. 无集团账号 fail-open 到主集团 1（P0-02）
3. 激励阅读统计/排行榜跨集团聚合（P0-03）
4. 激励 markRead 任意记录写入（P0-04）
5. “all” 范围的全局人员选择器跨集团（P1-09）
6. 激励专项授权列表跨集团 PII（P1-05）
7. role_perm_scope 故障后回退更宽旧范围（P1-08）
8. JWT blacklist DB 故障 fail-open（P1-07）
9. 忘记密码账号枚举（P1-06）
10. Flyway 12/9 版本偏斜 + deploy-db 吞退出码（P1-10）

## 八、建议修复顺序

**第一批（上线阻断）**：P0-01~P0-04 + P1-09 + production tenant fail-closed。

**第二批（安全加固）**：P1-05~P1-08 + forgot-password neutral response。

**第三批（工程交付）**：Flyway 统一版本/退出码、可复验交付包、lockfile/Maven Wrapper、补多集团活体 QA。
