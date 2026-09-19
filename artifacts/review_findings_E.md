# 分片E 审查发现：数据数据库 + AI/LLM + 云架构成本

> 审查模式：只读。未修改任何业务代码、未执行任何数据库写操作。
> 审查范围：`02-迭代源代码/aigicrm-backend/`（184 个 Java 文件，12 个业务模块）、`src/main/resources/`（Flyway 迁移 34 个 SQL）、`aigicrm-ui/`、`06-部署与运维/`。
> 基线：SonarQube `Eastdongjun_AigiCRM` 当前 OPEN 问题为 0。

## 问题清单

| 序号 | 严重级别 | 文件路径 | 问题描述 | 对应技能 | 建议方向 |
|------|----------|----------|----------|----------|----------|
| E-01 | Critical | `06-部署与运维/阿里云环境/aigicrm.com.pem` | SSH RSA 私钥文件以 `-rw-------` 明文存放在项目目录中；`sonar analyze secrets` 已确认检出 `RSA Private Key (secrets:S6706)`。该文件未被读取内容，仅按规则标注。若曾进入 Git 历史即属泄露，需立即轮换主机密钥并从仓库移除 | 环境密钥管理 / ai安全 | 立即轮换该私钥；将 `*.pem` 加入 `.gitignore` 与 SonarQube 排除外的密钥扫描；改用 1Password/云 Secret Manager 托管 |
| E-02 | High | `aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java:219,225,233` | `indicatorStats()` 对 `biz_customer_indicator`、`biz_invoice`、`biz_payment` 三表分别 `selectList(null)` 全量加载到 JVM 内存，再用 Java `HashMap` 做按 customerId 聚合求和。随着开票/回款记录增长，每次看板请求都会把全表数据拉进内存，堆内存与 RT 都会线性恶化 | 数据库优化 / sql-diagnose-refine / 高级数据工程师 | 改为 SQL 端 `SELECT customer_id, SUM(amount) FROM biz_invoice WHERE deleted=0 GROUP BY customer_id` 一次聚合返回；或引入统计表/物化视图 |
| E-03 | High | `StatisticsQueryService.java:481` | `customerRankings()` 内部再次调用 `indicatorStats()`（上一条已含全表扫描），同一请求内重复执行两份全量加载，重复开销翻倍 | 数据库优化 | 将 indicatorStats 的计算结果下沉为私有方法并复用内存对象，或合并为一次查询 |
| E-04 | High | `StatisticsQueryService.java:161-172`（dailyStats）、`190-204`（weeklyStats） | 按天/按周统计文件上传数时，在 Java 循环里对 `biz_file` 逐日（最多 31 次）逐周（最多 5 次）各发一次 `selectCount`，典型 N+1 聚合模式 | sql-diagnose-refine / 数据库优化 | 改为 `SELECT DATE(created_at) d, COUNT(*) ... WHERE created_at BETWEEN ? AND ? GROUP BY DATE(created_at)` 单条 SQL |
| E-05 | High | `CustomerService.java:573-574`、`1102-1103`、`1215-1217` | 三处循环内逐条 `userMapper.selectById(...)`：客户日志列表按行查用户、共享列表按行查用户、回款列表按行查创建人。列表分页稍大即成 N+1 | sql-diagnose-refine | 仿照 `StatisticsQueryService:322-329` 已有的 `selectByIds(集合)` 批量预取模式统一处理 |
| E-06 | High | `CustomerService.java:155-176`（pageSharedToMe / pageSharedByMe） | 先把 `biz_customer_share` 全量 `selectList` 到内存，再把全部 customerId 拼进 `IN (...)` 分页查询客户。共享记录多时 IN 列表过长、且共享表未分页 | 数据库优化 | 改为 join `biz_customer_share` 直接 `selectPage`，或对 share 表先分页再按 ID 查客户 |
| E-07 | High | `sea/listener/SeaImportListener.invoke`（CustomerImportDTO 导入） | 每导入一行都调用 `customerService.existsByName(name)` 做一次 `selectCount` 查重；千行 Excel 即千次 SQL 往返 | sql-diagnose-refine / 性能剖析 | 导入开始时一次性 `SELECT customer_name FROM biz_customer WHERE customer_name IN (本次批次名称集合)` 或建临时表做集合查重 |
| E-08 | Medium | `application.yml`（未配置 HikariCP） | 数据源未显式配置 `spring.datasource.hikari.*`（maximum-pool-size、connection-timeout、max-lifetime 等），全部走 Hikari 默认值（池 10）。生产单实例 + 看板类全量查询下，连接池容易被慢查询占满 | 数据库优化 / 云成本治理 | 显式配置 `maximum-pool-size`、`connection-timeout: 3s`、`max-lifetime`，并结合 RDS 实例规格核算 |
| E-09 | Medium | `PermissionCacheService.java:131` | `evictAll()` 使用 `redisTemplate.keys("perm:ctx:*")`。Redis `KEYS` 是 O(N) 阻塞命令，权限用户量上来后会卡住整个 Redis 实例 | 缓存与搜索 | 改用 `SCAN` 游标分批扫描删除，或维护一份 userSet 集合记录当前在线用户再逐个 DEL |
| E-10 | Medium | 全部 `@Transactional` 均未声明 `readOnly = true` | 统计/查询类方法（StatisticsQueryService、各 Service 的 list/page/get）走的是默认读写事务。虽 Spring 对未标注方法不强制开事务，但所有写方法也未隔离传播级别；只读路径无法被优化器识别为只读连接 | 数据库设计 / sql数据库助手 | 对只读 Service 方法标注 `@Transactional(readOnly = true)`，写方法显式 `rollbackFor = Exception.class` |
| E-11 | Medium | `StatisticsQueryService` 整类 870 行、`CustomerService` 1280 行 | 两个大类承担了统计聚合、客户 CRUD、共享、导入、指标、发票回款等多种职责，且统计计算在内存中以 `Map<Long, List<int[]>>` 维护临时状态，属贫血 Service + 过程式脚本，可测性与复用性差 | 数据建模 / 领域建模 | 拆分为 CustomerQueryService / CustomerWriteService / StatisticsAggregateService；将"核心客户判定""指标评分"抽为领域对象 |
| E-12 | Medium | `db/migration/V1__baseline.sql` 字符集与排序规则不一致 | 多数表 `utf8mb4_0900_ai_ci`，但 `biz_payment` 为 `utf8mb4_general_ci`；且 `tenant_id` 列注释为乱码 `é›†å›¢ID`（utf8 字节被 latin1 解读）。后续 join 这两张表的字符串列可能触发隐式转换，索引失效 | 数据库表结构设计 / 数据质量审计 | 统一 `utf8mb4_0900_ai_ci`；用 `ALTER ... CONVERT TO CHARACTER SET` 修复并新增迁移修正注释 |
| E-13 | Medium | `V1__baseline.sql:565-566` | `biz_invoice` 同时存在 `invoice_no` 与 `invoice_number` 两列，语义重复；`invoice_company`、`invoice_type` 仅在此表存在，未约束长度/非空 | 数据库表结构设计 | 确认哪列在用，废弃另一列；补唯一索引 `(customer_id, invoice_no)` 防重复开票 |
| E-14 | Medium | 无 Elasticsearch / 全文检索集成 | 客户搜索走 `LIKE %keyword%` 前置模糊（CustomerService `applyCommonFilters`），客户名/公司名稍大后无法走索引 | 缓存与搜索 / rag架构（不适用向量，适用全文） | 评估 MySQL 全文索引（`FULLTEXT`）或引入 OpenSearch；至少对高频搜索列建组合索引 |
| E-15 | Medium | `application.yml` `logging.level.com.aigicrm: debug` | 默认 profile 输出 debug 日志，且 `mybatis-plus.configuration.log-impl: StdOutImpl` 会把全部 SQL 打到标准输出。虽然 prod profile 已切到 Slf4j+info（application-prod.yml 已修复），但本地/未激活 prod 时 SQL 全量打印有泄漏业务数据风险 | 数据质量审计 / ai安全 | 确认部署脚本永远带 `--spring.profiles.active=prod`；本地开发日志落到文件而非 stdout |
| E-16 | Low | `SysRoleOrgMapper.java:19,27`、`SysUserOrgMapper.java:19`、`BizApprovalConfigMapper.java:12` | 注解 SQL 中使用 `SELECT *`，未显式列字段。表结构演进后易带出不需要的大字段 | sql数据库助手 | 改为显式列名；MyBatis-Plus 注解 SQL 与 MP Wrapper 混用建议统一 |
| E-17 | Low | `RedisConfig.java` 多态白名单 | 已将 LaissezFaire 收紧为 `com.aigicrm./java.util./java.time./java.lang.` 白名单，做得较好；但未看到 Redis 连接密码在 cluster profile 的单独配置说明 | ai安全 / 云成本治理 | 补充 cluster profile 下密码/TLS 配置样例；评估 Redis 持久化与淘汰策略 |
| E-18 | Low | Flyway 默认 `enabled: false`，靠 `deploy-db.sh` 管理账号执行 | 生产应用账号无 DDL 权限是好的最小权限实践；但 34 个迁移中出现 `V2026.09.11.x` / `V2026.09.13.x` 与 `V20`/`V21` 混合命名，版本号排序依赖字符串，存在未来碰撞风险 | 数据库设计 | 统一版本号命名规范（建议纯 `V<n>` 顺序递增或纯日期戳，不混用） |

## 数据库与数据建模评估

**正向：**
- 表结构具备完整审计字段（`created_at`/`updated_at`/`created_by`）与逻辑删除（`deleted tinyint` + MyBatis-Plus `@TableLogic`）。
- 主外键设计合理：`biz_customer` 在 `owner_id`/`office_id`/`expire_date`/`status`/`industry`/`region_id`/`tenant_id` 均有索引；`biz_invoice`/`biz_payment` 以 `customer_id` 为外键索引。
- Flyway 迁移已建立，34 个版本脚本保留了 lineage 文档（`MIGRATION-LINEAGE.md`）。
- 实体使用 `@TableField(fill = INSERT/INSERT_UPDATE)` + `@TableLogic`，与表结构映射规范。
- 递归 CTE（`SysDeptMapper`、`SysOrgAdminMapper`）用于组织树查询，写法正确。

**主要问题：**
1. **统计查询反模式**：看板/指标模块普遍"全表拉到内存再聚合"（见 E-02/E-03/E-04），随数据量增长会成为第一个性能瓶颈。
2. **N+1 残留**：客户日志、共享、回款三处列表仍在循环里 `selectById`（E-05）；Excel 导入逐行 `selectCount`（E-07）。
3. **连接池未调优**（E-08），默认 HikariCP 在慢 SQL 场景下风险大。
4. **字符集/列冗余**：见 E-12/E-13。
5. 未使用 `@Transactional(readOnly = true)` 区分读写事务（E-10）。

## AI/LLM 集成评估（重点）

**结论：当前代码库中不存在 AI/LLM 实现。**

逐项核查：
- `pom.xml` 无 `spring-ai` / `openai-java` / `langchain` / `dashscope-sdk` / `tiktoken` 等任何 LLM 依赖。
- 全仓库 grep `openai|llm|chatgpt|prompt|embedding|vector|tongyi|qwen|doubao|moonshot|deepseek|anthropic|claude` 仅命中 `UserDetailsServiceImpl.java` 一处无关字符串。
- `aigicrm-ui/package.json` 与源码中无 AI SDK。
- 无 `module.ai` / `module.llm` 包；无向量库依赖；无 Prompt 模板目录；无 Token 计量代码。

**因此下列 AI/LLM 维度在当前代码库中"未触发"，属"前置架构风险"，建议在引入 AI 功能前预先规划：**

| 维度 | 现状 | 前置建议 |
|------|------|----------|
| LLM API Key 管理 | 无 | 项目已有良好环境变量习惯（`${JWT_SECRET}` 无默认值、`${DB_PASSWORD}` 必填），接入 LLM 时应延续 `${LLM_API_KEY}` 无默认值策略，严禁硬编码 |
| Prompt 管理 | 无 | 预留 `prompts/` 模板目录 + 版本号；禁止散落在 Service 字符串中 |
| Token 成本控制 | 无 | 接入时需引入 `tiktoken` 等价计量、按用户/租户预算上限、结果缓存（幂等 Prompt → Redis） |
| RAG / 向量库 | 无 | 若做客户文档问答，需评估 MySQL 8.9 向量类型 vs Milvus/Qdrant；切分策略需与 `biz_file` 模块打通 |
| Agent / 工具调用 | 无 | 项目已有 MyBatis Mapper 层，可作为 Tool 化候选；但必须先做工具白名单与参数校验，防 Prompt 注入触达业务表 |
| AI 安全（Prompt 注入/输出审核） | 无 | 当前 `WhitelistedJsonRedisSerializer` 已演示"白名单"思路，AI 输入输出应同样做白名单+审核链 |
| 流式输出 / 超时 | 无 | 接入时必须配置 `RestTemplate/WebClient` 超时（建议 connect 2s / read 30s）与重试，避免线程耗尽 |

**云架构与密钥现状中与 AI 安全相关的一点：** `application.yml` 中阿里云 OSS 配置注释明确写明"已泄露的旧密钥已作废"，说明历史上发生过密钥泄露，建议把这个教训复用到未来的 LLM Key 管理 SOP。

## 云架构与成本评估

**部署形态：**
- 阿里云 ECS（`aigicrm.com.pem` 即 SSH 私钥，见 E-01），单机部署为主；Redis 已预留 cluster profile 但未启用。
- 文件存储：`STORAGE_TYPE` 默认 local，生产可切阿里云 OSS（endpoint/bucket 已配置）。
- 数据库：MySQL 外部实例（`DB_HOST:3308`），应用账号无 DDL 权限，Flyway 走 deploy-db.sh。

**成本与资源：**
- 未看到 ECS 规格/实例系列说明，无法核算 CPU/内存利用率。
- HikariCP 池 10、Redis 单实例默认配置，均未与 ECS 规格联动调优。
- 500MB 文件上传上限（`max-file-size: 500MB`）+ local storage，需关注磁盘增长；OSS 切换后应确认 lifecycle 策略。
- 无 CDN、无 SLB（单机）、无消息队列。
- `SysLog` 操作日志全量落库，未见 TTL/归档策略，长期成本需关注。

**优化空间：**
1. 尽快轮换 SSH 私钥（E-01，Critical）。
2. 看板类全表聚合（E-02）在数据量增长后会推高 RDS CPU 与连接占用，是最优先的成本/性能双赢优化点。
3. 统计接口建议加短 TTL 缓存（Redis 已有，10s~60s），避免每次刷新看板都全表扫描。
4. 单实例 → 多实例时需把 `perm:ctx:*` 缓存的 `evictAll()` 从 KEYS 改为 pub/sub 或 SCAN（E-09）。

## 技能覆盖清单

**已调用技能（方法论已用于审查）：**
- `sql-diagnose-refine`：按"锁定环境→分离 SQL→核对粒度→最小修复"流程审查 MyBatis 注解 SQL 与统计查询。
- `数据库优化` / `数据库表结构设计`：核对 V1 baseline 索引、字符集、冗余列。
- `缓存与搜索`：审查 RedisConfig、PermissionCacheService、KEYS 命令风险。
- `数据建模`：评估贫血 Service、审计字段、逻辑删除。
- `环境密钥管理`：对 `.pem` 文件执行 `sonar analyze secrets` 并按规则不读取内容。
- `ai安全`：确认 LLM Key/输入输出边界的前置风险。
- `云成本治理`：评估单机 ECS + OSS + RDS 的资源配置与成本优化方向。
- `高级数据工程师`：统计聚合查询评估。

**未找到/不适用而跳过的技能：**
- `aws架构师` / `azure云架构` / `gcp云架构`：项目实际部署在阿里云，非 AWS/Azure/GCP，方法论不适用。
- `rag架构` / `大模型知识库` / `高级机器学习` / `智能体设计` / `智能体工作流设计` / `智能体测试台` / `智能体记忆` / `记忆工程` / `高级提示词工程` / `提示词治理` / `大模型成本优化`：代码库中尚无任何 AI/LLM 代码与依赖，这些领域当前无对象可审；已在"AI/LLM 集成评估"中给出前置规划建议。
- `统计分析`：项目统计模块为业务聚合而非数理统计（回归/分布检验），不适用。
- `数据质量审计`：无独立数据质量监控/清洗管道，仅在列冗余、字符集乱码维度做了点评。
- `高级数据科学家`：同上，不适用。
