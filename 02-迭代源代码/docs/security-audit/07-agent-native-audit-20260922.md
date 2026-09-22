# AIGICRM Agent-Native 全生命周期审计报告(2026-09-22)

> **2026-09-22 晚修正注记**: 本报告 D13-02 曾将 FileService deleteStorage 的 catch 评为"带理由旁路(清理任务兜底)"——GPT 外审证实该"清理任务"不存在(虚构引用),该子项降为"失实注释,已修复";D01 漏列的基线文档冲突(CLAUDE.md 过时)与外发包内容完整性缺口同日修复。逐项裁定见 `08-gpt-agental-audit-20260922-adjudication.md`。

> 框架: 《Agent 全自动开发产品全生命周期审计体系》V2.0(20 域 168 控制项,用户提供)。
> 基线: origin/main@d8a6a239(其后 f14e1b25 测试口径 585/188/contracts134 亦被引用)。
> 方法: 总控+5 个并行只读审计代理分域取证(需求过程/工程数据/安全权限/产品测试/交付运维),证据均为文件行号或命令实测,无证据不 PASS。
> 纪律: 已修复项必须带提交证据;部分通过必须写明缺口;产品裁决(单集团部署即满足,多租户=纵深防御,68d62733)可作 RISK_ACCEPTED 依据。
> 状态口径: PASS 含"PASS(部分)";FAIL 含"FAIL(部分)";NC=NOT_CHECKED;NA=N/A;RA=RISK_ACCEPTED。

## 一、总体结论

**受控试点/内部 UAT 可以继续;正式发布前最低门槛尚有 1 项 P0 未闭环(真机 UAT/E2E),恢复演练与性能基线为发布硬门槛缺口。** 生产代码可信度基本面好:D13 假完成专项 7/7 全 PASS(零占位/零幻觉依赖/零文档伪完成),安全权限域(D09/D10)零 P0/P1,供应链自查诚实(初筛/精筛明确区分)。

| 维度 | 数字 |
|---|---|
| 实评控制项 | 156(另 D19 12 项 N/A:产品无 LLM/RAG/Agent 运行时,pom/package.json 零命中) |
| PASS(含部分) | ≈97 |
| RISK_ACCEPTED | ≈27 |
| FAIL(含部分) | ≈10 |
| NOT_CHECKED | ≈22 |
| 新发现 P0 / P1 / P2+ | 1 / 15 / 15 |

## 二、发布硬门禁对照(框架第 10 节)

| Gate | 状态 | 依据 |
|---|---|---|
| REQ Gate | ✅ | RTM 链 PR-xx→commit→evidence 完整;裁决记录在案(D01-03/04/07) |
| Business Gate | ⚠️ | 生命周期/不变量有;状态机白名单化缺(D03-02)、审批逆向/异常路径缺(D03-05/06) |
| Architecture Gate | ✅ | ArchUnit 守卫+ADR;19 个 Controller 技术债显式登记(D04) |
| Code Gate | ✅ | sonar OPEN=0 实测;占位/吞异常/幻觉依赖零命中(D05/D13) |
| Permission Gate | ✅ | 矩阵 20/20;65 端点对象级守卫;双集团夹具实测(D09) |
| Security Gate | ✅ | v-html/SSRF 零命中;日志脱敏实测;secret 三层防线(D10) |
| Test Gate | ⚠️ | 单测 585/契约 134/矩阵 20 扎实;**仓内零真实集成测试+Playwright 零用例**(D14-02/04) |
| Supply Chain Gate | ⚠️ | SBOM 278 组件入库;CVE 仅初筛未精筛;许可证零审查(D15) |
| Build Gate | ⚠️ | CI 5 job+本地 dev-verify 双层;制品无签名、action 未 SHA 固定(D15-06/08) |
| Deploy Gate | ✅ | Clean Room 当日实测 Flyway 48/48+smoke 10/10(D16-05) |
| Rollback Gate | ❌ | 回滚能力存在但**从未演练**(D16-07) |
| Recovery Gate | ❌ | 备份每日真实执行+SHA256;**恢复零实测**(D18-02,J13 待授权) |
| Evidence Gate | ✅ | completion-guard strict;472 份 handoff;声明均绑定实存证据(D13-07/D20-07) |
| P0 Gate | ❌ | 1 项 P0 开放(下节) |

## 三、P0(1 项,阻断正式发布)

| 编号 | 问题 | 事实与证据 | 关闭路径 |
|---|---|---|---|
| P0-1 | **真机 UAT 与 E2E 双未闭环** | `mobile-manual-qa-checklist.md` 33 处"待执行"0 处"已通过";`device-acceptance-13-journeys.md` 13 条全"待执行";`package.json:23` 声明 @playwright/test 但全仓 0 个 spec | 用户扫码执行 13 旅程清单(工具已备齐:env.local 指测试服+预览码步骤);Playwright 用例可后补为 P1 |

## 四、P1(15 项,原则上发布前关闭或正式风险接受)

**业务/测试**
1. 客户状态机无枚举白名单:`BizCustomer.status` 为 String,迁移靠散落 setStatus 字面量(CustomerService:358,592,606;SeaService:171-249),无 assertTransition(全库 0 命中);契约脚本只校验前端标签。(框架基线 P0,裁定 P1:迁移点全走服务层受控方法,非法迁移无运行时暴露实证)
2. 仓内零真实集成测试:无 testcontainers/H2,@SpringBootTest=0,test resources 自述"纯单元测试";DB/缓存真实路径依赖远程脚本(矩阵/smoke)补位。(框架基线 P0,裁定 P1:集成验证实际存在但形态为远程行为脚本)
3. 审批缺申请人撤回端点;驳回后重提路径未显式(ApprovalController 仅 submit/approve/reject/transfer)。
4. 审批异常路径无代码机制:超时(0 命中)/审批人停用/业务回写失败补偿(GPT §8.3 五项场景仅"待真实环境验证")。
5. 状态口径 31 处复制式 notIn("sea","archived") 非集中谓词,新增状态有漏改风险(B5 修补痕迹)。
6. 测试强度上限受 Builder 同体限制:无隐藏测试集/无 mutation/无属性测试(外部 GPT 两轮审计为唯一独立验证)。

**工程**
7. @Scheduled 无分布式锁(ShedLock 0 命中),单实例约束未成文;横向扩容将双跑(有 uk 兜底不脏数据,告警/指标翻倍)。
8. 声明式输入验证近乎缺失:全仓约束注解 9 处/@Valid 4 处/Assert 0,靠服务层手工判空,新增端点无防漏机制。
9. CustomerService 1617 行持续增长无截止日(ARCH-P2-013 仅入队列);列表/导入/转移/保护期/分享清混载。
10. 无请求级 traceId 贯穿(仅 500 类错误有 supportId;日志排障靠字符串匹配)。

**交付/运维**
11. 恢复演练零实测:备份链完整(当日产物+全量 SHA256)但恢复路径从未验证——备份未经恢复验证不视为有效灾备。
12. 告警通道未闭环:customer_expire_failed_total 等指标已暴露,但无 Prometheus 抓取器/告警规则/通知渠道;B1"汇总告警"现仅 log.error。
13. CVE 精筛缺位:bcprov-jdk15on 1.70(2022)/poi 4.1.2(2019,Excel 导入导出直接暴露面)/httpclient 4.5.13(EOL)三真候选未排期;Trivy 未装、CI 无 SBOM/CVE job。
14. 制品签名缺失+CI action tag 未 SHA 固定+Maven 无 lockfile(传递依赖未逐字节锁定)。
15. RPO/RTO 未定义未实测(隐含 RPO≈24h 未成文);性能域整体空白(容量模型/P50-99/chaos 均未做,需目标并发)。

**过程**
16. (并入 P1 清单)模型版本未记录:1152 个 run 无 model 字段,仅目录名区分 agent,事后无法追溯产物由哪个模型版本生成(D11-02,唯一 FAIL)。

## 五、P2/P3(15 项,版本整改计划)

1. 生产 CORS 含 localhost(docker-compose.prod.yml:63,SEC-P3-002 挂账未修)
2. 生产 root SSH 凭据本机明文可达(运维手册 3.3 sshpass 示例+~/.aigicrm-deploy.env;防线为纪律+钩子非技术隔离)
3. CSP unsafe-eval 三处残留(nginx-ssl.conf:61/79/109)
4. FileService 无扩展名放行与 javadoc 注释相反(FILE-P2-004 未修,风险低但易致误改)
5. 字段脱敏无默认种子规则(引擎 fail-closed 在,但 sys_permission_rule 零初始数据,脱敏靠上线手工配置)+客户导出动作无 securityAudit 留痕
6. admin MFA 未实施(强口令+限流+审计下可接受,观察项)
7. Token/API 成本无监控;自动修复循环无显式 maxIterations(仅 90s timeout)
8. git 提交未签名(%G?=N),人机共用单一作者,防抵赖靠中央 SHA256 证据链
9. `01-产品需求/` 空目录,原始用户输入未以原件入库(需求事实源为归并版 v4 总册)
10. v4 执行台账实施状态列未回填(RTM 完整性依赖 git 考古)
11. sys_audit_log append-only 无保留期/归档策略
12. 幂等无 DB 持久表(已知产品升级项)
13. `07-发布记录/` 空壳;break-glass 接管流程未成文
14. Redis KEYS 残留 evictAll 单点(PermissionCacheService:131,低频已注释)
15. N+1 与大类重构队列无截止日(TECH_DEBT 显式登记)

## 六、20 域状态分布

| 域 | 项 | PASS | RA | FAIL | NC | NA | 一句话结论 |
|---|---|---|---|---|---|---|---|
| D01 需求追溯 | 8 | 6 | 2 | 0 | 0 | 0 | RTM/裁决/基线锁定齐全;Assumption Log 与原始需求原件缺 |
| D02 产品完整性 | 7 | 5 | 0 | 2 | 0 | 0 | 功能地图/可配置性/状态覆盖好;审批撤回缺、UAT 未执行 |
| D03 业务状态机 | 8 | 4 | 0 | 4 | 0 | 0 | 生命周期/不变量在;白名单化/逆向/异常路径三缺口 |
| D04 架构 | 8 | 7 | 1 | 0 | 0 | 0 | ArchUnit 守卫+权限引擎集中;19 Controller 技术债登记 |
| D05 代码质量 | 9 | 6 | 2 | 0 | 1 | 0 | sonar 0+无吞异常;大类/魔法值 RA,资源管理未深查 |
| D06 契约一致性 | 7 | 5 | 2 | 0 | 0 | 0 | 212 端点+134 契约;输入验证与 traceId 两 RA |
| D07 数据库 | 9 | 6 | 2 | 0 | 0 | 1 | 唯一键/触发器硬兜底+Clean Room 重放;N+1/归档策略 RA |
| D08 并发幂等 | 6 | 2 | 2 | 0 | 0 | 2 | 幂等配置化+uk 兜底;ShedLock 缺;无 MQ 故两 NA |
| D09 权限隔离 | 8 | 7 | 1 | 0 | 0 | 0 | 认证/授权/范围/收敛全 PASS 带行为证据;字段脱敏默认规则未实证 |
| D10 AppSec | 9 | 8 | 1 | 0 | 0 | 0 | 注入/Web/文件/Secret/日志全过;导出销毁留痕 PARTIAL |
| D11 Agent 过程 | 8 | 6 | 1 | 1 | 0 | 0 | 变更边界/过程日志/接管性全过;模型版本未记录(FAIL) |
| D12 工具链环境 | 9 | 5 | 0 | 1 | 3 | 0 | Mimosa 拦截实证有效;出站/注入面 NC;生产 SSH 本机可达(FAIL,P2) |
| D13 假完成专项 | 7 | 7 | 0 | 0 | 0 | 0 | **全 PASS**:零占位/零幻觉/零迎合/零伪完成,抽验全部实存 |
| D14 测试体系 | 9 | 4 | 0 | 2 | 3 | 0 | 单测/契约/负向扎实;集成/E2E 两 FAIL;隐藏/mutation/property 三 NC |
| D15 供应链 | 8 | 2 | 4 | 0 | 2 | 0 | SBOM 入库+初筛诚实;精筛/许可证/签名/Agent生态清单缺 |
| D16 CI-CD | 9 | 5 | 3 | 0 | 1 | 0 | CI 5 job+Clean Room 实测;回滚演练 NC;发布审批单人 RA |
| D17 性能可观测 | 9 | 3 | 2 | 0 | 4 | 0 | fail-closed 语义+Micrometer 在;容量/基线/告警消费/chaos 四 NC |
| D18 备份灾备 | 8 | 4 | 3 | 0 | 2 | 0 | 每日备份真实+哈希齐;恢复演练/RPO-RTO 两 NC;单点有裁决 RA |
| D19 LLM 专项 | 12 | 0 | 0 | 0 | 0 | 12 | 产品无 LLM/RAG/Agent 运行时,全 NA |
| D20 治理验收 | 10 | 6 | 3 | 0 | 1 | 0 | 证据驱动 strict+P0 阻断曾闭环;成本监控 NC/未签名 RA |

## 七、亮点(区别于典型 Agent 开发项目的实证)

1. **D13 假完成 7/7**:生产三端(TOCAP 后端/前端/小程序)零 TODO/MOCK/STUB 命中;11 处 catch(ignored) 逐一核验均为带理由的非关键旁路;4 个不常见依赖抽验真实落盘;抽查完成声明全部绑定实存证据文件。
2. **治理链实际闭环过**:健康诊断抓 P0(文件库 ReferenceError)→修复→CI 强制→放行;Agent 自创字段被识别并回滚(b7fce4ea);页面级错误结论被纠错并记教训(b1f09f8e)。
3. **安全权限域零 P0/P1**:65 端点对象级守卫、双集团夹具、导出字节级断言、fail-closed 三处 503、改密即撤销、perm_version 即时收敛——全部行为级实测。
4. **备份当日产物+全量 SHA256+15 天保留脚本化**(D18-01),知识传承密度高于同规模项目常规(D18-07)。

## 八、整改建议排序(最小代价→最大风险收敛)

1. **本周**:用户扫码执行 13 旅程清单(关 P0-1,同时覆盖 D02-07/D14-04 主体);CORS 去 localhost;prod compose 注释单实例约束;导出动作补 securityAudit.record。
2. **下一批次**:客户状态枚举+assertTransition 白名单(关 D03-02,顺带收敛 31 处散落口径);审批撤回端点+超时机制;告警通道(Prometheus 抓取+一条 failed_total 告警规则即可闭环 D17-08)。
3. **需用户授权**:恢复演练(J13,在 Clean Room 用 20260922 dump 实测恢复+smoke,一并关 D16-07/D18-02);性能基线压测(需目标并发数字)。
4. **持续**:CVE 精筛(装 Trivy 挂 CI)/CustomerService 拆分/集成测试引入 testcontainers/run.json 补 model 字段。

## 九、审计留痕

- 5 份分域取证原始输出存会话档案;本报告为总控裁定,裁定项(如 D03-02 框架基线 P0→裁定 P1)均已写明理由。
- 可复验入口: `sonar list issues -p Eastdongjun_AigiCRM --statuses OPEN`(total:0)、`bash scripts/verify-perm-matrix.sh`(20/20)、`deploy/cleanroom/smoke.sh`(10/10)、`grep -rn "TODO\|FIXME\|STUB\|MOCK" aigicrm-backend/src/main/java`(0 命中)。
