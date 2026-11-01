# PROJECT-CHARTER — AIGICRM.COM（P-PR01-01）

> 2026-11-01 · 95分提升战役 B23 补立（最小可行章程，按 `00-管理/项目执行/project-charter-template.md` 摘 ★ 字段；未知项标"待补"）。
> 证据源：projects.json + git log（898 commits，2026-07-31 首 / 2026-10-30 最近）。

## 基本信息

- **prj_id / kind**：P-PR01-01 / product（自研产品，已上线：02-已上线/01-AIGICRM）
- **数据分级**：L1-直接PII（客户 CRM 含真实姓名、手机号——见 `00-管理/项目执行/aigicrm-pii-inventory.md`）
- **Sonar**：Eastdongjun_AigiCRM（本地实例门禁，ADR-008 口径）

## 1. 立项论证 ★

自研 AI 驱动客户关系管理产品（AIGICRM.COM），面向客户资料与商机的数字化管理；已上线运行且持续迭代（898 commits），不做的代价：已上线产品停摆、真实客户数据资产无人承接。量化现状：待补。

## 4. 范围与排除项 ★

- **In Scope**：CRM 核心功能迭代（02-迭代源代码）；测试与质量（05-测试与质量）；部署运维（06-部署与运维）。
- **Out of Scope**：待补。
- **产品/客户/沙箱定位**：自研产品（product），正式分档 02-已上线。

## 5. 成功标准量化 ★

| # | 成功标准（量化句式） | 度量方式 | 达标时限 |
|---|---|---|---|
| 1 | Sonar 本地门禁通过（Eastdongjun_AigiCRM，0 新增 Blocker/Critical） | sonar-local-scan.sh | 每次迭代发布前 |
| 2 | 工作区审计 gate PASS、173+ 测试全绿 | audit.py --mode quick | 持续 |

## 其余 ★ 字段（§2 备选方案 / §3 组合重叠 / §6 退出策略 / §7-§12）

待补（本项目为已上线存量补章，历史立项论证以 git 首提交（2026-07-31）与 00-项目管理 目录为准）。
