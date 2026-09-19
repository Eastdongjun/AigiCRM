# 分片C 审查发现：测试质量 + 性能 + 依赖技术栈

> 审查时间：2026-09-16 | 项目：AIGICRM | 审查模式：只读静态分析 + 实测测试运行

---

## 问题清单

| 序号 | 严重级别 | 文件路径 | 问题描述 | 对应技能 | 建议方向 |
|------|----------|----------|----------|----------|----------|
| 1 | Critical | `module/incentive/service/IncentiveService.java:403-414` | `recalculateAllScores()` 全表加载 `selectList(null)` 后循环内逐条 `categoryMapper.selectById()`，再 `updateById`。记录数达万级时产生 N+1 查询（1 + N + N 次 DB 调用），长事务持锁风险 | 性能剖析 / 测试实现 | 批量预加载 category 到 Map（`selectBatchIds`），批量更新改用 `updateBatchById`；加事务超时和分页批处理 |
| 2 | High | `module/customer/service/CustomerService.java:573-575` | 客户操作日志循环内 `userMapper.selectById(log.getUserId())` 逐条查用户，日志量大时 N+1 | 性能剖析 | 收集所有 userId → `selectBatchIds` → Map 回填 |
| 3 | High | `module/customer/service/CustomerService.java:1102-1104` | 客户分享列表循环内 `userMapper.selectById(s.getSharedToUserId())` 逐条查用户 | 性能剖析 | 同上，批量 `selectBatchIds` |
| 4 | High | `module/customer/service/CustomerService.java:1115-1117` | 循环 userIds 逐条 `shareMapper.selectCount()` 判断分享状态，N 次 COUNT 查询 | 性能剖析 | 改为一次 `GROUP BY sharedToUserId` 或 `selectList` 后内存聚合 |
| 5 | High | `module/approval/service/ApprovalService.java:107-108` | 审批列表分页循环内 `fillApprovalNames(a)` 每条做 2 次 `selectById`（申请人 + 客户），一页 20 条 = 40 次额外查询 | 性能剖析 | 收集本页所有 applicantId/customerId → `selectBatchIds` → Map 批量回填 |
| 6 | High | `module/approval/service/ApprovalService.java:152-156` | 审批详情节点循环内 `userMapper.selectById(node.getApproverId())` 逐条查审批人 | 性能剖析 | 批量收集 approverId → `selectBatchIds` |
| 7 | High | `task/CustomerExpireTask.java:143-150` | 定时任务循环客户逐条 `reminderMapper.selectCount()` 检查去重，逾期客户多时 N+1 | 性能剖析 | 先一次 `selectList` 查出已有 pending 提醒的 customerId 集合，内存判断 |
| 8 | High | `module/statistics/service/StatisticsQueryService.java:219,225,233,440` | 4 处 `selectList(null)` 全表扫描：指标、发票、回款、激励分类，全部加载到内存做聚合 | 性能剖析 | 改用 SQL `GROUP BY customerId` 聚合查询，或加分页/时间范围限制 |
| 9 | High | `module/incentive/service/IncentiveService.java:403,943` | 2 处 `selectList(null)` 全表加载激励记录和分类 | 性能剖析 | 第 403 行改为分批查询；第 943 行分类表小可接受但建议缓存 |
| 10 | High | `module/customer/service/CustomerService.java:709-713` | `getAllActiveCustomers()` 无分页全量查询所有非删除非公海客户，被统计服务多处调用 | 性能剖析 | 加时间范围或分批流式处理；统计场景改为 SQL 聚合 |
| 11 | High | `module/auth/service/AuthService.java` + `controller/AuthController.java` | 认证模块（登录、登出、短信验证码、忘记密码）**0 个测试类**，核心安全路径无覆盖 | 测试策略 / tdd开发 | 补充 AuthService 单元测试：登录成功/失败、token 生成/校验、短信限流、忘记密码流程 |
| 12 | High | `module/score/service/ScoreService.java` + `controller/ScoreController.java` | 积分模块（评分排名）**0 个测试类** | 测试策略 | 补充 ScoreService 单元测试：排名计算、文件数统计、趋势计算 |
| 13 | Medium | `pom.xml`（build/plugins） | 未配置 JaCoCo 覆盖率插件，无法度量后端测试覆盖率 | 测试策略 / 高级qa | 添加 `jacoco-maven-plugin`，配置 `prepare-agent` + `report` goal，设置覆盖率门禁 |
| 14 | Medium | `vitest.config.js` | vitest 未配置 `coverage` 选项（provider/reporter/thresholds），无法度量前端覆盖率 | 测试策略 | 添加 `test.coverage: { provider: 'v8', reporter: ['text','html'], thresholds: { lines: 60 } }` |
| 15 | Medium | `package.json` devDependencies | `@playwright/test` 已安装但无 `playwright.config.*` 配置文件、无 `e2e/` 目录、无 `test:e2e` 脚本——E2E 测试能力闲置 | playwright初始化 / 测试策略 | 要么初始化 Playwright E2E 项目（`playwright init` + 编写关键流程用例），要么移除依赖减少维护负担 |
| 16 | Medium | `pom.xml` properties | Flyway 版本偏斜：Maven 插件 12.4.0 vs 生产 docker 镜像 9.22.3（pom.xml 注释已标注） | 依赖审计 / 技术栈评估 | 同步生产镜像 Flyway 版本到 12.4.0，或降级 Maven 插件到 9.22.3，消除跨大版本迁移风险 |
| 17 | Medium | 全部测试类（47个） | 所有后端测试均为 `@ExtendWith(MockitoExtension.class)` 纯 Mockito 单测，无 `@SpringBootTest`/`@DataJpaTest`/`@WebMvcTest` 集成测试，无数据库层验证 | 测试策略 / 高级qa | 补充关键路径集成测试：Mapper SQL 正确性、事务回滚、缓存 evict 链路 |
| 18 | Medium | `vite.config.js` | 前端表格组件（`el-table`）未使用虚拟滚动（`el-table-v2` 或 `virtual-scroll`），客户列表大数据量时 DOM 渲染压力 | 性能剖析 / 前端选库 | 客户主表数据量增长后评估引入 `el-table-v2` 或 vue-virtual-scroller |
| 19 | Medium | `module/sea/service/SeaConfigService.java:166,180` | 2 处 `deptMapper.selectList(null)` 全量加载部门表到内存构建 Map | 性能剖析 | 部门表数据量通常较小可接受，但建议加本地缓存（Caffeine）减少重复查询 |
| 20 | Low | `pom.xml` EasyExcel 3.3.4 | EasyExcel 3.3.4 版本较旧（最新 4.x），但功能稳定且无已知安全漏洞 | 依赖审计 | 低优先级跟踪，下次大版本升级时评估 |
| 21 | Low | `pom.xml` aliyun-sdk-oss 3.17.4 | 阿里云 OSS SDK 版本可升级到最新稳定版 | 依赖审计 | 低优先级，跟随阿里云 SDK 迭代 |
| 22 | Low | `package.json` 所有 dependencies 均使用 `^` 前缀 | 所有依赖版本号均为 `^` 大范围匹配，lockfile 锁定但 CI 重建时可能引入新版本 | 依赖审计 | 关键生产依赖（vue/element-plus/pinia）考虑锁定 `~` 或精确版本 |
| 23 | Low | `pom.xml` 测试类命名 | 部分测试类命名偏长（如 `SysUserPageGroupScopeTest`、`PermissionSecurityFixTest`），但语义清晰可接受 | 测试实现 | 无强制修改，保持现状 |

---

## 测试覆盖评估

### 后端测试

| 维度 | 数据 |
|------|------|
| 主源码 Java 文件 | 184 个 |
| 测试类文件 | 47 个 |
| 测试类/主文件比 | 25.5% |
| 测试方法数 | 498 个（`mvn test` 实测） |
| 通过率 | 498/498 = 100%（0 失败、0 错误、0 跳过） |
| 测试框架 | JUnit 5 + Mockito 5.17.0 + ArchUnit 1.4.2 |
| 覆盖率度量 | **无 JaCoCo 配置**，无法量化行/分支覆盖率 |

**模块级测试分布：**

| 模块 | 主代码文件（不含 entity/mapper/dto） | 测试类 | 覆盖率估算 |
|------|------|------|------|
| system | 23 | 8 | 中（核心 Service 有测试） |
| incentive | 3 | 7 | 高（Service 层测试充分） |
| approval | 4 | 3 | 中 |
| customer | 3 | 2 | 中低（Service 有测，Controller 部分覆盖） |
| file | 5 | 3 | 中 |
| sea | 4 | 2 | 低 |
| statistics | 2 | 2 | 中 |
| reminder | 2 | 1 | 低 |
| **auth** | **2（AuthController + AuthService）** | **0** | **零覆盖** |
| **score** | **2（ScoreController + ScoreService）** | **0** | **零覆盖** |
| common（scope/engine） | — | 多 | 高（ScopeService/OrgScopeResolver/PermissionRuleEngine 均有测试） |

**测试金字塔健康度：**
- 底层（单元测试）：充足，498 个测试方法全部为纯 Mockito 单测，执行快（8.4s 完成）
- 中层（集成测试）：**缺失**，无 `@SpringBootTest`、无 Mapper SQL 验证、无事务行为验证
- 顶层（E2E）：**缺失**，无任何端到端测试

### 前端测试

| 维度 | 数据 |
|------|------|
| 源文件 | 79 个 .vue + 40 个 .js（非 spec）= 119 个 |
| 测试文件 | 29 个 .spec.js |
| 测试文件/源文件比 | 24.4% |
| 测试方法数 | 175 个（`npm test` 实测） |
| 通过率 | 175/175 = 100%（29 文件全过） |
| 测试框架 | Vitest 4.1.10 + @vue/test-utils 2.4.11 + jsdom 30 |
| 覆盖率度量 | **无 coverage 配置**，无法量化 |

**前端测试分布：**
- utils/ 层：14 个 spec（customer-fields、token、time-label、pagination 等纯函数，测试充分）
- composables/ 层：3 个 spec（useCustomerDetail、useIncentiveUpload、useCustomerForm）
- views/ 层：9 个 spec（reminder、system、incentive、customer/detail 等关键页面）
- store/router/api：3 个 spec

**前端测试特点：**
- 纯函数/util 层测试覆盖最好
- Vue 组件测试集中在 detail 和 system 页面，列表页/MainTable 无组件测试
- 无 Playwright E2E 测试（依赖已安装但未使用）

---

## 性能风险总览

### 数据库查询风险（静态分析）

| 风险类型 | 位置 | 严重度 | 说明 |
|----------|------|--------|------|
| N+1 查询 | CustomerService:573,1102,1115 | High | 3 处循环内单条查询 |
| N+1 查询 | ApprovalService:107,152 | High | 列表分页和详情节点 |
| N+1 查询 | IncentiveService:406 | High | 全表重算积分 |
| N+1 查询 | CustomerExpireTask:145 | Medium | 定时任务逐条 COUNT |
| 全表扫描 | StatisticsQueryService:219,225,233,440 | High | 4 处 selectList(null) 无过滤 |
| 全表扫描 | IncentiveService:403,943 | Medium | 激励记录全量加载 |
| 全表扫描 | SeaConfigService:166,180 | Low | 部门表（数据量小） |
| 无分页查询 | CustomerService:709 getAllActiveCustomers | High | 全量客户加载，被统计服务多处调用 |

### 前端渲染风险

| 风险类型 | 位置 | 严重度 | 说明 |
|----------|------|--------|------|
| 无虚拟滚动 | 所有 el-table（客户主表、激励记录、审批列表等） | Medium | 数据量增长后 DOM 节点过多导致滚动卡顿 |
| 路由懒加载 | router/index.js | ✅ 良好 | 全部路由使用 `() => import()` 懒加载 |
| Element Plus 按需加载 | vite.config.js | ✅ 良好 | 已配置 AutoImport + Components 按需引入 |
| Code splitting | vite.config.js | ✅ 良好 | 已配置 vue-core chunk 分组 |

### 资源管理风险

| 风险类型 | 位置 | 严重度 | 说明 |
|----------|------|--------|------|
| OSS Client 生命周期 | OssConfig.java | ✅ 良好 | OSS Client 为 Spring Bean 单例，通过 ObjectProvider 注入 |
| Redis 缓存 | PermissionCacheService.java | ✅ 良好 | 使用 Redis 替代本地缓存，有 TTL + 精确 evict + afterCommit 延后失效 |
| 定时任务 | CustomerExpireTask.java | Medium | 每日凌晨 2 点执行，全量客户扫描 + 逐条 COUNT，数据量增长后执行时间可能延长 |

---

## 依赖与技术债评估

### 后端依赖（pom.xml）

| 依赖 | 当前版本 | 评估 | 风险 |
|------|----------|------|------|
| Spring Boot | 4.1.0 | ✅ 最新线，OSS 支持至 ~2027-06 | 无 |
| Java | 21 | ✅ LTS，长期支持 | 无 |
| MyBatis-Plus | 3.5.17 | ✅ Boot 4 专用 starter，已适配 Spring 7 | 无 |
| jjwt | 0.13.0 | ✅ 最新 | 无 |
| springdoc | 3.1.0 | ✅ Spring 7 线 | 无 |
| ArchUnit | 1.4.2 | ✅ 适配 JUnit 5.12+ | 无 |
| Flyway（运行时） | 12.4.0（Boot BOM 管理） | ⚠️ 生产 docker 镜像锁 9.22.3 | **版本偏斜** |
| EasyExcel | 3.3.4 | 可升级到 4.x | Low |
| aliyun-sdk-oss | 3.17.4 | 可升级 | Low |
| aliyun dysmsapi | 3.1.0 | 正常 | 无 |
| Lombok | 1.18.46 | ✅ 支持 JDK 25 | 无 |
| Mockito | 5.17.0 | ✅ 已配置 javaagent 预挂载 | 无 |

### 前端依赖（package.json）

| 依赖 | 当前版本 | 评估 | 风险 |
|------|----------|------|------|
| Vue | 3.5.34 | ✅ 最新 3.x | 无 |
| Element Plus | 2.14.5 | ✅ 最新 2.x | 无 |
| Pinia | 3.0.4 | ✅ 最新 | 无 |
| Vue Router | 4.6.4 | ✅ 最新 | 无 |
| Axios | 1.17.0 | ✅ 最新 | 无 |
| Vite | 8.0.12 | ✅ 最新（rolldown） | 无 |
| Vitest | 4.1.10 | ✅ 最新 | 无 |
| @playwright/test | 1.63.0 | ⚠️ 已安装但未使用 | 闲置依赖 |
| jsdom | 30.0.1 | ✅ 最新 | 无 |
| TypeScript | 5.9.3 | ✅ 最新 | 无 |
| @vueuse/core | 14.4.0 | ✅ 最新 | 无 |

### 技术债清单（按优先级排序）

| 优先级 | 技术债 | 影响 | 建议 |
|--------|--------|------|------|
| P0 | 无覆盖率度量（无 JaCoCo / vitest coverage） | 无法量化测试质量，无法设门禁 | 添加 JaCoCo 插件 + vitest coverage 配置 |
| P1 | auth/score 模块零测试覆盖 | 登录认证和积分计算无回归保障 | 补充单元测试 |
| P1 | N+1 查询（6 处） | 数据量增长后接口响应变慢 | 批量查询替换循环单查 |
| P1 | 全表扫描统计查询 | 大表统计接口可能 OOM | SQL GROUP BY 聚合替代内存计算 |
| P2 | 无集成测试 | Mapper SQL、事务、缓存链路未验证 | 补充关键路径 @SpringBootTest |
| P2 | Flyway 版本偏斜 | 跨大版本迁移风险 | 同步生产镜像版本 |
| P2 | Playwright 闲置 | 增加 node_modules 体积和维护负担 | 初始化 E2E 或移除依赖 |
| P3 | EasyExcel 版本旧 | 功能稳定，无安全漏洞 | 下次大版本升级时评估 |
| P3 | 前端大表格无虚拟滚动 | 当前数据量可接受 | 客户数据量过万后评估 |

---

## 技能覆盖清单

### 已调用技能

| 技能 | 调用方式 | 产出 |
|------|----------|------|
| 依赖审计 | 运行 `dep_scanner.py` 扫描项目全部依赖 | 输出 JSON 依赖清单，确认无已知 CVE 匹配 |
| doubao-coding-develop-unit-tests | 读取 SKILL.md，按"契约→反例→证据"方法论评估现有测试结构 | 评估测试覆盖、Mock 使用合理性、测试金字塔健康度 |
| doubao-coding-optimize-performance | 读取 SKILL.md，按"无测量不结论"原则做静态性能风险识别 | 识别 N+1 查询、全表扫描、前端渲染风险（标记为 EVIDENCE_REQUIRED，需 profile 验证） |
| tdd开发 / 测试策略 | 按测试金字塔框架评估后端/前端测试分布 | 发现 auth/score 零覆盖、无集成测试层 |
| 性能剖析 | 静态代码分析定位循环内 DB 调用 | 6 处 N+1、4 处全表扫描 |
| 前端选库 | 评估 UI 库/状态管理/HTTP 客户端选型 | Vue 3 + Element Plus + Pinia + Axios 选型合理，按需加载已配置 |
| 技术栈评估 | 评估 Spring Boot 4.1 + Java 21 + MyBatis-Plus 3.5.17 版本兼容性 | 选型前沿且兼容，Flyway 版本偏斜需治理 |
| 技术债追踪 | 按优先级整理技术债清单 | P0-P3 共 9 项 |

### 未找到/不适用而跳过的技能

| 技能 | 原因 |
|------|------|
| playwright专业 / playwright修复 / playwright初始化 / playwright报告 / playwright用例生成 / playwright覆盖率 / playwright迁移 | Playwright 依赖已安装但无配置文件、无 E2E 目录、无 E2E 测试代码。当前为只读审查，不初始化新项目。仅在问题清单中标注"闲置依赖" |
| 测试用例追踪 | 项目无 TestRail 或类似用例管理系统集成 |
| 高级qa | 无 QA 流程文档或质量门禁配置可分析 |
| testrail集成 | 项目未使用 TestRail |
| 浏览器自动化 | 审查为只读静态分析，无需浏览器操作 |
| 性能测试 | 无 JMH/Gatling/k6 等性能压测脚本，无法执行基准测试。按"无测量不结论"原则，仅输出静态风险识别 |
| 网页测试 | 无 Playwright E2E 测试代码可审查 |
