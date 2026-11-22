# 小程序 Crosswalk 与推进台账（MP-W0，2026-11-18）

> 来源：11-12 外部审计 miniprogram 17 条、11-13 代码审查 2 P0/P1-4/P1-5、11-17 微信身份/真机缺口、MP 契约与 Node 测试。
> 原则：已核的写状态；未核的写"待核"；不因 PC 通过而默认 MP 通过。

## 一、11-12 miniprogram 17 条

| ID | 级别 | 发现 | 当前状态 |
|---|---|---|---|
| MINI-001 | HIGH | 主 JWT 查询串传 web-view；nginx referer 未脱敏 | **部分**：referer 已脱敏（`nginx-ssl.conf:38-42`）；MP `picker/index.js:37 token=` 仍在（JWT 主 token 暴露面未根除） |
| MINI-002 | MEDIUM | 『记住密码』base64 存明文口令、无有效期/设备密钥、rememberMe 恒 true | **OPEN**：`pages/login/index.js encodeSaved/decodeSaved` 仍 base64；需改安全存储/有效期/开关 |
| MINI-003 | MEDIUM | `wx.downloadFile` 未复用统一 401 语义（不清 token/不跳登录） | **待核**：`utils/attachment.js:64` |
| MINI-004 | MEDIUM | 自动化探针假 token 写入真实 storage，清理在 && 链末尾 | **待核**：`scripts/miniprogram/package.json:10` |
| MINI-005 | MEDIUM | 激励 stats 页强制 `incentive:view`，仅持 stats/rank 码被踢 | **已修**：`pages/incentive/index.js:626` 按 tabMode 分流（M-10 2026-11-12 修复） |
| MINI-006 | MEDIUM | 审批『通过』一键即提交，无二次确认 | **OPEN**：`pages/approval/index.js approve()` 无 showModal 确认 |
| MINI-007 | MEDIUM | PC/MP 功能真源与 parity 文档漂移（manifest/06 矩阵过期） | **待核**：`docs/unified-feature-manifest.md:70` |
| MINI-008 | MEDIUM | MP 残留已删除端点包装（发票/回款 add/delete） | **OPEN**：`api/customer.js:45-48` 仍导出 addInvoice/deleteInvoice/addPayment/deletePayment |
| MINI-009 | LOW | 冷启权限回填竞态仅文件页接入 whenAuthReady | **待核**：`pages/files/permission-ready.js:4` |
| MINI-010 | LOW | 会话清理不对称（退出不清记住密码；清缓存不清预览） | **待核**：`utils/auth.js:60` |
| MINI-011 | LOW | 术语单一源失效；70 处硬编码『学分』 | **待核**：`utils/terminology.js` 零引用 |
| MINI-012 | LOW | 死代码：demo-finance 零引用、api/customer 7 个未用导出等 | **待核** |
| MINI-013 | LOW | 22 处 build*View 死字段 | **待核**：`pages/files/index.js:316` |
| MINI-014 | LOW | 休眠契约 `test-miniprogram-incentive-dial.js` 断言已退役 heroInitial 且未接 CI | **待核**：该脚本在 16 个 Node 测试中（见 MP 验证日志） |
| MINI-015 | MEDIUM | 发布手册只登记 request 域名，未覆盖 downloadFile/uploadFile/web-view | **OPEN**：`docs/miniprogram-release-runbook.md:79` |
| MINI-016 | LOW | 超长页面文件（incentive 1655 行/upload 1423/files 1368） | **OPEN（债务）** |
| MINI-017 | INFO | 导航契约正则盲区（textview 页误判不可达） | **待核**：`scripts/check-mobile-navigation-contract.py:13` |

## 二、11-13 代码审查 MP 项

| 项 | 状态 |
|---|---|
| P0-1 `normalizeFile` 丢弃行级 `perms` | **OPEN**：`pages/files/index.js:804-825` 返回对象无 `perms`（原始 item.perms 在 216/279 等使用，但归一化后视图丢） |
| P0-2 四项服务 tab 写按钮实际无效 | **待核**：tab 在 file 视图，需查 `views/file/index.vue`/`FileTable.vue` |
| P1-4 MP 不消费 `folderActions` | **OPEN**：MP `grep folderActions` 零命中，只有 `perms/folderPerms` |
| P1-5 权限变更后 MP 不刷新权限位 | **待核**：F-MP1 `9db1e53e` 更新测试断言；需看 `test-miniprogram-permission-refresh.js` 结果 |

## 三、11-17 相关缺口

| 项 | 状态 |
|---|---|
| 微信身份链路（登录/绑定/解绑/换绑无接口无测试） | **待核**：`grep wechat.*bind` 后端/MP；RTM G6 |
| 真机 web-view/PDF/订阅消息 | **未做（S）**：需用户提供 DevTools/真机 |
| F-MP2 性能回归需 devtools+automator | **S**：本机 Node 跑 `test-miniprogram-perf-regression.js` 预期失败/跳过；等 DevTools 环境 |

## 四、优先级与工单建议

| 优先级 | 项 | 工单 |
|---|---|---|
| P0 | P0-1 normalizeFile perms | MP-PERMS-001 |
| P0 | MINI-008 残留已删端点 | MP-DEADAPI-001 |
| P1 | MINI-002 记住密码安全 | MP-LOGIN-001 |
| P1 | MINI-006 审批二次确认 | MP-APPROVE-001 |
| P1 | P1-4 folderActions | MP-FOLDER-001 |
| P1 | P1-5 权限刷新 | MP-PERM-001（F-MP1 复验） |
| P1 | MINI-001 JWT 查询串 | MP-TOKEN-001（短时 download token） |
| P2 | MINI-015 发布手册域名 | MP-RELEASE-001 |
| P2 | MINI-003/004/007/009/010/011/012/013/014/016/017 | 批次债务 |
| S | 真机/F-MP2 | 等用户 DevTools/真机 |

## 五、MP 检查网矩阵初值（L11 × 6 阶段）

| 阶段 | 状态 | 依据 |
|---|---|---|
| 入口可见（tabBar/应用中心） | P | 5 tab + 应用中心；权限码消费存在（部分契约） |
| 提交/动作（上传/分享/阅读） | P | 契约 + Node 测试；P0-1 perms 未透传 |
| 后端状态/DB | U | 未在 MP 运行时链路验证（W2 窗口可补 API 层） |
| 跨端反映（PC/MP/H5） | U | 未做三端差分 |
| 负例/守卫（403/边界） | P | 契约覆盖部分；需 28 用户运行时 |
| 补偿/回滚（失败重试/离线） | U | 未做 |

- 0 C；P 3 / U 3。MP 检查网仍是"静态有骨架、运行时空白"。
