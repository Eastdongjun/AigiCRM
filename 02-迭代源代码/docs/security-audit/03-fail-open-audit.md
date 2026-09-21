# Fail-open 审计(审计产出 4/4,2026-09-21)

## PR-01 fail-closed 声明独立核验
| 声明 | 结论 | 证据 |
|---|---|---|
| 改密撤销 Redis 异常回查 DB | **属实** | JwtTokenProvider.java:229-244(Redis get 异常→sys_user.pwd_changed_at,DB 也失败→return true 拒绝);isBlacklisted 同构:118-139(:137 DB 失败→true) → **16项#8 JWT blacklist: fail-closed 已验证** |
| role_perm_scope 读取异常 503 | **属实+1残留** | RolePermScopeService.java:70-75/148-152/293-299 三处 503;残留: anchorOf:198-200 静默回退 OWNER(P3) → **#7: fail-closed 基本属实** |
| 权限范围解析异常 503 | **语义属实,字面不符** | OrgScopeResolver 无 try/catch,异常穿透→DataAccessException→500(拒绝);BusinessException(503) 经 GlobalExceptionHandler:53-61 实际 HTTP 400 仅 body code=503 |

## fail-closed 已验证点(节选)
JwtAuthFilter:98-103(链路异常→401 不建会话)/37-50(download-scope 滥用→拒)/67-75(pwd_changed_at 每请求 DB 级撤销)/83-92(集团归属实时校验);UserDetailsServiceImpl:61-63(停用→401)/67-69(缓存须 permVersion 一致)/237-240(组织管理员查询失败不提升);PermissionCacheService:49-53(get 异常→回源查库);GroupScopeUtil:31-37/66-68/95-107(**无集团→空集/null/false,不回退主集团1** → **16项#2: 已修复**);MybatisPlusConfig:63-65/133-140;CustomerAccessService 全类无 catch(null→false);SeaConfigService/FolderPermissionService 默认 false;DataScopeGuard:25(ownerId null→403)

## Fail-open 风险清单
| # | 等级 | 位置 | 异常场景 | 异常后行为 |
|---|---|---|---|---|
| F1 | **P2** | PermissionRuleEngine.java:160-164 catch(Exception ignored) | 规则 JSON 脏数据 | field_visible 规则被跳过→**敏感字段(营收等)原样返回,脱敏静默失效且无日志** |
| F2 | **P2** | 同文件:174-180 | 同上 | action_control 规则跳过→操作限制不生效 |
| F3 | P3 | RolePermScopeService.java:198-200 anchorOf | DB 异常 | 回退 OWNER 锚点而非 503(ORG 配置故障期被换口径,可能更宽) |
| F4 | P3 | OrgScopeResolver:54-55 + SysConfigService:33-36 | 配置行缺失/空白 | 两个放宽性开关(上级可见链/负责人可见组织子树)**默认开** |
| F5 | P3 | JwtTokenProvider:248-254+221 / JwtAuthFilter:71 | 无 iat claim 的签名 token | 逃逸改密撤销(需持密钥,纵深收紧项);jti null 同理(:119) |
| F6 | P3 | PermissionCacheService:61-137 put/evict 失败仅 warn | Redis 写失败+写点漏 bump | 旧权限最长 TTL(10min)残留 |
| F7 | P3 | SecurityAuditService:128-191 | Redis/DB 故障 | 审计事件丢失(设计如此,故障期越权探测无痕) |
| F8 | P3观察 | CustomerAccessService:189/206-211 | admin 空集=全部约定 | 非 admin 分支 resolve=null(all)时只回本人+共享(过严);空集语义脆弱 |
| F9 | P3观察 | JwtAuthFilter:122-128 startsWith 前缀 | /change-password-xxx 之类路由 | 命中改密白名单(需存在该路由) |

## permitAll 清单评估(SecurityConfig:48-55)
login(限流429)/captcha(死配置,无端点)/forgot-password/**(中性化+ANON_PROBE 审计+限流,**#9 找回密码枚举:缓解到位,建议持续监控**)/configs/public(白名单键)/configs/logo(防穿越)/file/download/public/**(normalize+首段=public+SVG attachment;残余:可枚举 key,建议不可猜测 key)。**Swagger 条件放行依赖 profile 含"prod"(:42)——默认 profile 启动即匿名全量暴露,建议显式白名单 profile**。
