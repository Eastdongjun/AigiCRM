# 交接提示词 — AIGICRM QC 质量检查续作（Claude → ZCode，2026-11-19）

> 投给 ZCode 会话即可零上下文接手 AIGICRM 阶段 B QC 职责。本文件自包含；细节在被指向的 `out/` 文件里。
> 交接者：`01-claude-1c`（Claude Code）｜ 接收者：ZCode

---

## 一、角色与边界

你是 **AIGICRM 阶段 B 的独立 QC**。项目根：`/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`。

- **铁律**：生产**零写入**；生产动作（部署/迁移/写/重启）须用户明确授权；测试服动作走 `ssh_test_once`；**不采信任何自报**（含实施方声称"已修复"）；只输出 `PASS` / `FAIL` / `BLOCKED` 三态，**`BLOCKED ≠ PASS`**；未验证不得算"没有问题"。
- **QC 结论写 `out/`，不 commit**。凭据绝不打印（`~/.aigicrm-deploy.env`、`JWT_SECRET`、`DB_PASSWORD`）。
- 单一执行人：测试服窗口内只有你操作；生产宿主无 jq/mysql 客户端（JSON 走 python3、mysql 容器 exec）。

## 二、当前状态（2026-11-19 02:38 UTC 定格）

| 项 | 状态 |
|---|---|
| HEAD | `080a95f7`（S1068 豁免 e2/e3），工作区干净 |
| **P0-5（Sonar 发布阻断）** | **PASS 已销账** —— 整体 critical **145→0**（java 87 / python 34 / js 24 全清），gate `AIGICRM-P0-5` 绿（新口径 `new_major_violations`，**待用户追认**） |
| W2 数据范围 | DEF-1 修复闭环（TY005 121/121）；MP 权限矩阵 280/280 零不一致 |
| W3 首窗运行�� | C1 公海并发 / C2 双击幂等 / C3 断 Redis **全 PASS** |
| 测试服基线 A | `aigicrm_prod_20261016`，jar `2ea180c6…`、Flyway `2026.11.19.2`、62 表、user_max 82、health 200、Redis 0/0 —— **三次窗口复核零漂移** |
| 测试新库 | `aigicrm_prod_20261118`（生产快照，W2/W3 写残留只落此库） |

## 三、材料索引（先读 `out/qc/check-net/INDEX-20261118.md`）

- **P0-5 销账证据**：`out/qc/check-net/evidence/sonar-20261118/closure-20261118.md`（+ README 滚动记录 40+ commit）
- **波次报告**：`out/阶段B-检查网-第2波-20261118-01claude.md`、`out/阶段B-检查网-第3波-20261118-01claude.md`
- **矩阵**：`out/qc/check-net/matrix-v1.md`（18 域×13 维×13 链，含 D6/D7/D8 证据指针）
- **工单**：`out/qc/check-net/tickets/`（DICT-RPX-001、DEF-1-TY005、MP-PERMS/DEADAPI/LOGIN/APPROVE/FOLDER/TOKEN、C7-NGINX-001、LOW-REDIS-001、DEF-TRANSFER-001、DEF-EXTRADATA-001）
- **债务台账**：`out/qc/check-net/evidence/debt-ledger-20261118.md`（1305→真实约 700；S1068 判定）
- **决策单**：`out/qc/check-net/decisions/B3-B13-decision-sheet-20261118.md`（待用户拍板）
- **W3 第二窗计划与脚本**：`out/qc/check-net/plans/W3-window2-plan-20261118.md` + `/tmp/qc-w3b-orchestrator.sh`

## 四、待用户决策（按优先级，材料全备）

1. **5 个真死字段删/不删**：`FabushiReadingService.postMapper:35`、`PermEpochService.permissionCacheService:32`、`FileService.folderAclMapper:85`、`PermReconcileService.userMapper:35`、`StatisticsQueryService.incentiveService:216` —— 全项目零引用；删除影响 = 5 行 + 测试构造同步 5 处（FileService 4 / PermReconcile 1），风险低。
2. **W3 第二窗授权**：C5 停 DB 注入（`/tmp/qc-w4-c5.sh`）+ DEF-EXTRADATA 运行时复验（`empty-extradata` 探针），编排已备。
3. **MP P1 四张**排期：LOGIN/APPROVE/FOLDER/TOKEN（MP-PERMS/DEADAPI 已修复验）。
4. **生产窗口**：C7-NGINX-001（生产 nginx 缺 referer 脱敏 P1 + 缺 /mp-file-picker CSP P2）、MP-TOKEN-001（主 JWT 进 URL）、CERT-EXPIRY-001（TLS 到期核查，BLOCKED）。
5. **B3-B13 口径拍板**、DEF-TRANSFER-001 裁决（转移事件 oldOwnerName 记成新负责人）、A10 恢复演练授权。
6. **gate 口径校准追认**（`new_violations` → `new_major_violations`，0b 已置顶用户报告）。

## 五、工作协议（继承自 01-claude-1c）

### 5.1 扫描协议（血泪教训）

- 协调/实施方（当前 `01-claude-0b`）每批 **commit → 冻结工作区** → 你扫 → 回 `SCAN_DONE` → 它开下一批。
- **工作区 dirty 时禁止扫描**：scanner 读磁盘、JS 桥接读 HEAD 快照，行数差直接崩（历史两次：BasicSettings 465↔478、PermScopeManager 540↔541）；污染扫描数据必须作废重扫。
- 扫描脚本：`bash /tmp/qc-sonar-f85.sh`（token 运行时生成，coverage 取 `target/site/jacoco/jacoco.xml`；工作区有改动时 `mvn test jacoco:report` 重生成）。
- **Sonar 陷阱**：S5852 按文件逐条报可疑正则、行号随改动漂移，单条微调追不上，去正则/彻底线性才是终局（本轮 4 轮才收敛）。

### 5.2 测试服窗口纪律

- 窗口前/后 **pin 基线 A 八项**（jar/DB/Flyway/62 表/user_max 82/health 200/Redis perm:ctx+login_fail）。
- API 测试会写 `perm:ctx*` 缓存（每 28 用户一键）——**窗口结束必须 DEL 清理并复核**（`/tmp/qc-w2-clean.sh`）。
- token TTL 2 小时：**窗口前重签**（`/tmp/qc-tokens.tsv` 由 `scope_oracle_api_*.py` 的 `make_token` 生成，secret 在 `/tmp/jwt_secret.txt`，**不过网**）。
- 探针：`/tmp/qc-w3-probe.py`（auth-probe / claim-race / double-submit / empty-extradata；token 文件 `/tmp/qc-w3-tokens.tsv`）。
- 公海捞取走 `POST /api/approval/submit {type:"claim"}`（`/api/sea/claim` 不存在）。

### 5.3 已知坑位

- `docker exec -i` 需要 stdin 重定向时必须 `< file`，不可再追加 `</dev/null`（会覆盖）。
- `MAX(version)` 对 Flyway 是字典序 → 用 `ORDER BY installed_rank DESC`。
- 测试服 nginx 只服务 API（公网 80 是宿主 nginx，对 ycmn 站点 502）；API 测试走 `127.0.0.1:8080` 本机。
- `extraData:""` 空串曾 500（DEF-EXTRADATA-001，已修 `ed5f0cc8`）。

## 六、与协调方的关系

- 协调/实施方：`01-claude-0b`（Claude 会话，跨会话消息可达；**它现在处于守候态**，你接手后可直接 SendMessage 联系）。
- 用户拍板项不要代答；口径未定不判缺陷、不销账。

## 七、你的第一步建议

1. 读 `INDEX-20261118.md` 与 `closure-20261118.md`（10 分钟建立全貌）；
2. 向用户确认 5 个真死字段的处理（第 1 项最快）；
3. 与 `01-claude-0b` 建立联系，确认它是否继续承担实施方；
4. 若要动测试服，先 pin 基线 A 再开窗。
