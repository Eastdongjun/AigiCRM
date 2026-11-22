# C1 并发 — 代码级核查（2026-11-18，HEAD 724e2279）

> 静态核查；并发重放实验留 W3。生产零写入。

## 一、有并发守卫（PASS）

| 场景 | 位置 | 机制 |
|---|---|---|
| 文件配额扣减 | `FileService.java:404` | **原子条件更新**：`WHERE used+size<=quota` 命中才扣（P2 2026-10-09 修复），并发下不会超卖 |
| 审批处理 | `ApprovalService.java:635-644` | 重复/并发 →「该审批已处理」，多人同时通过结果唯一 |
| 存储空间申请 | `ApprovalService.java:266-274` | 先 `SELECT ... FOR UPDATE` 锁申请者行，再查在途单，防并发落两张 |
| 公海捞取 | `ApprovalService.java:284+` | 池状态（available/pending_approval）检查，已被申请则拒 |
| 权限范围写入 | `RolePermScopeService.updateScopes` | ROLE 级 `SELECT id FROM sys_role WHERE role_code=? FOR UPDATE` 行锁串行化（P1-5 2026-11-15） |
| 文件删除 | `FileService.java:581/638` | `affected==1` 才退款，重复/并发删除幂等 |
| 权限缓存失效 | `PermissionCacheService` | 写时精确 evict + 10min TTL 兜底；Redis 故障降级回源 |

## 二、约束与提示

- **定时任务**：`CustomerExpireTask`（02:00）、`FileRecycleCleanupTask`（03:30）用 `@Scheduled`，**无分布式锁**（未用 ShedLock）——当前**单实例部署**成立；若未来多实例部署，需加锁否则重复执行（P3 架构提示，非当前缺陷）。
- 公海锁超时释放（`sea.claim.lock.days`）为幂等 UPDATE，无并发风险。

## 三、待运行时验证（W3）

- 审批 approve 并发 ×N（两账号同时点），断言仅一条成功、副作用唯一；
- 文件上传并发撞配额（used+size 边界），断言不超卖；
- 权限保存并发（同一角色两个管理员），断言后写覆盖前写 + FOR UPDATE 串行（已代码级）；
- 公海捞取并发（两人同时捞），断言仅一人。

## 四、结论

- **C1 静态 = PASS（核心写点均有原子/锁守卫）**；运行时重放 = BLOCKED（W3 窗口）。
- 证据：本文件；行号基于 HEAD `724e2279`。
