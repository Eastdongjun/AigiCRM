# GPT《External 源码全生命周期审计报告》逐项裁定(2026-09-22)

> 审查对象: `AIGICRM-源代码-20260922-external.zip`(external 脱敏档, SHA256 de8ee86b…f031)
> 裁定基线: origin/main@7c47358b; 裁定时间: 2026-09-22。
> 纪律: 属实项带实测证据; 区分"包内视角缺位"与"项目真实缺失"; 已知挂账注明出处; 本轮直接修复的标注修复提交。
> 参照系: 本仓库同日自审报告 07-agent-native-audit-20260922.md(基线 d8a6a239)。

## 一、总体判定口径修正

GPT 判定 "FAIL / NOT RELEASE READY" 是 **external 包视角**,不等同项目判定:
- 包≠项目: SBOM/CleanRoom/CI/备份/审计档案均存在于完整仓库,被 external 档排除规则隔离(GPT 环境看不到);
- 动态验证 BLOCKED(mvnw 下载/无 node_modules)是 GPT 环境无网,非项目缺陷——完整环境当日 `mvn package` 成功、585 测试绿(f14e1b25);
- 但**包自身有 2 个真缺陷必须修**(见 P1-002 与 P1-012 部分):包的使命是供外部审查,小程序不可编译即包废。

## 二、12 项 P1 逐项裁定

| GPT 编号 | 内容 | 裁定 | 证据/处置 |
|---|---|---|---|
| P1-001 | AGENTS/CLAUDE 基线冲突 | **属实-新采纳** | 实测: CLAUDE.md:11 声明 Boot 3.2.5/Java17、:125 "无 Maven Wrapper",实际 pom=Boot 4.1.0 parent+Java21+mvnw 在库。系总报告 ENG-P2-006 挂账一直未修,本轮修复(CLAUDE.md 对齐+版本一致性校验入 doc-freshness)。**自我检讨: 07 审计 D01 漏列此项** |
| P1-002 | external 缺 config/env* | **属实-打包缺陷(本轮修)** | 实测: zip 内 `config/env` 0 命中、miniprogram/config/ 空目录——排除模式 `*/config/env*.js` 连坐排掉了 env.js(demo 默认档,无敏感信息且小程序编译必需)与 example 模板。正确边界: 只排 env.local.js(本机覆盖档,含测试服地址)。**自我检讨: 07 审计未验外发包内容完整性** |
| P1-003 | 取号 MAX+1 并发 | 属实-已知 | 05 裁定 DB-P1-007 残余"同租户 MAX+1 竞态(低)";07 D08-01 RA。维持 P1:撞 uk 表现为随机失败;修复选项(号段表/INCR/重试上限)列开发批次 |
| P1-004 | 幂等默认 bypass | 属实-已知 | DATA-P1-008 配置化+产品升级项(DB 幂等表);修复=生产关键写默认 strict(一行配置+负向验证),列下一批次 |
| P1-005 | reminder:view 承载写 | 属实-已知升档 | 原 PERM-P2-001;GPT 升 P1 合理(权限语义可解释性),拆 view/process/generate 小工作量,列下一批次 |
| P1-006 | sea:view 承载删除 | 属实-已知-**有意设计** | B8 granularity 豁免清单已登记"公海池 ACL 双闸":入口权限+对象 ACL 双层;GPT 亦确认"实际安全性好于注解表面"。修复=权限架构文档显式化双层模型(不改码),降 P2 |
| P1-007 | 上传仅扩展名校验 | 部分属实-拆分 | 无扩展名放行与注释相反=已知 FILE-P2-004(未修,P3,本轮顺带修注释);magic bytes/病毒扫描=增量增强,按威胁模型列 P2(下载链路强制 attachment+HTML/SVG 拒收已缓解执行面) |
| P1-008 | deleteStorage 虚构"清理任务兜底" | **属实-新采纳(GPT 本轮最有价值发现)** | 实测: 全库 grep 无该清理任务实现;FileService:477 注释引用不存在的东西。**自我检讨: 07 审计 D13-02 将此 catch 评为"带理由旁路"——理由本身是假的,误采信注释**。本轮最小修: 注释改真实状态+log.warn 留痕;Outbox 台账列产品批次 |
| P1-009 | 多集团静默取最小集团 | 属实-已知+产品裁决 | TENANT-P2-003;单集团部署裁决下 RISK_ACCEPTED(安全不扩大,合法数据被过滤属功能缺口);多集团需求启动时实施"强制选集团/重签 token" |
| P1-010 | Agent Provenance 不足 | 属实-已知(视角受限) | 包内仅见 1 份 handoff,完整仓库 472 份+1152 runs;07 D11-02 已 FAIL 同题(模型版本未记录)。采纳 `.audit/` 结构建议,列 P2 |
| P1-011 | 缺独立验证 | 属实-已知(视角受限) | 07 P1-6 同结论(Builder 同体,两轮 GPT 外审为唯一独立验证);GPT 看不到 runs/审计档案但结论一致,互为印证 |
| P1-012 | 无 SBOM/License/SCA | **部分误报+部分属实** | SBOM 误报: 当日已入库 engineering/evidence/sbom-20260922/(191+87 组件+OSV 初筛 278 包),被 `*/engineering/*` 排除出包;License BOM/SCA 精筛属实(07 D15-04 NC/P1-13)。CI workflow 属实但根因是 **ci.yml 在仓库根 .github/,打包根为 02-迭代源代码/**,结构性不可达(本轮打包纳入) |

## 三、P2 组裁定摘要

| GPT 编号 | 裁定 |
|---|---|
| P2-001 包非完整交付包 | 有意设计(external=脱敏档);采纳 manifest 建议(本轮打包附 INCLUDE-MANIFEST.md) |
| P2-002 pom 注释版本漂移 | 属实(106 行"3.2.12 BOM"/166 行"生产 JDK17"残留),本轮修 |
| P2-003 N+1 | 已知 PERF-P2-012(07 P2-14 同) |
| P2-004 Redis KEYS | 已知(PermissionCacheService:131 低频 evictAll,07 D17-03 同) |
| P2-005 500MB 上限偏高 | **属实-增量采纳**:无并发/配额保护下的资源耗尽路径,列配置治理批次 |
| P2-006/007 审计防篡改与降级策略 | 属实-增强项(P2) |
| P2-008 demoMode release 门控 | 部分已有(env.js:release/trial 运行时强制真实地址);"CI 检查 release 无 demo 写入路径"为增量,列 P2 |
| P2-009 状态字符串散落 | 与 07 P1-5/D03-02 一致 |
| P2-010 性能基线缺失 | 与 07 P1-15 一致 |
| P2-011 CleanRoom/恢复证据缺失 | 误报+属实混合: CleanRoom 当日实测 48/48+10/10(完整仓库);恢复演练确缺(07 P1-11,J13 待授权) |

## 四、对 07 自审报告的修正(审计纪律:错必改)

1. **D13-02 修正**: FileService deleteStorage 的 catch(ignored) 原评"带理由旁路(清理任务兜底)"——理由经查为虚构引用,应为"无兜底旁路+注释失实"。D13-02 由 PASS 降为 **PASS(带1项失实注释,本轮修复)**。
2. **D01 增补**: 漏列基线文档冲突(ENG-P2-006),本轮修复后关闭。
3. **D15/交付增补**: 07 未验外发包内容完整性(env 模板连坐排除/CI 不可达),本轮修复+重打包。

## 五、本轮直接修复清单

| # | 修复 | 验证 |
|---|---|---|
| 1 | package-source.sh: config 排除精确化(只排 env.local.js,保留 env.js demo 档+两份 example);打包根纳入仓库根 .github/workflows;附 INCLUDE-MANIFEST.md | 重打包后 unzip 断言: env 模板≥3 文件、ci.yml 在包、泄漏门仍过 |
| 2 | CLAUDE.md 基线声明对齐实际(Boot 4.1.0/Java21/有 mvnw) | grep 断言无 3.2.5/Java17 残留;doc-freshness 门禁 |
| 3 | FileService 注释去虚构引用+catch 加 log.warn | mvn 编译+FileService 相关测试+sonar 该文件 0 问题 |
| 4 | pom.xml 两处过时注释修正 | 编译不受影响(注释级) |

## 六、合并后的开放队列(两审并一)

- **P0(1)**: 真机 UAT/E2E(07 P0-1,13 旅程待用户执行)
- **P1 下一批次(排序)**: ①幂等生产默认 strict ②取号并发方案 ③reminder 权限拆分 ④审批撤回+超时 ⑤状态机枚举白名单 ⑥告警通道 ⑦CVE 精筛+CI SBOM job ⑧恢复演练(需授权) ⑨性能基线(需目标并发) ⑩制品签名 ⑪模型版本/run 字段 ⑫仓内集成测试
- **P2**: 双层权限模型文档化/上传内容检测(按威胁模型)/500MB 上限/`.audit/` 结构/demo release 门控/多集团 currentGroup(裁决挂起)
