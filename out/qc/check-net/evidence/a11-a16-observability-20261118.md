# A11 审计日志 + A16 监控告警（2026-11-18，只读）

## A11 审计日志

### 覆盖（PASS）

| 事件 | 位置 |
|---|---|
| LOGIN_SUCCESS / LOGIN_FAILED | `SecurityAuditService.java:58-69` |
| LOGIN_FAIL_BURST / LOGIN_FAIL_IP_BURST（爆破告警） | `SecurityAuditService.java:189/193` |
| PERMISSION_CHANGE | `SecurityAuditService.java:36` |
| ACCESS_DENIED（SecurityConfig） | `SecurityConfig.java:111` |
| anonymousProbe（匿名探测） | `AuthController.java:98` |
| 文件授权/删除、夹 ACL、回收导出 | `FileGrantService`/`FileService:652`/`FolderAclService`/`RecycleExportService` |

- 记录字段：事件类型、actorId、username、ip、detail（`SecurityAuditService.persist:234`）。
- 生产量：`sys_audit_log` 1,219 行、`sys_log` 13,264 行。

### 缺口（P3）

- **无保留/清理策略**：`task/` 无日志清理任务（仅 CustomerExpire/FileRecycleCleanup），yml 无 `log.retention` 配置 → 无限增长；含 username/ip/detail（PII）**无期限保留**。建议登记保留策略（如 180 天滚动）——合规观察项。

## A16 监控告警

### 已落地（PASS 部分）

- 生产 crontab：`*/5 * * * * /data/ycmn/scripts/ops-alert-check.sh >> /data/ycmn/aigicrm-alert.log 2>&1`（已核）。
- 脚本 `/data/ycmn/scripts/ops-alert-check.sh`（2757B, Sep 25）存在。
- **日志活跃**：每 5 分钟一条 `OK: 容器在运行, api "code":200, 0 errors(5m), disk ok`（最近 2026-11-19 01:35 CST = 17:35 UTC）。
- 覆盖：容器运行、API health、5 分钟 ERROR 计数、磁盘。
- actuator：health 匿名、metrics/prometheus 需认证（`application.yml:141-146`）。

### 缺口（P2）

- **webhook 未配置**：容器 env 无 `AIGI_ALERT_WEBHOOK` → 告警只写本地日志，**无外部接收人/通知通道**（11-12 审计 OPS-GL-013 的"谁接收"仍未闭环）。
- 未覆盖：NAS/磁盘容量趋势（脚本有 disk 检查但无阈值告警确认）、慢查询、TLS 证书到期（2027-01-13，11-12 审计 P0-8 提及）。
- 自愈默认关闭（`AIGI_ALERT_AUTOHEAL` 未设）——安全默认，符合预期。

## 结论

- **A11 = 覆盖 PASS / 保留策略 MISSING（P3）**。
- **A16 = 自检 PASS（在跑、有日志）/ 告警接收 MISSING（P2）**——OPS-GL-013 部分闭环。
- 建议：①配置 webhook（用户提供地址）；②补容量/证书阈值；③登记日志保留策略。

## A16 补充：CERT-EXPIRY-001（P2 观察，**BLOCKED**）

- 状态：**BLOCKED（网络路径受限）**——QC 本地 `openssl s_client -connect www.aigicrm.com:443` 不通；0b 侧亦无生产机/域名访问权且不越权探测。
- **无新鲜证据：不得引用 11-12 审计记录的到期日（2027-01-13）作为当前结论。**
- 执行口径（生产窗口，需用户授权）：生产机侧 `echo | openssl s_client -connect 127.0.0.1:443 -servername www.aigicrm.com | openssl x509 -noout -dates`，核对实际到期日并登记。
