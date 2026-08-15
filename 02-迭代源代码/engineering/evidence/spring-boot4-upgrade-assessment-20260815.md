# Spring Boot 4 升级可行性评估(AIGICRM,2026-08-15)

> 触发:应用启动提示「OSS support for Spring Boot 3.5.x ended on 2026-06-30」。
> 本文为纯调研结论 + 升级方案,不动代码;版本信息以 2026-08-15 Maven Central 权威元数据为准。

## 一、问题定性

- 当前 Spring Boot **3.5.16**:免费补丁支持已于 **2026-06-30 结束**(已过 6 周)。
- 后果:**后续任何 CVE 无官方免费修复**;商业订阅(Tanzu)才维护到 2032。
- 已知附带现象:升级后该提示可能因 banner 缓存仍出现一次(社区已报告,见
  [StackOverflow #79981421](https://stackoverflow.com/questions/79981421/oss-support-for-spring-boot-3-5-x-ended-on-2026-06-30-message-doesnt-go-away))。

## 二、目标版本选择

| 版本线 | OSS 支持期 | 结论 |
|---|---|---|
| Boot 3.5.x | 已结束 | ❌ |
| Boot 4.0.x | ~2026-12 | 可用但窗口短 |
| **Boot 4.1.x**(2026-05 发布) | **~2027-06** | ✅ **推荐目标**(当前最长免费窗口) |

## 三、依赖兼容矩阵(已查 Maven Central 权威元数据)

| 依赖 | 当前 | Boot 4 方案 | 动作 |
|---|---|---|---|
| mybatis-plus | boot3-starter 3.5.12 | **mybatis-plus-spring-boot4-starter 3.5.17**(专用 starter 已存在) | 换 artifact + 升版本 |
| mybatis-plus-jsqlparser | 3.5.12 | 3.5.17 | 同步升 |
| springdoc | 2.8.17(覆盖 knife4j 内嵌) | **3.1.0**(Spring 7 线) | 升 major,API 小改 |
| **knife4j** | 4.5.0(已停更) | **必须移除**(内嵌 springdoc 2.3.0 与 Spring 7 不兼容) | 删依赖,UI 用 springdoc 自带 swagger-ui |
| jjwt | 0.12.5 | 0.13.0(可选) | 建议顺手升 |
| easyexcel | 3.3.4 | 4.0.3(可选) | 可选 |
| archunit | 1.4.2 | Boot 4 管理线核对 | 核对 |
| flyway | 11.7.2 | Boot 4.1 BOM 管理版本核对 | 插件/core 同步 |
| lombok | 1.18.46 | 不变 | — |
| aliyun-oss/dysmsapi | 3.17.4/3.1.0 | 不变(纯 HTTP 客户端) | — |

## 四、代码迁移面(已扫描项目实际使用)

1. **SecurityConfig**(1 个文件):Spring Security 7 的 `SecurityFilterChain` lambda DSL 基本延续,
   但若干废弃项移除(如 `authorizeRequests`、`and()` 等旧式链——本项目已是 lambda 风格,
   预计改动小;`requestMatchers` 白名单中的 `/doc.html/**` 要换成 `/swagger-ui/**`)。
2. **knife4j 移除**:pom 删依赖 + SecurityConfig 放行路径调整;前端/文档入口
   `/doc.html` → `/swagger-ui/index.html`(需查前端是否有跳转链接)。
3. **测试**:项目未用 `@MockBean/@SpyBean`(已扫 0 处)——Spring 7 的测试迁移负担≈0。
4. **Jackson/校验/缓存**:无自定义深层扩展迹象,预期兼容。
5. **数据库迁移**:Flyway 11.x → Boot 4 BOM 管理版本,校验和兼容(同大版本内);
   V16 未部署的问题**先部署再升级**,升级前迁移链必须干净。

## 五、工作量与风险

| 步骤 | 预估 |
|---|---|
| 1. 分支隔离 + 依赖矩阵替换(pom) | 0.5 天 |
| 2. 编译修错(安全配置/springdoc API/废弃项) | 1-2 天 |
| 3. 412 单测全绿 + 前端构建 + 三脚本核对 | 0.5 天 |
| 4. 起服冒烟(MySQL/Redis)+ 接口文档 UI 复核 | 0.5 天 |
| 合计 | **约 3-4 个工作日**(串行,由我小步执行+逐步验证) |

风险:Spring Security 7 行为差异(权限校验/会话)、MyBatis-Plus 3.5.12→3.5.17 行为变化、
springdoc 3.x 注解 API 变更。缓解:git 分支隔离、逐依赖提交、412 回归 + verify.sh + 冒烟。

## 六、决策选项

- **A(推荐)**:升 Boot 4.1.x,按上表执行,我分小步做并逐步验证;
- **B**:暂不升级,接受"无免费补丁"状态,登记残余风险(CVE 出现时人工评估);
- **C**:商业订阅(不建议,内部系统)。

## 七、前置条件(升级前必须完成)

1. **V16 迁移先部署**(本地 MySQL 3308 起库 → `scripts/deploy-db.sh`);
2. 当前未提交改动(测试/pom 修复)先按规范提交,保证升级分支从干净基线出发。
