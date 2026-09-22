# GPT《External(1) 再审计报告》逐项裁定(2026-09-22)

> 审查对象: `AIGICRM-源代码-20260922-external(1).zip`(即本仓库 external 档二次产物, SHA cc3fb7d6…)
> 裁定基线: 本轮修复前 origin/main@1ff083ad; 修复后以本文件第五节提交为准。
> 纪律: 属实项先实测核验再修;已修项带门禁证据;包视角与项目视角分开表述。

## 一、14 项 P1 裁定总表

| GPT 编号 | 内容 | 裁定 | 处置 |
|---|---|---|---|
| ACL-P1-001 | resolveModuleUsers null(all) 在全局表放大为全平台(Score/Task/SysLog 三处) | **属实(三处全中,本轮修)** | SysLog 处注释写"本集团全部"而代码空操作,比 GPT 描述更严重。新增 `OrgScopeResolver.resolveModuleUsersWithinGroup`(非admin的all→当前集团成员集,无集团=空集 fail-closed,与 ScopeService.visibleUsers 同口径),三处调用点切换;admin 保持平台全量 |
| SCORE-P1-002 | participantIds 独立于 submitted 未校验,不提交 entry 即可混入范围外名单 | **属实(本轮修)** | participantSet 逐一过 saveVisible 校验(WithinGroup 收口后非admin恒非null);去重已有(Set) |
| TASK-P1-003 | 关联客户只查 tenant 存在性,同集团横向可挂无权客户 | **属实(本轮修)** | create 增加 `customerAccessService.assertCanView(customerId)` 对象守卫 |
| AUTH-P1-004 | lookup 仍是账号存在性 oracle | 属实-部分已知 | B6(2026-09-22)已修"不存在→全 false 无掩码";残余="存在且有联系方式→hasPhone+掩码"仍可识别存在。产品权衡项(找回密码可用性),维持挂账;缓解=限流+ANON_PROBE 审计+错误计数 |
| CONC-P1-005/006 | 客户/审批编号 MAX+1 并发撞号 | 属实-已知 | DB-P1-007 残余(05 裁定);下一批次号段表方案 |
| IDEM-P1-007 | 幂等默认 bypass+finish 失败丢结果 | 属实-已知 | DATA-P1-008 配置化;finish 丢失链是有效增量细节,并入 DB 幂等表批次 |
| FILE-P1-008 | 注释称无扩展名拒绝、代码放行 | **属实(本轮修)** | 改为真拒绝(对齐白名单哲学);replaceFile 的 null 文件名场景以旧文件扩展兜底(类型继承自已过白名单的旧文件,治理不破);新增 4 断言负向用例 |
| FILE-P1-009 | 物理删除失败无补偿台账 | 属实-已知 | 上轮已改真实注释+warn;FileDeletionOutbox 列产品批次 |
| CI-P1-010 | 包内 ci.yml 平铺根目录,非 Actions 识别位置 | **属实(打包缺陷,本轮修)** | `zip -qj` 平铺所致;改保留 `.github/workflows/ci.yml` 原路径。仓库实际位置=仓库根 .github/workflows/(CI 已触发,非"CI 不存在") |
| CI-P1-011 | Node20 声明与锁文件冲突(jsdom30/undici8 要≥22) | **属实(本轮修)** | engines `>=20 <23`→`>=22.22.2 <23`;CI node-version '20'→'22'(两处);此前 CI "绿"系 npm 对 EBADENGINE 仅警告 |
| AGENT-P1-012 | CLAUDE.md 顶部 Java21 与后部 JDK17 门禁并存 | **属实(本轮修)** | 后部"必须 JDK 17/export openjdk@17"段落对齐 Java21;基线唯一事实源=pom.xml |
| TEST-P1-013 | 结构型源码字符串测试占比高,不等价行为证据 | 属实-已知 | 16项#16 早已分类"结构 grep 与行为测试互补";本轮 PermissionMatrixTest 断言随行为升级同步收紧(非删除)。分层标签建议采纳列 P2 |
| SUPPLY-P1-014 | CI action 未 pin SHA/npx 拉最新 | 属实-已知 | 07 报告 P1-10 同项 |

## 二、P2 组裁定摘要

Reminder 拆分/Sea 双层文档化/多集团 fallback(裁决挂起)/N+1/大文件容量:均已知挂账,无新事实。
DB-P2-004(4 张含 tenant_id 未白名单表: perm_scope_field/perm_scope_org/biz_sea_config_region/biz_sea_config_scope):**属实-新采纳**——静态确认 Java 无业务使用,判历史/死表;处置=登记 TENANT-P2-003 反向门禁清单(不删,删需迁移),下一批次实现"有列→须在白名单或 legacy 名单"反向断言。
SEC-P2-007(迁移 SQL 含历史默认口令 bcrypt hash 用于识别强制改密):属实-低风险(非明文+强制改密逻辑+生产已轮换);在外发 manifest 补一句说明即可,不改迁移内容。

## 三、包视角澄清(非项目缺失)

D11/D12/D15/D16/D18/D20 的 BLOCKED:external 档有意排除内部证据(pkg-manifest 已声明),下轮如签 Release Gate 需审完整内部包——与 07 报告"完整仓库视角 9✅4⚠️2❌"并存不冲突。动态 BLOCKED(mvnw/npm):GPT 环境无网;本机本轮全量实测 580/580+188/188。

## 四、本轮修复清单与门禁证据

| # | 修复 | 门禁 |
|---|---|---|
| 1 | OrgScopeResolver+WithinGroup 新方法;Score visibleUserIds/Task assertAssigneeVisible/SysLog buildBase 三处切换 | 全量 **580/580**;测试服部署(health UP)后矩阵 **20/20** |
| 2 | TaskService.create 客户对象守卫(assertCanView)+注入 | 同上(TaskServiceTest 桩同步) |
| 3 | Score batchSave participantSet 范围校验 | 同上 |
| 4 | FileService 无扩展名拒绝+replaceFile 旧扩展兜底+extOf+4 断言负向用例 | FileServicePermissionTest 41/41(含新用例) |
| 5 | CLAUDE.md JDK17 残留清偿;package.json engines/CI node 22 对齐 | 前端 **188/188**(本机 node22);grep 断言无 JDK17 残留 |
| 6 | 打包 ci.yml 保留 .github/workflows 原路径 | 重打包断言门通过 |
| 7 | 测试构造器连锁适配(OrgScopeResolverTest 27/ScopeServiceTest/PermissionSecurityFixTest/TaskServiceTest/DataScopeGuardTest) | 全量 580/580 覆盖 |

构造器注入扩张致 5 个测试文件 30+ 处适配——@RequiredArgsConstructor 增字段的测试侧成本,已如实记录。

## 五、残余开放(合并队列)

- P1 下一批次: 号段表取号/幂等 DB 化(含 finish 丢失链)/审批撤回+超时/reminder 权限拆分/告警通道/CVE 精筛+CI SBOM job/恢复演练(需授权)/性能基线(需目标并发)/tenant 反向白名单门禁(新)/制品签名+action pin
- 产品权衡挂账: lookup 存在性残余(限流+审计缓解)/多集团 currentGroup(单集团裁决下 RA)/文件内容检测(magic/AV,按威胁模型)
