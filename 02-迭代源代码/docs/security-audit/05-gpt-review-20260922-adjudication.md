# GPT 全景审查报告(2026-09-22)逐项裁定

> 审查对象为旧快照 AIGICRM-源代码-20260921-v2审计后.zip(SHA 2d368214…);
> 裁定基线为 origin/main@88fbc3fc(其后的 11 项 P1 修复已全部推送)。
> 裁定时间: 2026-09-22。纪律: 已修复项必须带提交证据, 不得凭记忆。

## 一、P1 裁定总表(11 项)

| 报告编号 | 内容 | 裁定 | 证据/备注 |
|---|---|---|---|
| DEP-P1-001 | 生产存储两套真相 | **已修复** | compose 已改 MYSQL_DATA_DIR bind + NAS_UPLOAD_DIR, 删 named volume(88fbc3fc 内批次2提交) |
| STAB-P1-002 | 自动入海不幂等+定时任务整批中断 | **属实-新增待办 P1** | 代码核实: CustomerExpireTask 循环无逐客户 try/catch(仅 :101 日期解析 catch); 运行日志有 Duplicate entry '1-301' 中断实证 |
| DB-P1-003 | uk_sea_pool_active schema 漂移 | **属实-新增待办 P1** | 迁移链 grep 无该键; 运行库存在(app.log 证据)——手工改库未版本化, 需收编进新迁移 |
| PERM-P1-004 | Score batchSave 无服务端校验 | **已修复** | 批次3: visible 集合+积分项存在+min~max 强校验, 579 绿 |
| PERM-P1-005 | Score grid 带出范围外人员 | **已修复** | 批次3: participants/scores 均按 visible 过滤 |
| PERM-P1-006 | Task 分派/关联对象守卫 | **已修复(90%)** | 批次3: create/transfer assertAssigneeVisible + customerId 存在未删 + sourceLog 归属; 残余: sourceLog 本身可见性未校验 |
| DB-P1-007 | 编号全局唯一 vs 租户取号 | **已修复(主冲突)** | V2026.09.21.3 复合唯一(tenant_id,code)已应用; 残余: 同租户 MAX+1 并发竞态(低) |
| DATA-P1-008 | 创建幂等 Redis fail-open | **已配置化** | aigicrm.idempotency.fail-mode bypass(默认)/strict; DB 幂等表为更强方案, 列产品升级项 |
| BUS-P1-009 | 公海删除绕过客户删除保护 | **属实-新增待办 P1** | SeaService.deletePool 直接 deleteById, 未复用票款/在途审批保护 |
| SEC-P1-010 | 找回密码枚举 | **半修复** | reset 三态文案已归一(批次4); **lookup 端点对存在用户返回掩码手机/邮箱、不存在返回空——此差异未修, 新增待办 P1** |
| BUS-P1-011 | archived 口径未贯穿统计/提醒/定时 | **属实-新增待办 P1** | 多处仅 .ne(status,"sea") 未排 archived |

## 二、P1 门禁类裁定

| 报告编号 | 裁定 |
|---|---|
| MIG-GATE-001 迁移计数 45 硬编码 | **已修复**(动态磁盘计数, chain PASS) |
| QA-GATE-002 PR22 台账包外依赖 | 属实-P2(待迁入稳定路径) |
| ENG-GATE-003 workspace 包外依赖 | 属实-P2 |
| BUILD-001/002 环境 阻断 | 审查环境限制, 非代码问题; 本地实跑 579 绿+188 绿有据 |

## 三、P2 采纳清单(按价值排序)

1. **TEST-P2-008 门禁过度声明**——granularity 脚本只扫配置类 Controller 却输出"所有 Controller": 属实且重要, 待修(扩全量+waiver 机制)
2. **API-P2-002 BusinessException(503) 落 HTTP 400**——与语义不符(我方 fail-open 审计同期发现), 待补映射
3. **ENG-P2-007 审计文档过期**——02-idor 的 biz_task 白名单结论已过时(本次已annotate), 后续审计产物应带 snapshot hash+stale 标志
4. TENANT-P2-003 反向租户完整性门禁(有列→该在白名单)
5. FILE-P2-004 无扩展名上传放行与注释相反
6. FORM-P3-003 draftType 白名单 / CSP unsafe-eval / 生产 CORS localhost / 可观测性 / CI workflow / SBOM / N+1 / 超大类 / Map 入参 —— 均属实, 发布成熟度层队列

## 四、修正报告的两处口径

1. "213 vs 212 端点冲突"系计数口径差异(含 permission-explain 等), 非文档错误
2. 报告第一批清单按本裁定重排: 真正待办 = STAB-002 / DB-003 / BUS-009 / BUS-011 / lookup 半条 + P2 前五项

## 五、下一批修复队列(登记入 PERMISSION-AUDIT-PROGRESS)

B1 CustomerExpireTask 逐客户隔离+汇总日志+告警; B2 addToSea 幂等; B3 uk_sea_pool_active 收编迁移; B4 公海删除复用生命周期保护; B5 archived 统一谓词; B6 lookup 响应归一; B7 503 HTTP 映射; B8 granularity 门禁校准。
