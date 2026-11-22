# C7 环境漂移：生产 nginx vs 仓库配置（2026-11-18，只读）

> 生产 `ycmn-frontend`（镜像 `ycmn-frontend:20261117-0122`）容器内 `/etc/nginx/conf.d/default.conf`（226 行，md5 `4eacade4a6ec917f81187f594f38e948`，文件时间 Nov 16 17:23）vs 仓库 `02-迭代源代码/nginx/nginx-ssl.conf`（248 行）。去注释/空行后 diff 28 行。
> 生产零写入；本文件只做差异与影响判定，配置更新须用户窗口授权。

## 一、实质差异（2 项缺口 + 2 项等价）

### 缺口 A（P1，安全）：referer 脱敏 map 缺失

- 仓库版（17-22 行）定义 `map $http_referer $referer_redacted`：把 referer 中的 `token=`/`jwt=`/`auth=` 查询串替换为 `***`，日志格式用 `$referer_redacted`。
- **生产版无此 map，日志格式直接用 `$http_referer`** → 任何带 token 查询串的请求（尤其 MP picker 的 `/mp-file-picker?token=<主JWT>`，见 MP-TOKEN-001）**会把主 JWT 写进 nginx access.log**。
- 影响：能读容器/NAS 日志者可直接拿到有效主 JWT（TTL 内）。与 MP-TOKEN-001 叠加放大。
- 修复：把仓库 map + 日志格式下发到生产（配置变更，需窗口）。

### 缺口 B（P2，功能）：`location /mp-file-picker` 缺失

- 仓库版有该 location：CSP `script-src 'self' 'unsafe-eval' https://res.wx.qq.com`（微信 JSSDK 定向放行）。
- **生产走 `location /` 的 server 级 CSP**（`script-src 'self' 'unsafe-eval'`），`res.wx.qq.com` 被拦。
- 代码证据：`aigicrm-ui/src/views/mp/FilePicker.vue:78`、`FabushiReader.vue:87` 动态加载 `https://res.wx.qq.com/open/js/jweixin-1.3.2.js`；FilePicker 有降级（`s.onerror → wxReady=false`，上传后不自动 `wx.miniProgram.navigateBack()`，需手动左滑返回）。
- 影响：MP 上传回跳降级、法布施阅读器 JSSDK 能力降级；上传/阅读主体功能不依赖 JSSDK，**非阻断**。
- 修复：同 A，下发仓库配置。

### 等价项

- `server ycmn-backend:8080`（生产）vs `server ${BACKEND_HOST}:8080`（仓库模板变量）——等价。
- `index.html` 指令顺序——等价。

## 二、其余核对（一致）

- server_name：`www.aigicrm.com aigicrm.com` / `aigicrm.com` / `www.aigicrm.com` 三块两边一致。
- location 集合：`/`、`= /index.html`、`= /favicon.ico`、`= /apple-touch-icon*`、`= /doc.html`、`^~ /actuator/`、`^~ /swagger-ui/`、`^~ /v3/api-docs`、`^~ /api/`、`^~ /api/file/download/`、`~* \.mjs$`、静态资源缓存——两边一致（除 B）。
- `.mjs` MIME 固化（生产 mime.types 43 行 + `location ~* \.mjs$`）——生产已有。
- CSP 多 location 整组重抄、HSTS——生产已有。

## 三、测试服偏差（说明）

- 测试服（111.229.37.220）**无 nginx 容器**，API 直连 127.0.0.1:8080；公网 80 为宿主 nginx 1.24.0 但无 ycmn 站点配置 → 对外 502（与 W2 事故同源）。测试服是 API-only 环境，C7 的 nginx 对照对象是生产。

## 四、结论

- **C7 = FAIL（生产配置落后于仓库）**：2 项实质缺口（A 安全 P1、B 功能 P2）。
- 生产更新配置属生产写，须用户窗口授权；更新内容=`nginx-ssl.conf` 的 referer map + `/mp-file-picker` location 两段（其余等价）。
- 证据：`prod-nginx-T.conf`（363 行全量）、`prod-default.conf`（226 行）、本文件。
