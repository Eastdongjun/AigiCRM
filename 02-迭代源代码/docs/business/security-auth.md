# 认证与会话

> 来源锚点:`AuthService`、`SecurityConfig`、`JwtAuthFilter`、`SecurityAuditService`。核实日期 2026-09-24。

## 1. 登录与限流

- 登录失败限流两级:**同一 IP+用户名 5 次**、**同一 IP(跨用户名)20 次**,锁 **15 分钟**(Redis 计数,TTL 过期自动解锁)。
- 错误提示统一"账号或密码错误"(不区分账号不存在/密码错误);账号存在性与停用状态只进安全审计日志。
- 成功登录只清**当前用户名**维度失败计数;IP 维度计数不被成功登录清零(防穿插正确登录洗掉爆破计数),靠 TTL 自然过期。
- 失败达阈值触发一次告警事件(loginFailBurst/loginFailIpBurst);所有登录成败均留结构化审计(SECURITY_AUDIT)。

## 2. JWT 与会话

- 无状态 JWT,携带 jti;**注销即拉黑当前 jti**(被盗 token 复用防护)。
- **多集团会话**:登录时推导集团集合(推导链见 [visibility.md](visibility.md) §2),默认当前集团=最小 id;`switch-group` 校验归属后**重签 token**。
- 权限缓存带 perm_version,角色勾选变更后即时生效(版本号+1 自愈)。

## 3. 密码策略

- admin 初口令首登**强制改密**(pwd_reset_required),改后旧口令立即失效——冒烟工具里的旧口令会 401,属预期。
- 忘记密码三步:lookup(查账号)→ send-code(短信验证码)→ reset;lookup 与发码均按 IP 60s 限流,匿名链路探测留痕+阈值告警(账号枚举唯一可见痕迹)。

## 4. 端点暴露面(与探活)

- 匿名放行仅:`/api/auth/login`、`/api/auth/captcha`、`/api/auth/forgot-password/**`、`/api/system/configs/public`、`/api/system/configs/logo`、`/api/file/download/public/**`、`/actuator/health`。
- **公网 nginx 把 /actuator/health 回退到 SPA 首页**(200 的 index.html),不可用作探活;部署脚本探活用 `/api/system/configs/public`(判据=HTTP 200 且 body 含 "code":200,防回退假阳性)。
- metrics/prometheus 需认证;探活类脚本禁止真实登录(失败登录会污染审计与限流计数)。

## 5. 越权防护要点

- @PreAuthorize(权限码)+ 数据范围 + 对象级守卫三层;服务层拒绝统一 400"无权"语义。403 越权拒绝**已汇入结构化安全审计**(BS-A09 2026-09-19:SecurityConfig.accessDeniedHandler 统一出口,留 uri/用户/IP 痕迹+IP 阈值告警)——2026-09-24 更正本文旧说法"不落日志"(该说法源于 BS-A09 之前的状态;服务层 400"无权"业务异常确实仅 WARN 日志,排查仍靠探测,两者勿混)。
- 用户管理三写接口(update/delete/reset-password)守卫:`admin ∨ orgAdmin 且可管理该用户`(2026-09-22 P0 修复:原短路写法曾允许行政改 admin)。
