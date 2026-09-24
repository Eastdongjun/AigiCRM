# AUDIT — AIGICRM 本地代码审计体系

编辑器无关:全部逻辑在 `.audit/`,VS Code Tasks 只是入口之一;命令行在任何地方等价。

## 快速使用

```bash
python3 .audit/scripts/audit.py --mode quick      # 语法+lint+secret+小程序静态+Sonar
python3 .audit/scripts/audit.py --mode security   # SAST面+Secret+缺失工具清单
python3 .audit/scripts/audit.py --mode business   # Agent 语义审查注入(.audit/findings/agent-review-*.json)
python3 .audit/scripts/audit.py --mode changed    # Git 增量+影响扩展(分层邻居)
python3 .audit/scripts/audit.py --mode full       # quick+business+mvn test(长任务)
python3 .audit/scripts/audit.py --mode recheck    # 仅复测上次 OPEN 问题
python3 .audit/scripts/generate_report.py         # 报告集(.audit/reports/)
```

## 流水线

detect_stack(stack-profile.json)→ 扫描器按栈选择 → 统一问题模型(AUDIT-YYYYMMDD-NNNNN)
→ 去重(detectedBy 聚合)→ 分级(严重度×置信度)→ 门禁(thresholds.yaml)→ findings-<ts>.json → 报告+SARIF。

## 关键文件

- `config/audit.config.yaml` 扫描器开关与参数;`thresholds.yaml` 门禁;`exclusions.yaml` 排除(带理由)
- `rules/` 项目沉淀规则(security/miniprogram/business/api/java/database/docker/common/vue)
- `evidence/stack-profile.json` 技术栈档案(扫描器选择的唯一依据)
- `findings/` 统一问题+agent-review 注入;`reports/` 报告集;`baseline/` 基线快照

## 纪律(不可绕)

1. 缺失工具记 TOOL_NOT_INSTALLED+安装建议,不安装不中断(建议见 audit.config.yaml)
2. 文件宇宙=git 受控文件;排除必须给理由;lock/manifest/Dockerfile/CI 永不排除
3. 整改前 git 快照;禁止 reset --hard / clean -fd;修复走 Finding→根因→影响→修→测→复测
4. 禁止以关规则/suppress/删断言换取绿色;ACCEPTED 状态必须有人工裁决依据
5. 与既有体系的关系:dev-verify.sh(项目门禁)、verify-perm-matrix.sh(权限矩阵)、
   服务端 SonarQube(Eastdongjun_AigiCRM)被本编排器复用而非替代;中央证据目录
   (Agent/00-管理/项目执行/.../evidence/)仍为发布级证据的最终归宿,`.audit/reports/` 为项目内快照。

## 与 JEV 的分工(本地 System One 决策模型)

| 职责 | 归属 |
|---|---|
| 规则扫描(Sonar/ruff/secret/静态) | .audit 内建扫描器 |
| **分诊**:误报判断/处置路由/风险分级 | **JEV**(`jev_triage.py`,>0.7采纳 0.5~0.7复核 <0.5转人工) |
| 深度语义/业务/根因审查 | Agent(LLM),结论注入 agent-review-*.json |
| 自动整改+复测闭环 | Agent(按规范§15流程) |

```bash
python3 .audit/scripts/jev_triage.py     # 对最新 findings 做 JEV 三判断
```

注意:JEV scorer 为 CC-BY-NC(非商业)许可,分诊仅作开发期 advisory,不作为发布门禁依据。

## 基线

首跑基线见 `.audit/baseline/`(由首次 full/quick 结果归档);当前门禁:CRITICAL=0 HIGH=0 Secret=0。
