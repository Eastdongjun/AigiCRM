# 分片B 审查发现：API设计与安全攻防

> 审查日期：2026-09-16 | 审查范围：API设计测试 + 安全攻防合规 | 审查方式：只读静态代码审查
> 项目：AIGICRM (Spring Boot 4.1.0 / Java 21 / Vue 3 + Vite)

---

## 问题清单

| 序号 | 严重级别 | 文件路径 | 问题描述 | 对应技能 | 建议方向 |
|------|----------|----------|----------|----------|----------|
| 1 | **High** | `aigicrm-backend/src/main/resources/application.yml:6` | MySQL 连接 URL 配置 `useSSL=false&allowPublicKeyRetrieval=true`，数据库通信全程未加密。生产环境虽在 Docker 内网，但跨主机部署或云数据库时凭据和查询明文传输。 | owasp安全 / 云安全 | 生产环境启用 `useSSL=true`，配合 MySQL 服务端 SSL 证书；`allowPublicKeyRetrieval` 应仅在初始化时使用。 |
| 2 | **High** | `aigicrm-ui/src/utils/token.js:5` | JWT Token 存储在 `localStorage`，任何 XSS 漏洞即可窃取 Token。当前虽有 nginx CSP 缓解，但 CSP 中含 `unsafe-eval`，且应用大量动态渲染场景，XSS 面仍存。 | owasp安全 / 认证与授权 | 评估迁移到 HttpOnly + Secure + SameSite Cookie 存储 Token，或至少在 localStorage 基础上增加 CSP 严格模式。 |
| 3 | **High** | `docker-compose.prod.yml:33-38` | Redis 服务无密码（无 `requirepass` 配置），且与后端同一 Docker 网络内。任何同网络容器可无认证访问 Redis，读取/篡改 JWT 黑名单、权限缓存、登录限流计数器。 | 云安全 / 密钥保险库 | 为 Redis 设置 `requirepass`，在 application.yml 中通过 `${REDIS_PASSWORD}` 注入；生产 compose 中显式配置 `command: redis-server --requirepass ${REDIS_PASSWORD}`。 |
| 4 | **High** | `docker-compose.prod.yml:60` | 生产 CORS 配置 `CORS_ORIGINS: https://www.aigicrm.com,http://localhost:5173,http://localhost:3000` 包含 localhost 开发地址。配合 `allowCredentials=true`，存在 DNS 重绑定/本地攻击面。 | owasp安全 / 云安全 | 生产环境移除 localhost 来源，仅保留 `https://www.aigicrm.com`；开发来源通过单独 profile 注入。 |
| 5 | **High** | `aigicrm-backend/src/main/java/com/aigicrm/security/JwtTokenProvider.java:133-135` | `isBlacklisted()` 方法在 DB 查询异常时返回 `false`（未拉黑），即已注销/被盗 Token 在 DB 故障期间仍然有效。注释声称"fail-closed"，实际行为是 fail-open。 | owasp安全 / 认证与授权 | DB 查询失败时应返回 `true`（视为已拉黑）或抛出异常拒绝请求，而非放行。当前 Redis→DB 两级兜底在 DB 故障窗口内撤销失效。 |
| 6 | **High** | `aigicrm-backend/src/main/java/com/aigicrm/common/engine/PermissionRuleEngine.java:104-107,227-229` | 权限规则引擎通过字符串拼接构造 SQL WHERE 子句（`col + " = " + quoteValue(value)`），虽然 `quoteValue()` 转义了反斜杠和单引号、`escapeColumn()` 校验列名白名单，但本质不是参数化查询。规则值中的 `${user.username}` 等变量来自数据库配置，若管理员被诱导配置恶意规则值，转义可被绕过。 | owasp安全 / sql数据库助手 | 长期应迁移为 MyBatis 参数化查询或 JSqlParser 安全构造；短期确保规则配置入口有严格的管理员权限管控和审计。 |
| 7 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/dto/ForgotPasswordResetRequest.java:14` | 新密码仅要求 `@Size(min=6, max=30)`，无复杂度要求（无大小写/数字/特殊字符要求）。OWASP ASVS L1 建议至少 12 字符。 | owasp安全 / 认证与授权 | 增加 `@Pattern` 复杂度校验（如至少包含字母+数字），或接入弱密码字典检查。 |
| 8 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/config/SecurityConfig.java:37` | CSRF 防护完全禁用。虽然纯 JWT Bearer Token 不依赖 Cookie，CSRF 风险较低，但 `/api/file/download/public/**` 等公开端点 + 未来若引入 Cookie 会话则无防护。 | owasp安全 | 确认当前无 Cookie 认证场景后可接受；建议在 SecurityConfig 中注释说明禁用原因，或为公开端点增加 CSRF Token。 |
| 9 | **Medium** | 全局 | 仅登录、忘记密码接口有速率限制（Redis 计数），其余所有 API（客户搜索、导出、文件下载、数据统计）无速率限制。可被用于数据爬取或 DoS。 | owasp安全 / api设计审查 | 引入全局速率限制过滤器（Bucket4j 或 Spring Cloud Gateway），对导出/搜索等重操作接口单独配置更严格限制。 |
| 10 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/common/Result.java` + `GlobalExceptionHandler.java` | 错误响应格式为自定义 `{code, message, data}`，不符合 RFC 7807 Problem Details 规范；`code` 字段混用 HTTP 状态码和业务错误码，无 traceId/requestId 用于日志关联。 | api接口设计 / api设计审查 | 统一错误格式为 Problem Details（type/title/status/detail/traceId），或在现有 Result 基础上增加 traceId 字段。 |
| 11 | **Medium** | 全局 API | 无 API 版本管理策略。所有端点使用 `/api/` 前缀（如 `/api/customer/list`），无 `/api/v1/` 版本路径。破坏性变更无版本隔离机制。 | api接口设计 / api设计审查 | 在 URL 路径中引入版本前缀（`/api/v1/`），制定废弃策略（Deprecation/Sunset 头）。 |
| 12 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java:329` | 短信发送失败时将阿里云 SMS 网关原始错误信息直接返回给客户端：`"短信发送失败: " + resp.getBody().getMessage()`，可能暴露内部基础设施信息（如账号余额不足、模板未审核等）。 | owasp安全 / 合规与隐私 | 对外返回通用提示"短信发送失败，请稍后重试"，详细错误仅记录在服务端日志中。 |
| 13 | **Medium** | `aigicrm-backend/src/main/resources/application.yml:28-29` + nginx 配置 | 文件上传限制为 500MB（Spring + nginx 双重配置），偏大。单个大文件上传即可占用大量磁盘和带宽，存在 DoS 风险。 | owasp安全 | 根据业务实际需求调整为 50-100MB，配合文件类型白名单校验。 |
| 14 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/config/CorsConfig.java:31-34` | CORS 配置 `setAllowCredentials(true)` + `addAllowedHeader("*")` + `addAllowedMethod("*")`。虽然 Origin 从环境变量注入，但允许所有方法和头增加了攻击面。 | 云安全 / owasp安全 | 收紧 `addAllowedMethod` 为实际需要的方法集（GET/POST/PUT/DELETE/PATCH），`addAllowedHeader` 为 `Authorization, Content-Type`。 |
| 15 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java:77-80` | `switchGroup` 端点使用 `Map<String, Object>` 接收参数而非 DTO，且 `Long.parseLong(String.valueOf(gid))` 未捕获 `NumberFormatException`，恶意输入可导致 500 错误。 | api接口设计 / 严格api | 定义 `SwitchGroupRequest` DTO，使用 `@NotNull @Min(1)` 校验 groupId。 |
| 16 | **Medium** | `aigicrm-backend/src/main/resources/application.yml:38` | 基础 profile 中 MyBatis `log-impl: StdOutImpl` 将所有 SQL 打印到标准输出，包括查询参数。生产 profile 虽已切换为 Slf4jImpl，但 dev/test 环境 SQL 日志可能包含敏感数据（手机号、身份证等）。 | owasp安全 / 日志安全 | 确保非生产环境 SQL 日志不输出敏感列，或使用脱敏日志 appender。 |
| 17 | **Medium** | `aigicrm-backend/src/main/resources/application.yml:24` | Redis 密码默认为空字符串 `${REDIS_PASSWORD:}`，未设置环境变量时 Redis 以无密码模式启动。 | 密钥保险库 / 云安全 | 改为 `${REDIS_PASSWORD:?REDIS_PASSWORD 未设置}` 强制注入，或至少在非开发 profile 中要求密码。 |
| 18 | **Medium** | `aigicrm-backend/src/main/java/com/aigicrm/config/SecurityConfig.java` | 后端未配置安全响应头（HSTS/X-Frame-Options/X-Content-Type-Options/CSP）。当前安全头仅由 nginx-ssl.conf 提供，若直接访问后端 8080 端口（虽然绑定 127.0.0.1），则无任何安全头。 | owasp安全 / 安全设计 | 在 Spring Security 中增加 `headers()` 配置，或确认后端端口不直接暴露（当前 docker-compose 已绑定 127.0.0.1，风险较低）。 |
| 19 | **Low** | `aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java:60-65` | 文件上传端点在 Controller 层未校验文件扩展名和 MIME 类型，仅依赖 Service 层。需确认 FileService.upload() 中有类型白名单校验。 | owasp安全 | 在 Controller 层增加文件类型白名单校验（如仅允许图片/PDF/Office 文档）。 |
| 20 | **Low** | `aigicrm-backend/src/main/resources/application.yml:96` | 基础 profile 日志级别 `com.aigicrm: debug`，debug 级别日志可能打印敏感调试信息。生产 profile 已覆盖为 info，但测试环境仍为 debug。 | owasp安全 | 测试 profile 也应设为 info 级别。 |
| 21 | **Low** | `aigicrm-backend/src/main/java/com/aigicrm/common/Result.java` | 响应体无 `traceId` 字段，生产问题排查无法从响应反查服务端日志。 | api接口设计 / 可观测性 | 在 Result 中增加可选 traceId 字段，由请求拦截器生成并传递。 |
| 22 | **Low** | `aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java:315` | 密码重置验证码使用 `ThreadLocalRandom` 生成 6 位数字，非密码学安全随机数生成器。 | owasp安全 | 使用 `SecureRandom` 替代 `ThreadLocalRandom` 生成验证码。 |
| 23 | **Low** | `aigicrm-ui/src/utils/request.js:8` | Axios 超时固定为 30 秒，文件上传/导出等长耗时操作可能超时中断。 | api接口设计 | 对上传/导出等长耗时接口单独配置更长超时。 |
| 24 | **Low** | `aigicrm-backend/src/main/java/com/aigicrm/config/SecurityConfig.java:40-42` | 公开端点白名单包含 `/api/system/configs/public` 和 `/api/system/configs/logo`，需确认这些端点不返回敏感配置信息。 | owasp安全 | 确认 public config 端点仅返回非敏感的 UI 配置（如 Logo URL、系统名称）。 |

---

## API 设计整体评估

### 一致性与规范

| 维度 | 评估 | 说明 |
|------|------|------|
| RESTful 资源命名 | **良好** | 使用复数名词（`/api/customer/list`, `/api/file/folders`），但部分端点混用了动词（如 `/list`, `/add`, `/export`），不完全符合纯 RESTful 规范 |
| HTTP 方法使用 | **良好** | GET 查询/POST 创建/PUT 更新/DELETE 删除使用正确，导出用 GET + 下载 token |
| 状态码规范 | **中等** | GlobalExceptionHandler 已将业务码映射到 HTTP 状态码（401/403/404/429），但 `Result.code` 字段仍混用 HTTP 状态码和业务码，未使用标准 RFC 7807 |
| API 版本管理 | **缺失** | 无版本前缀，所有端点直接挂载 `/api/`，无 `/api/v1/` 版本策略 |
| 分页一致性 | **良好** | 使用 MyBatis-Plus IPage 分页，参数 `current`/`size` 一致，返回 `IPage` 结构 |
| 错误处理统一 | **中等** | GlobalExceptionHandler 覆盖了大部分异常类型，错误信息不泄露堆栈，但格式非标准 Problem Details |
| DTO 校验 | **良好** | 登录、密码重置等核心 DTO 使用 `@Valid` + `@NotBlank`/`@Size`，但部分端点（switchGroup、文件上传）缺少 DTO 校验 |
| 权限标注覆盖 | **优秀** | 24 个控制器中 23 个使用了 `@PreAuthorize`，覆盖了功能码级权限控制；AuthController 作为认证入口无 @PreAuthorize 属合理 |
| 对象级授权 | **优秀** | CustomerAccessService 提供了完整的对象级访问控制（ID 越权防护），所有客户相关操作均经过 `assertCanView/assertCanEdit/assertCanDelete` 校验 |

### API 设计优点

1. **下载专用 Token 设计**：`scope=download` 的短时 Token（1h）替代主 JWT 进 URL，有效防止 Token 泄漏到日志/浏览器历史
2. **登录防枚举**：统一错误提示"账号或密码错误"，忘记密码查询返回中性响应
3. **多维度限流**：登录有 IP+用户名双维度限流，忘记密码有 IP 级限流防短信轰炸
4. **对象级权限校验**：CustomerAccessService 解决了列表过滤但详情/子资源无归属校验的经典 IDOR 问题
5. **审计日志**：LogAspect 自动记录所有写操作（含失败），模块/动作/详情完整

---

## 安全风险总览（OWASP Top 10:2025 分类）

| OWASP 风险 | 风险等级 | 关键发现 |
|------------|----------|----------|
| **A01 失效的访问控制** | 🟡 中 | 对象级授权（CustomerAccessService）实现良好；但 `isBlacklisted()` DB 故障时 fail-open（#5），部分端点无速率限制（#9） |
| **A02 加密机制失效** | 🔴 高 | MySQL `useSSL=false`（#1）；JWT 存 localStorage（#2）；Redis 无密码（#3）；`ThreadLocalRandom` 生成验证码（#22） |
| **A03 注入** | 🟡 中 | MyBatis-Plus 主流路径使用 `#{}` 参数化查询（安全）；PermissionRuleEngine 字符串拼接 SQL WHERE 子句有转义但非参数化（#6） |
| **A04 不安全设计** | 🟡 中 | 无 API 版本管理（#11）；无全局速率限制（#9）；密码复杂度要求不足（#7） |
| **A05 安全配置错误** | 🔴 高 | 生产 CORS 含 localhost（#4）；Redis 无密码（#3）；MySQL SSL 关闭（#1）；debug 日志在基础 profile（#16/#20） |
| **A06 供应链失败** | 🟢 低 | Spring Boot 4.1.0 / jjwt 0.13.0 / MyBatis-Plus 3.5.17 均为较新版本；knife4j 已移除 |
| **A07 身份认证失败** | 🟡 中 | JWT 实现较完善（黑名单/改密撤销/首登强制改密）；但密码策略弱（#7）；isBlacklisted fail-open（#5） |
| **A08 数据完整性失败** | 🟢 低 | Redis 反序列化已收紧为白名单（`WhitelistedJsonRedisSerializer`），防 gadget 反序列化攻击 |
| **A09 日志与监控失败** | 🟡 中 | 操作审计日志完善（LogAspect），但无安全事件独立告警；SQL 日志在 dev 环境全量输出 |
| **A10 异常处理不当** | 🟢 低 | GlobalExceptionHandler 不泄露堆栈；但 SMS 网关错误信息泄漏给客户端（#12）；switchGroup 未捕获 NumberFormatException（#15） |

### 最高风险项（需优先处理）

1. **Redis 无密码**（#3）— 同网络任意容器可读写 JWT 黑名单和权限缓存，可篡改权限或伪造注销状态
2. **生产 CORS 含 localhost**（#4）— 配合 allowCredentials=true 扩大了 CSRF/重绑定攻击面
3. **MySQL SSL 关闭**（#1）— 数据库凭据明文传输
4. **JWT 存 localStorage**（#2）— XSS 一旦成功即可窃取会话 Token
5. **isBlacklisted fail-open**（#5）— DB 故障期间已注销 Token 仍然有效

---

## 技能覆盖清单

### 已调用技能（读取 SKILL.md 并按方法论执行审查）

| 技能名称 | 技能路径 | 审查应用方式 |
|----------|----------|-------------|
| API设计审查 | `.user_skills/api设计审查/SKILL.md` | 按 REST 规范 checklist 逐项审查 Controller 命名/HTTP方法/状态码/分页/错误格式 |
| API接口设计 | `.user_skills/api接口设计/SKILL.md` | 按 RFC 7807 错误格式、版本管理、分页模式、Idempotency 等标准评估 |
| 严格API | `.user_skills/严格api/SKILL.md` | 检查 DTO 完整性、API 误用（Map 替代 DTO）、异常处理路径 |
| OWASP安全 | `.user_skills/owasp安全/SKILL.md` | 按 OWASP Top 10:2025 + ASVS 5.0 checklist 逐项审查代码安全 |
| 云安全 | `.user_skills/云安全/SKILL.md` | 审查 Docker 网络暴露面、Redis 密码、CORS 配置、端口绑定 |
| AI安全 | `.user_skills/ai安全/SKILL.md` | 审查 Redis 反序列化白名单（防 gadget 攻击）；项目无 LLM 集成，AI 专项风险不适用 |

### 未找到/不适用而跳过的技能

| 技能名称 | 跳过原因 |
|----------|----------|
| api测试套件构建 | 只读静态审查，未运行测试套件；前端有 vitest 测试文件但不在本审查范围执行 |
| 集成设计 | 项目为单体 Spring Boot 应用，无跨服务集成设计文档可审 |
| 认证与授权 | 认证授权专项已通过 OWASP checklist + SecurityConfig/JwtTokenProvider/JwtAuthFilter 深度审查覆盖 |
| 事件驱动架构 | 项目无消息队列/事件总线，为同步 CRUD 架构 |
| 渗透测试 / 红队 / 安全测试 / 默认漏洞检测 | 任务明确禁止攻击性操作（实际渗透/注入攻击），仅做静态代码审查 |
| 安全设计 / security-threat-review | 无威胁建模文档可审；安全设计已通过代码级审查覆盖 |
| 威胁检测 / 高级安全 / 高级安全运营 | 无运行时遥测/监控数据可分析 |
| 合规与隐私 | 已在审查中覆盖：数据脱敏（maskPhone/maskEmail）、用户枚举防护、审计日志；无隐私政策/合规文档可审 |
| 密钥保险库 / 环境密钥管理 | 已在审查中覆盖：密钥全部通过环境变量注入（无硬编码）、Docker compose 使用 `:?` 强制注入 |
| 可信执行治理 / 危险边缘识别 | 项目不涉及 TEE/机密计算等场景 |

---

## 审查方法说明

- 所有关键文件读取前均通过 `sonar analyze secrets` 扫描密钥，全部通过（未发现硬编码密钥）
- 审查覆盖：7 个 Security 包文件、8 个 Config 包文件、Auth 模块（Controller+Service+DTO）、Customer 模块 Controller、File 模块 Controller、PermissionRuleEngine、GlobalExceptionHandler、RedisConfig、ClientIpUtils、LogAspect
- 前端审查：token.js、request.js、store/user.js
- 部署配置审查：Dockerfile、nginx-ssl.conf、docker-compose.prod.yml、application.yml、application-prod.yml
- 依赖审查：pom.xml（Spring Boot 4.1.0、jjwt 0.13.0、MyBatis-Plus 3.5.17）、package.json（axios 1.17.0、Vue 3.5.34）
- 未执行任何代码修改、文件创建（除本报告）、配置变更或攻击性操作
