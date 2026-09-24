# OCR 发现逐条复核 — core-infra（37 条）

- 复核对象：`03-管理/evidence/aigicrm/20260922-ocr-code-review/slices-live/findings-core-infra.json`（37 条）
- 复核方式：逐条打开真实源码 + 全仓 grep/glob 交叉验证（调用方、配置、迁移链、单测、前端拦截器、jar 反编译）
- 复核基线：当前工作区源码（Spring Boot 4.1.0 / spring-security 7.1.0 / spring-data-redis 4.1.0）
- 路径缩写（**以仓库根为基准的相对路径**）：`BE/` = `02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/`
  （例：`BE/config/CorsConfig.java` = `02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/config/CorsConfig.java`；测试与资源文件写全路径）
- 裁定口径：CONFIRMED（属实且归因正确） / PARTIAL（现象属实但原因或严重度有偏差） / FALSE（不成立） / BY_DESIGN（属实但属有意设计或已裁定风险接受） / UNVERIFIABLE

| 序号 | 严重度(校准后) | 裁定 | 文件:行 | 一句话理由 + 代码证据 | 触发前提 | 修复建议 |
|---|---|---|---|---|---|---|
| 1 | low | CONFIRMED | BE/common/BaseEntity.java:8 | 全仓 grep `BaseEntity` 仅该类自身声明，无 `extends BaseEntity`；各实体自持 id/createdAt（BizCustomer.java:13,96、BizFile.java:21）→ 确实无运行期作用 | 不触发（可维护性） | 二选一：删除该类，或让实体统一继承并去掉重复字段，避免双份"审计列"事实源 |
| 2 | low | CONFIRMED | BE/common/BusinessException.java:6 | 类继承 RuntimeException（Serializable）且未声明 serialVersionUID 属事实；但全仓 grep 无 ObjectOutputStream/ObjectInputStream，无 Java 原生序列化路径 | latent：仅当实例被 Java 原生序列化/跨版本反序列化（当前项目无此链路） | 加 `private static final long serialVersionUID = 1L;` |
| 3 | low | BY_DESIGN | BE/common/GlobalExceptionHandler.java:62 | `httpStatusOf` 确无 500 分支（:55-63，javadoc"其余默认 400"）；但这是被单测固化的契约：GlobalExceptionHandlerTest.java:57-63 显式断言 `BusinessException(500)` → HTTP 400 并注明"与改前行为一致"；B7 仅补 503（:52-54） | 触发条件已具备（FieldFilterAdvice.java:89、AuthService.java:355/362 抛 500），但行为是既定契约而非缺陷 | 若确需语义化：加 `case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;` 并同步改该单测断言 |
| 4 | low | PARTIAL | BE/common/GlobalExceptionHandler.java:82-86,88-92 | 两个参数校验 handler 确无 `@ResponseStatus`（同类 :94-96/100-105 都有）→ 默认 HTTP 200 + body code=400，现象属实；偏差：前端按 body code 分流（aigicrm-ui/src/utils/request.js:46 `res.code !== 200`），"网关当成功"影响有限 | 任何参数校验失败即现（POST/PUT body 非法或表单绑定失败） | 两处补 `@ResponseStatus(HttpStatus.BAD_REQUEST)` |
| 5 | low | CONFIRMED | BE/common/GlobalExceptionHandler.java:84,90 | `getAllErrors().get(0)` / `getAllErrors().get(0)` 无空集兜底属实 | latent：需 BindingResult 零错误；Spring 抛 MethodArgumentNotValid/BindException 时必含 ≥1 错误，正常路径不可达 | 先取 errors 判 `isEmpty()`（或用 `getFirst()`）；handleBindException 同步 |
| 6 | medium | CONFIRMED | BE/common/enums/DataScopeType.java:83-85 | `rank()` 返回 ordinal；声明序 DEPT=4 < ORG_AND_CHILDREN=5（:33-37），而 OrgScopeResolver.java:505-518（及 :279-289）的 org_and_children 解析 = 本部门子树 ∪ managedOrgIds 子树 ⊇ dept；SysUserService.java:191/197、SysRoleService.java:89 用 `rank() < caller.rank()` 拦截提权 | 调用者持 dept（rank=4）等范围 + `system:user:edit`/`system:role:edit`，授予 dataScope=org_and_children（5<4 为 false 不拦截）；被授权者须有 managedOrgIds 才真正超出 dept | `rank()` 改为显式赋值（不依赖声明顺序），使 ORG_AND_CHILDREN 不低于 DEPT 的宽松语义 |
| 7 | low | CONFIRMED | BE/common/enums/DataScopeType.java:90-92 | 全仓 grep `needChildren` 只有定义、零调用方；实现仅对 ORG_AND_CHILDREN 返回 true，与 REGION/OFFICE/DEPT 注释的"及以下"递归语义不符 | latent：一旦有人按方法名/注释调用，将对 REGION/OFFICE/DEPT 漏查子级 | 删除该死方法，或按真实递归语义修正判断 |
| 8 | low | PARTIAL | BE/config/CorsConfig.java:31-32 | `setAllowCredentials(true)` + `setAllowedOriginPatterns(...)` 组合事实成立（Spring 不对 patterns 拒绝通配）；偏差：值来自 cors.allowed-origins，生产已收敛为精确源（docker-compose.prod.yml `CORS_ORIGINS: https://www.aigicrm.com`，注明 SEC-P3-002 已清偿），application.yml:92 默认仅 localhost | latent：仅当运维把 CORS_ORIGINS 配成 `*` 或通配模式，才会回显任意 Origin 并带凭据 | 启动时断言 origins 不含 `*`（或改用 `setAllowedOrigins` 精确匹配） |
| 9 | low | PARTIAL | BE/common/util/ClientIpUtils.java:17-19,66-80 | 默认三段私网不含 loopback、ipToLong 只认点分 IPv4（IPv6 返回 -1）属实；偏差：生产 nginx 与 backend 同 docker 网络（docker-compose.prod.yml frontend `BACKEND_HOST=aigicrm-backend`），对端为 172.x 落在 172.16/12 内，loopback 场景不适用；Docker 默认 IPv4，"全部塌成一个桶"仅在 IPv6 对端时成立 | latent：需 loopback 直连（非本部署）或 IPv6 对端 | 默认补 `127.0.0.0/8`，并对 IPv4-mapped IPv6 做解包（或改用 InetAddresses 判定） |
| 10 | low | PARTIAL | BE/common/util/ClientIpUtils.java:34-37 | X-Real-IP 原样返回、未校验格式属实；偏差：nginx 无条件覆盖该头（aigicrm-ui/nginx.conf.template:29、nginx/nginx-ssl.conf:93 均为 `proxy_set_header X-Real-IP $remote_addr`），OCR 假设的"透传 $http_x_real_ip"配置不存在 | latent：仅当可信代理被改为透传客户端自带 X-Real-IP | 采纳建议：`ipToLong(candidate) != -1` 才采用，否则回落 remoteAddr（低成本纵深） |
| 11 | low | PARTIAL | BE/common/util/ClientIpUtils.java:22-25 | 空白输入静默 return（无法表达"不信任任何代理"）、非法 CIDR 静默跳过属实；偏差：trim 建议多余——使用处已 `cidr.trim()`（:49）且空项已跳过（:48） | 运维配置写错 CIDR 时静默失效（无日志） | 启动时逐项校验并 warn，显式支持"空值=不信任任何代理"语义 |
| 12 | medium | CONFIRMED | BE/common/util/DeptTreeUtils.java:31-36 | 每节点一次 `selectList`（递归 :35），调用点 13 处（StatisticsQueryService×8、ReminderService×2、ScopeService、SeaService、ApprovalService）；OCR 描述与 05-gpt-review-20260922-adjudication.md 已挂账的 "N+1" 一致 | 任一列表/统计接口传 deptId 且部门子树较深即触发 | 一次性取全表 (id,parentId) 建 `parentId→children` 映射，内存迭代展开 |
| 13 | low | PARTIAL | BE/common/util/DeptTreeUtils.java:33 | 父部门 status≠1 会连带剪掉整棵子树属实；偏差：同口径在 OrgScopeResolver.java:526-529 与 SysDeptMapper.java:13-15（递归 CTE 同样 `status=1`）三处一致实现，属项目既有"禁用即连同子树不可见"统一约定，非本工具独有缺陷 | 存在"父禁用、子启用"的畸形组织数据时可见集合偏小 | 在 javadoc 显式写明该语义即可；若业务要求穿透，须三处同步改并加测试 |
| 14 | low | CONFIRMED | BE/common/util/DeptTreeUtils.java:31 | `selectList` 未加 `.select(...)`，取回 sys_dept 全列而只消费 `getId()` | 每次调用都多传列（伴随第 12 条被放大） | 加 `.select(SysDept::getId, SysDept::getParentId)` |
| 15 | low | BY_DESIGN | BE/config/OssConfig.java:11-17 | `@Data` + accessKeyId/accessKeySecret 确会生成含密 toString；但全仓无任何日志调用点（grep `ossConfig` 仅 SmsConfig.java:20-23 与 FileService.java:235/406/472/488 取值，无 log）；权威基线已把"@Data 上 DTO 的 toString 风险（无实际日志调用点）"列为理论风险 | latent：仅当未来有人 log 该 Bean / 异常拼接该对象 | 可选加 `@ToString.Exclude`（零成本预防），非必需 |
| 16 | low | FALSE | BE/config/OssConfig.java:20-22 | 反证：Spring `@Bean` 默认 `destroyMethod=INFER_METHOD`，按运行时 bean 类查找 `close()`/`shutdown()`；javap 实证 `com.aliyun.oss.OSSClient` 有 `public void shutdown()`（3.17.4 jar），类加载的实例类即 OSSClient → 容器关闭时会自动调用，不构成连接/线程泄漏 | 无（关闭上下文即自动 shutdown） | 无需修改；如需显式可读可加 `@Bean(destroyMethod = "shutdown")`（非必需） |
| 17 | low | CONFIRMED | BE/common/util/TrustedProxyConfig.java:13 | 同一默认值三处复制（此处 @Value 默认、application.yml:54、ClientIpUtils.java:17-19），且空白值经 configureTrustedCidrs 静默回落内置默认（ClientIpUtils.java:23）；默认信任全部 RFC1918 | 需先取得私网内可达位置才能伪造 X-Real-IP（当前对端为 docker 网络） | 收敛为 YAML 单一来源（去掉 @Value 默认），启动日志打印生效 CIDR |
| 18 | low | CONFIRMED | BE/common/util/ImportGuard.java:33 | `endsWith(".xlsx")/(".xls")` 大小写敏感属实，调用点 CustomerController.java:457、SeaController.java:81（HTTP 上传） | 上传 `XX.XLSX` 等大写扩展名 → 被误拒并提示"仅支持 .xlsx 或 .xls" | 统一 `toLowerCase(Locale.ROOT)` 后再比较 |
| 19 | low | CONFIRMED | BE/common/util/ImportGuard.java:42-45 | 调用方先 `rowNum++`（初值 1=表头）再判 `rowNum > 5000`（CustomerImportListener.java:16,29-31；SeaImportListener 同构）→ 第 5000 数据行 rowNum=5001 被拒，实际上限 4999，与 javadoc:14"数据行数 ≤ 5000"不符 | 导入恰好 5000 数据行时被拒（差一） | 调用方改传 `rowNum - 1`，或把 javadoc/常量口径改为"物理行号" |
| 20 | low | PARTIAL | BE/common/util/ImportGuard.java:29 | `file.isEmpty()` 无 null 兜底属实；偏差：唯二调用点均为 `@RequestParam("file") MultipartFile`（CustomerController.java:454、SeaController.java:78），Spring 在缺失时先抛异常，永远不会传 null；OCR 假设的定时任务/内部调用方不存在 | latent：仅当未来出现非 Spring 调用方传 null | 加 `file == null ||` 一行防御（保持 BusinessException 语义） |
| 21 | medium | PARTIAL | BE/config/SmsConfig.java:20-24 | 短信确复用 OSS 凭证、yml 无 `aliyun.sms.access-key-*`（application.yml:75-77 仅 sign-name/template-code）→ 无法为短信配独立 AK/SK，属实；偏差：`@ConditionalOnProperty` 只约束 `ossClient` Bean（OssConfig.java:20-21），OssConfig 属性 Bean 无条件存在且值为空串（${OSS_ACCESS_KEY_ID:}）而非 null，不存在 OCR 所述"null 凭证/NPE"机理 | 生产 `STORAGE_TYPE: local`（docker-compose.prod.yml）且未注入 OSS_ACCESS_KEY_ID/SECRET 时，短信以空 AK/SK 初始化，发送时才报错（找回密码不可用） | SmsConfig 增独立 access-key-id/secret 属性（缺省回退 OSS），或在启动自检中显式校验 |
| 22 | low | PARTIAL | BE/config/SmsConfig.java:19-20 | smsClient 无条件创建、并被 AuthService.java:66 构造注入属实；偏差：signName/templateCode 在 application.yml:76-77 已有默认值，默认不为空，真实故障模式是"SMS 环境变量配错时延迟到发送时暴露"而非启动即空配置 | 仅在 SMS_SIGN_NAME/SMS_TEMPLATE_CODE 被显式置空时 | @PostConstruct 校验非空（或 @ConditionalOnProperty 守卫），让误配在启动期暴露 |
| 23 | low | PARTIAL | BE/config/SecurityConfig.java:62 | 只配 accessDeniedHandler 属实；javap 实证 spring-security-config 7.1.0 的 ExceptionHandlingConfigurer 在无默认 entry point 时 `new Http403ForbiddenEntryPoint()`，且过期 token 由 validateToken 返回 false（JwtTokenProvider.java:270-277）→ 匿名/过期请求得 **403 空体**而非 401 JSON，此点成立；反证："前端无法触发重新登录"不成立——aigicrm-ui/src/utils/request.js:63-64 明确 `status===403 && !isTokenValid()` → handleSessionExpired；且 403 是项目已声明口径（PERMISSION-AUDIT-PROGRESS.md:90 "匿名拒绝断言 401 → 403，SecurityConfig BS-A09 统一 403 语义，测试服实测一致"） | 携带过期/无效 token 或不带 token 访问受保护接口即现（前端已被补偿分支覆盖） | 若要"与全站 Result 口径一致"，显式配 authenticationEntryPoint 输出 401 JSON（或至少把 403 也补 JSON 体） |
| 24 | low | BY_DESIGN | BE/config/SecurityConfig.java:75-77 | catch 内无日志属实且注释明示"审计旁路失败不得改变拒绝语义"；03-fail-open-audit.md F7 已裁定 "SecurityAuditService Redis/DB 故障 → 审计事件丢失（设计如此，故障期越权探测无痕）" | 仅当 SecurityAuditService 持续故障时 | 可选补一行 `log.warn(...)` 提升可观测性（不改变拒绝语义），非必需 |
| 25 | low | CONFIRMED | BE/config/SecurityConfig.java:30-31 | 类上仅 `@RequiredArgsConstructor`，无 `@Slf4j`/无 logger 字段属实（依赖第 24 条的修复才需要） | 仅在第 24 条采纳日志建议时成为前置条件 | 与第 24 条一并加 `@Slf4j` |
| 26 | low | PARTIAL | BE/config/MybatisPlusConfig.java:160-163 | getTenantId 在多集团分支反复 `log.warn` 属实（TenantLineHandler 每条 SQL/每张表调用，:110-117），多集团用户会刷日志；偏差：`.sorted().findFirst().get()`"不安全"站不住——上游 :146-151 已保证 size>1，Optional 必非空，groupIds 来自 DB 不含 null | 一人多集团且启用租户隔离时（当前产品裁决为单集团，少量存量多集团账号） | 降为 debug 或按请求去重；用 `min(Long::compareTo)` 替代 `sorted().findFirst()` |
| 27 | medium | CONFIRMED | BE/config/MybatisPlusConfig.java:73-80,125-128 | 自检仅做"白名单表→须有 tenant_id 列"单向；反向缺失时新表落入 `ignoreTable=true`（:127）不加租户条件且自检通过（fail-open），属实；项目自身已采纳该待办：05-gpt-review-20260922-adjudication.md P2-4 "TENANT-P2-003 反向租户完整性门禁(有列→该在白名单)"，09-gpt-reaudit…adjudication.md 记录现存 4 张含 tenant_id 未白名单表（perm_scope_field/perm_scope_org/biz_sea_config_region/biz_sea_config_scope） | 新增或现存的带 tenant_id 表未登记 TENANT_TABLES，且 TENANT_ENABLE=true（生产已强制 true） | 自检补反向差集：一次查 information_schema 中所有含 tenant_id 的表，与白名单比对，缺配即启动失败/告警 |
| 28 | low | CONFIRMED | BE/config/MybatisPlusConfig.java:30-31 | 注释写"全部 25 张业务表"，而 `Set.of(...)`（:35-42）实为 26 个元素（含 biz_task）；V19__drop_backup_tables.sql 已 DROP 8 张 `*_bak_*`，注释仍称"5 张 *_bak_* 快照表" | 不触发（文档准确性） | 注释同步为 26 张，并删除已删备份表描述 |
| 29 | low | PARTIAL | BE/config/WhitelistedJsonRedisSerializer.java:28-30 | 构造器接受任意 ObjectMapper、对 null 不 fail-fast 属实；偏差：唯一实例化点 RedisConfig.java:39 传入的 mapper 已 activateDefaultTyping + 白名单，且类 Javadoc:20/27 已声明契约；更关键的是"无 default typing 的 mapper"根本不会按 @class 多态反序列化（readValue(Object.class) 只会退化成 Map），该分支描述有误 | latent：仅当未来有人用 permissive validator（LaissezFaire）的 mapper 构造本类 | 构造器加 `Assert.notNull(mapper, ...)`（一行防御） |
| 30 | low | BY_DESIGN | BE/config/WhitelistedJsonRedisSerializer.java:35 | `serialize(null)→new byte[0]` 属实，但类 Javadoc:19 已显式写明"null 输入按 RedisSerializer 惯例返回空字节数组/反序列化返回 null"，且 :46 与之对称 | 无调用方以 null 值写入（各调用点均有非空守卫） | 无需修改；如担心歧义可在方法级 Javadoc 复述 |
| 31 | medium | CONFIRMED | BE/config/LogAspect.java:34-38 | 切点仅 `execution(* com.aigicrm.module..controller..*(..))`；全仓仅 `com/aigicrm/task/ScheduledTaskController.java` 在包外（:1/13/19 `POST /api/system/task/check-expire`，仅 admin 可触发 :22-25），且无 @AigicrmLog（全仓仅 3 处注解：SysUserController:63、ApprovalController:77/85）→ 该全局批处理触发无审计记录 | 管理员调用 POST /api/system/task/check-expire 即产生审计盲区 | 切点扩为 `execution(* com.aigicrm..*Controller.*(..))`（或补 `@within(@RestController)`） |
| 32 | low | PARTIAL | BE/config/LogAspect.java:59 | 机制属实：成功路径 detail 原样（:59）→ SysLogService.java:69-77 不截断 → `sys_log.detail varchar(1000)`（V1__baseline.sql:1015），插入异常仅 debug 丢弃（:62-64），而失败路径截断 200（:89）；偏差：可达的自由文本源都被业务列先兜住——审批 comment varchar(500)（V1:116）、任务取消原因写入 completion_note varchar(1000)（V2026.09.19.8:15）、发票/回款作废原因 varchar(500)（V2026.09.19.7:11/19）、文件名 varchar(200)（V1:357），实体 getter 取值列同样 ≤500，故超长会先失败并走已截断的失败路径 | latent：未来任何"成功写入 TEXT 列/不落库"且命中白名单 key（reason/comment/name…）的自由文本字段 | 两条路径统一截断（如 ≤1000 字符），避免未来字段变更时静默丢审计 |
| 33 | low | PARTIAL | BE/config/LogAspect.java:113 | 条件确属冗余（凡含 "/system/users" 必含 "/system/user"）；偏差：OCR 说"前半个 \|\| 条件是永远走不到的死分支"方向反了——短路后先判的正是 `/system/users`，真正恒被覆盖的是后半条件 | 不触发（可读性，行为等价） | 简化为 `if (uri.contains("/system/user")) return "人员";` |
| 34 | low | PARTIAL | BE/config/LogAspect.java:111 | `/incentive` → "文件" 属实；IncentiveController 有 8 个写端点（:31/38/46/68/83/100/109/166）且全无 @AigicrmLog → 激励写操作记为"文件"；偏差：前端模块标签是固定 5 类（src/views/system/log.vue:192 客户/登录/文件/人员/系统，无"激励"），可能是"不新增 tab"的权宜归并而非缺陷 | 激励模块任一写操作后查审计日志即见语义错位 | 改归"客户"或在前后端同时新增"激励"标签 |
| 35 | low | PARTIAL | BE/config/RedisConfig.java:25-32 | 机制属实：DefaultTyping.NON_FINAL + 4 个前缀白名单，BigDecimal 非 final 会写入 @class 而 `java.math.` 未放行 → 反序列化非法类型；偏差：当前唯一缓存类型 PermissionContext（PermissionCacheService.java:60）及其嵌套类型无 BigDecimal（grep 无命中），且读失败被 catch 后降级回源查库（PermissionCacheService.java:47-53）→ 后果是缓存未命中 + warn，不是功能故障 | latent：未来把含 BigDecimal/BigInteger 等非 final JDK 类型的 DTO 放入 Redis 时 | 白名单补 `java.math.`（或直接收敛为显式类清单） |
| 36 | low | BY_DESIGN | BE/config/RedisConfig.java:27 | `allowIfSubType("com.aigicrm.")` 确按前缀放开项目全部类，描述属实；但这是项目自己写明并已实施的 M5 决策——RedisConfig.java:24 注释"多态反序列化从 LaissezFaire(任意类型) 收紧为项目+JDK 白名单，防外部污染 Redis 触发 gadget"；且触发前提是 Redis 已被写入污染（此时攻击者可直接投毒 PermissionContext 本身，收紧前缀的边际收益有限） | 需先获得 Redis 写权限（弱口令/内网横向/误写） | 可选纵深：收敛为显式类（如 com.aigicrm.security.PermissionContext 及其嵌套类型） |
| 37 | low | CONFIRMED | BE/config/RedisConfig.java:44 | javap 实证 spring-data-redis 4.1.0 `RedisAccessor implements InitializingBean`（RedisTemplate 继承之）→ 作为 @Bean 返回时 Spring 初始化回调必调 `afterPropertiesSet()`，第 44 行手动调用确属重复 | 不触发（冗余代码，无行为差异） | 删除第 44 行 |

---

## 统计

### 一、裁定的分布（合计 37）

| 裁定 | 条数 | 序号 |
|---|---|---|
| CONFIRMED | 15 | 1, 2, 5, 6, 7, 12, 14, 17, 18, 19, 25, 27, 28, 31, 37 |
| PARTIAL | 16 | 4, 8, 9, 10, 11, 13, 20, 21, 22, 23, 26, 29, 32, 33, 34, 35 |
| FALSE | 1 | 16 |
| BY_DESIGN | 5 | 3, 15, 24, 30, 36 |
| UNVERIFIABLE | 0 | — |

### 二、校准后严重度分布（合计 37）

| 严重度 | 条数 | 序号 |
|---|---|---|
| critical | 0 | — |
| high | 0 | — |
| medium | 5 | 6, 12, 21, 27, 31 |
| low | 32 | 其余全部（含 OCR 原判 high 的 3/8/21 与 12 已下调） |

> 说明：OCR 原报 high 4 条（3/8/12/21）经核对全部下调——第 3 条是被单测固化的既定契约、第 8 条生产已收敛为精确源、第 12 条是已挂账的性能项、第 21 条是 SMS 与 OSS 凭证耦合的配置陷阱；无一具备"当前即可高危触发"的条件。真正可在当前配置下触发的安全类问题只有第 6 条（提权防护绕过）与第 31 条（审计盲区）。

### 三、最值得优先修的 5 条（按风险排序）

| 优先 | 序号 | 一句话理由 |
|---|---|---|
| P1 | 6 | `rank()` 依赖 ordinal 使 DEPT 调用者可授出实际范围更宽的 `org_and_children`，提权防护被绕过（DataScopeType.java:83-85 + SysUserService.java:191）。 |
| P2 | 27 | 租户自检只做单向校验，带 `tenant_id` 而未入白名单的表会静默失去隔离（fail-open），现存已确认 4 张，项目自身亦已立 TENANT-P2-003。 |
| P3 | 31 | 切点漏掉包外的 ScheduledTaskController，管理员触发全库批处理无任何审计记录（LogAspect.java:35）。 |
| P4 | 21 | 短信复用 OSS 的 AK/SK 而生产 `STORAGE_TYPE=local`，漏配 OSS 密钥即导致找回密码/验证码不可用（SmsConfig.java:22-23）。 |
| P5 | 12 | DeptTreeUtils 按部门节点逐次查库（N+1，13 处调用点），列表/统计接口的 DB 往返随子树规模放大（DeptTreeUtils.java:31-36）。 |

### 四、复核方法备注

- 所有"是否有守卫/调用方"的结论均来自全仓 grep（`BaseEntity`、`needChildren`、`rank()`、`ImportGuard.`、`DeptTreeUtils.`、`@RequestBody.*Map`、`@AigicrmLog`、`ossConfig`、`BigDecimal`、`ObjectOutputStream` 等）与 glob（`*Controller.java` 全域枚举）。
- 两处框架行为以 jar 反编译实证，未凭记忆断言：`spring-security-config 7.1.0` 的 ExceptionHandlingConfigurer 默认 `new Http403ForbiddenEntryPoint()`（第 23 条）；`spring-data-redis 4.1.0` 的 `RedisAccessor implements InitializingBean`（第 37 条）；`aliyun-sdk-oss 3.17.4` 的 `OSSClient.shutdown()`（第 16 条）。
- 配置与部署结论来自 `application.yml`、`docker-compose.prod.yml`、`nginx.conf.template`、`nginx-ssl.conf`；迁移结论来自 `db/migration/`（V1/V19/V2026.09.19.7/V2026.09.19.8）。
- 判定 BY_DESIGN 的 5 条均引用了项目权威基线（`docs/security-audit/03-fail-open-audit.md` F7、`05-gpt-review-20260922-adjudication.md`、`09-gpt-reaudit-external1-20260922-adjudication.md`、`PERMISSION-AUDIT-PROGRESS.md:90`）或源码内已声明的设计注释。
- 未发现"描述的是已修复行为"的条目；无 UNVERIFIABLE 条目。
