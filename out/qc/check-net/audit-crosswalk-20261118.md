# 旧审计 Crosswalk v0.1（2026-11-18，W0b）

> 目的：把 11-12 外部审计、11-13 代码审查、11-17 独立审计的发现逐条映射到当前 HEAD 与登记册，解决"旧审计未进登记册、阶段 A 全绿≠全量闭环"的口径冲突。
> 原则：有当前证据的才写"已核"；未复跑的写"待核"，不默认已修。

## 一、2026-11-12 外部审计（`05-测试与质量/audit-evidence/AIGICRM-20261112/`）

- 结构化发现：`findings-all.json` **124 条** = CRITICAL 1 + HIGH 10 + MEDIUM 54 + LOW 43 + INFO 16。
- 类别 Top：ops 25、quality 21、miniprogram 17、database 16、security 4、maintainability 4，其余为数据范围/认证/API/信息暴露/permission/supply-chain 等。

### 1.1 CRITICAL / HIGH 逐条

| 严重级 | ID | 规则 | 位置 | 当前状态 |
|---|---|---|---|---|
| CRITICAL | BIZ-20261112-001 | BIZ-20 | `CustomerService.java:805` assertDeletable 未校验阶段总额 | **已核 PASS**：P0-1 修复（补 `biz_customer_stage_amount` + 负例 `delete_withStageAmountsOnly_rejected`，QC 亲跑 1/0/0，全量 1187/0） |
| HIGH | BIZ-20261112-002 | BIZ-13 | 旧 `/api/customer/rankings` 仅集团过滤 | **已核 PASS**：P0-2 端点退役，API 清单无旧端点，行为测试存在 |
| HIGH | AUDIT-20261112-DB-01 | DB-01 | `deploy-db.sh:80` 无条件 flyway repair、不跑冻结契约 | **已核 PASS（Batch-4 #2）**：`ecf1a9f8` 补 VALIDATE_RC/MIGRATE_RC/REPAIR_RC 退出码门禁；`127787ad` 补 pending 例外（窗口实证）；QC8 负例 3/3 |
| HIGH | FE-PC-001 | FE-02 | `FilePicker.vue:75` 动态外链 JSSDK 与 CSP `script-src 'self'` 冲突 | **已修**：`nginx/nginx-ssl.conf:150-153` 对 `/mp-file-picker` 定向放行 `https://res.wx.qq.com`；UI 模板 `nginx.conf.template` 未同步该定向（登记为漂移，生产用 nginx-ssl） |
| HIGH | GATE-20261112-001 | GATE-F-01 | 前端生产依赖 38 项漏洞（31 High），CI npm audit 红 | **已核 PASS（P0-4）**：allowlist 带理由（follow-redirects/@vue/server-renderer/source-map-js），CI 11-12 后连续 success |
| HIGH | GATE-20261112-002 | GATE-F-02 | axios 1.20.0 → follow-redirects 1.16.0 三条 High | **已核 PASS（P0-4）**：ACCEPTED 台账（axios Node 传输层、浏览器 bundle 不含） |
| HIGH | LEAD-20261112-DEP-001 | DOCKER-04/CVE | 后端运行时依赖 8 CRITICAL + 13 HIGH（OSV） | **已核 PASS（P0-3，CI 级）**：Boot 4.1.2/依赖覆盖；CI osv-scan success；本地 `mvn clean verify` 与 OSV 原始日志待补 |
| HIGH | LEAD-20261112-DEP-002 | DOCKER-04/CVE | 前端生产依赖 16 条 high | **已核 PASS（P0-4，CI 级）**：npm audit allowlist + CI success |
| HIGH | MINI-20261112-001 | SEC-30 | 主 JWT 以查询串传 web-view，nginx referer 未脱敏 | **部分修复**：`nginx-ssl.conf:38-42` 已加 `$http_referer` 脱敏；**MP picker 仍 `token=` 查询串**（`pages/files/picker/index.js:37`），JWT 主 token 暴露面未根除 |
| HIGH | OPS-20261112-001 | DOCKER-02 | 共享/测试 compose 后端 8080 发布 0.0.0.0 且 root | **已修**：`docker-compose.yml:13` 与 `docker-compose.prod.yml:64` 均 `127.0.0.1:8080:8080`；`Dockerfile` `USER 10001:10001` |
| HIGH | OPS-20261112-002 | COMMON-03 | 部署 env 模板/规范/脚本三口径冲突（ycmn vs aigicrm） | **已修/缓解**：实际 `~/.aigicrm-deploy.env` 已钉 `APP_SLUG/DB_NAME=ycmn`、`DEPLOY_DIR=/root/ycmn`、`NAS_DIST_DIR=/Aigicrm/files/ycmn-dist`；`assert_prod_env_pinned` 已加；示例模板仍 aigicrm（文档债） |

### 1.2 MEDIUM/LOW/INFO（54+43+16=113 条）

- 全量明细在 `findings-all.json`；本版不逐条展开。按类别归并到登记册：
  - ops 25 → 登记册 A16/C7/E5/E6
  - quality 21 → 登记册 C9/D11/E1
  - miniprogram 17 → 登记册 A13/C10/E12
  - database 16 → 登记册 A9/A20/C7
  - security 4 → 登记册 C6/E3/E7
  - maintainability 4 → 登记册 D9/E10
  - 其余 → 对应 A/B/C/D/E 项
- **W1/W2 动作**：逐条判"已修/已接受/真缺口"，写入登记册或独立销账表；未逐条核前不得整体宣称"审计 121 条已闭环"。

## 二、2026-11-13 文件管理代码审查（`05-测试与质量/代码审查报告-文件管理权限与性能-20261113.md`）

| 级别 | 发现 | 当前状态 |
|---|---|---|
| P0-1 | 小程序 `normalizeFile` 丢弃行级 `perms`，T2-MP 修复形同虚设 | **仍 OPEN**：`pages/files/index.js:804-825` 返回对象无 `perms` 字段（有 uploaderId/canDownload，但未透传行级 perms） |
| P0-2 | `3aaca9d1`「四项服务 tab 放开写按钮」实际无效 | **已修（代码级）**：`FileTable.vue:159-178` 行级 `row.perms` 优先 + `isOwn` 兜底 + `canFileAction`；T2 2026-11-12 修复；UI 实拍待 DevTools |
| P1-3 | 显示层与点击处理器权限口径不一致 | **待核** |
| P1-4 | 小程序不消费 `folderActions`，夹菜单与 PC 不一致 | **待核** |
| P1-5 | 权限变更后小程序不刷新权限位 | **待核**（F-MP1 可能部分覆盖） |
| — | Sonar 质量门与密钥扫描门未生效（无 token） | **已核**：P0-5 本地通道已恢复；严格口径未达标（6/459） |

## 三、2026-11-17 独立审计（`out/独立审计报告-20261117.md`）

### 3.1 前 AI 虚报清单（9 条）

| # | 声称 vs 实测 | 当前状态 |
|---|---|---|
| 1 | Redis 断连因果/时长（7 分钟 vs 22.5 分钟） | 历史事件；当前生产稳定，登记册 C7/E5 |
| 2 | 「权限零缓存」实为 Redis TTL 10min | 机制事实；登记册 C3，需持续验证 |
| 3 | 「contracts 190 全绿」实为 148 PASS | **已变化**：当前 191 PASS/1 SKIP/0 FAIL（A1 亲跑），数字已更新 |
| 4 | 「26 姓名全扫零命中」缺张杰、契约自身写死 26 名 | **待核**（W1：查 `check-no-hardcoded-persons.py` 与 KNOWN_NAMES 是否已修） |
| 5 | 四角色绑定 110/88/67/53 与实测 111/87/65/52 不符 | **待核**（W1：当前权限矩阵复跑） |
| 6 | 3 个测试号仍活跃 | **待核**（W1：生产/测试库查 TY101/smoke_sales/__test_user_e2e__） |
| 7 | 后端独有 23 码 = INFO | **待核** |
| 8 | deploy-backend.sh 快滚 + RESULT | **待核**（W1：当前脚本与生产实际版本） |
| 9 | U1/U2 PDF 已上生产 | **待核**（生产只读核验） |

### 3.2 漏报清单（10 条）

| 级别 | 发现 | 当前状态 |
|---|---|---|
| 高危 1 | 销售总监角色拥有 26 个 system 权限（含 12 个增删改/授权码） | **待核**（W1：查生产 role 10 权限集） |
| 高危 2 | 反硬编码契约自身写死 26 个员工姓名，冒烟脚本写死工号/用户 ID | **已修（2026-11-18 批次，QC 复核）**：`scripts/check-no-hardcoded-persons.py` 已改为 `KNOWN_NAME_HASHES`（SHA-256 比对，第 40 行），明文姓名 grep=0；脚本实跑 `PASS: 业务代码零硬编码人员(姓名/工号/用户ID 字面量)` RC=0。剩余：冒烟脚本自身工号字面量属夹具、不在业务扫描面（如需也可哈希化，P3） |
| 中危 3 | 同一 admin token 并发保存两个权限码，后写覆盖前写 | **已修**：`RolePermScopeService.updateScopes` 对 ROLE 级写 `SELECT id FROM sys_role WHERE role_code=? FOR UPDATE`（P1-5 2026-11-15 行锁串行化） |
| 中危 4 | 法布施空间上传文件无删除通道（admin 403） | **已修（代码级）**：`FileService.java:600-602` + `FolderPermissionService.java:330-341` 已实现 `fabushi:file:delete` 独立码 + 上传人范围；运行时待验 |
| 中危 5 | 快滚对 stop/rm 容器无效 | **待核/OPEN**：本地 `fast_rollback_container` 仍以 `docker restart` 为主；对 stop/rm 容器是否有效需实测 |
| 中危 6 | 生产机 deploy-backend.sh 仍 67 行旧版、REDIS_HOST=redis | **仍 OPEN**：生产 `/data/ycmn/scripts/deploy-backend.sh` 实测 **67 行**（md5 `2f8707d2…`，无 RESULT/快滚）vs 仓库 **352 行**；更新生产脚本须用户授权 |
| 低危 7 | Redis 故障被报「数据库异常」 | **已建单** `tickets/LOW-REDIS-001.md`（P3）：`GlobalExceptionHandler:188-197` 对全部 `DataAccessException` 统一记"数据库异常"，`RedisConnectionFailureException` 继承它 → 误导排障；待 0b 修复 |
| 低危 8 | 文件列表 P95 562ms | 登记册 A15（性能） |
| 低危 9 | 迁移/原型/主代码注释含真实姓名与工号 | **待核**：secret-scan 通过 ≠ 姓名扫描；结合 High 2 |
| 低危 10 | 注册表残留 2 个 deprecated 码 | **已核/非缺陷**：`gen-perm.py:8 LEGACY_PERMISSION_CODES` 实为 **10 条显式登记的历史码，每条带裁决理由**（如 `incentive:view-all`「改由授权表按人核定」、`sea:pool:delete`「运行时由池级 PERM_DELETE 守卫」）；生成器排除它们是设计，不是残留 |

## 四、结论

1. **11-12 审计的 1 CRITICAL + 5/10 HIGH 已有当前证据**（P0-1/2/3/4/6）；其余 5 条 HIGH（FE-PC-001、MINI-001、OPS-001/002）与 113 条 MEDIUM/LOW/INFO **尚未逐条销账**，是 W1/W2 的实打实工作。
2. **11-13 的 2 个 P0 与 11-17 的 19 条发现基本未进登记册**；本文件只是 crosswalk 起点，不是销账。
3. **阶段 A「全绿」只覆盖治理范围**，不能推导全量审计闭环；P0-5 已证伪"全绿"叙事（6/459）。
4. 下一步：W1 逐条核 5 条 HIGH + 2 个 P0；W2 按模块核 MEDIUM/LOW；每条落入登记册/销账表并附证据。
