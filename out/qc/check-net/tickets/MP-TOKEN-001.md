# MP-TOKEN-001 — 主 JWT 查询串传 web-view（P1）

- 位置：`aigicrm-miniprogram/pages/files/picker/index.js:37`
- 修复：改短时 download token（单用途/短过期）
- 验收：token 不再主 JWT；referer 脱敏保留
- 归属：实施方（协调方 0b）；QC 复验
- 状态：OPEN（2026-11-18）

## QC 设计约束（复验口径，2026-11-18 补）

短时 token 至少满足：
1. **单用途**：只能换文件下载/预览，不能换 access token、不能调业务 API；
2. **短过期**：≤5 分钟，且一次性（用后失效）优先；
3. **绑定**：user_id + 文件 id（+ 租户），不能跨文件/跨用户重放；
4. **不可伪造**：服务端签发（HMAC/JWT 专用密钥，非主 JWT 密钥），URL 不含主 JWT；
5. **审计**：签发/使用打日志（user、file、ip、结果），失败有计数；
6. **兼容**：PC 预览与 MP 预览路径不回归；referer 脱敏保留。
验收证据：抓包/日志证明 URL 无主 JWT；token 重放第二次失败；过期后失败；跨文件使用失败。

## 现状事实（QC 2026-11-18 核实 @ d052189a）

- **前端仍用主 JWT**：`pages/files/picker/index.js:32-38` `const token = getToken()` → `src = apiBaseUrl + '/mp-file-picker?token=' + encodeURIComponent(token)`——主 JWT 在 URL 查询串，经 web-view 加载。
- **后端短时 token 能力已存在**：`GET /api/file/download-token`（`FileController:46-56`）→ `JwtTokenProvider.generateDownloadToken(userId, parentJti)`：`scope=download`、TTL **1h**、jti、`pjti`（主 token 注销联动）。
- **scope 限制**：`JwtAuthFilter:39/165` 强制 URL token 必须 `scope=download` 且仅允许下载/导出路径——**H5 上传路径不能直接换用 download token**，需新增 `scope=upload`（或 picker 上传专用短时票）并同样限路径。
- MP `api/file.js:45` 已有 `download-token` 调用（下载/预览侧），说明基础设施可复用；缺的是 picker 上传路径的短时票设计。
- 建议修复顺序：①后端加 `scope=upload` 短时票（TTL ≤5min、绑用户、仅 `/api/file/upload*`）；②picker 改成先取短时票再拼 URL；③H5 页面识别 scope 并只用对应范围 API；④主 JWT 不再出现在任何 URL。

---

## 销账（2026-11-19，QC fabushi-upload-security-debt）

**状态：CLOSED**。修复链：F3a-1 `5921fdfe`（法布施上传通道短时票）→ F3a-2 `346a0be6`（阅读器换票）→ F3b `01f81bfb`+fix `4b5ea1e9`（文件选择器换票，全仓最后一处）。

**终验证据**：
1. 两轮全仓 grep：全部 8 处 `token=` URL 逐一溯源均为 getDownloadToken 短时票通道（fabushi detail/upload 试听、incentive index/detail/upload、files index 均闭环）；两 web-view H5（fabushi-uploader/file-picker）+阅读器均 5 分钟 scene 短时票
2. W-A live 窗口票制矩阵 5/5 PASS（T2.1-2.5：有效 200/过期 401/scope 403/scene 403/pjti 撤销联动 401）
3. 最终扫 4b5ea1e9：gate OK、critical=0、unresolved=750 基线
**主 JWT 彻底退出 URL。**
