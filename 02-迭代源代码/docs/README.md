# AIGICRM 知识资产(活文档)

> 本目录是与代码同源维护的**活文档**:数据库/权限码/API 由脚本自动生成,业务与架构文档随功能包交付同步更新。
> **规则**:任何功能包交付时,若涉及数据/接口/权限/业务规则,必须同步更新对应文档(交付门禁检查)。

## 目录

| 目录 | 内容 | 生成方式 |
|---|---|---|
| architecture/ | 架构设计、模块说明、数据流 | 人工维护(随交付更新) |
| database/ | 数据字典(55表)、ER 关系 | **脚本自动生成** scripts/gen-data-dictionary.sh |
| permission/ | 权限设计、权限码字典、角色矩阵 | 设计人工 + 字典脚本生成 |
| api/ | API 清单(路径/方法/权限码) | **脚本自动生成** scripts/gen-api-list.sh |
| business/ | 业务规则(积分/保护期/审批/公海/激励) | 人工维护(需求确认时录入) |
| ops/ | 运维手册(NAS/备份/回滚/部署) | 人工维护 |

## 测试体系（2026-11-22 用户确认转正）

| 资产 | 位置 | 内容 |
|---|---|---|
| **测试机制（执行入口）** | `docs/test-mechanism-20261122.md` | 方法·内容·结果三合一绑定：场景→命令路由表、矩阵填表分工、问题闭环状态机、域闭环判据、与领域层关系 |
| 严格测试门禁模板 v1 | `docs/test-gate-template-20261122.md` | G0–G7 分层门禁 + H1–H10 硬规则；收口既有 gate.sh/quality-gate.sh/契约脚本，补齐 G6 内容级核验与 G7 上线后门禁；待建 A/B/C 见其 §5 |
| 全系统测试矩阵模板 v1 | `../AIGICRM全系统测试矩阵模板-20261122.xlsx`（14 sheet） | 01 七档定义/02 组织树fixture/03 测试账号/04 权限测试记录主表/05 问题闭环/06–13 审批链·消息·日志·数据库·表结构·安全·性能·兼容；核心断言=配置态 vs 生效态 ID 集合级对账 |
| 权限矩阵自动化 | `scripts/verify-perm-matrix.sh` 第 8 段 | 七档 ID 集合 diff 全量回归（`AIGI_MATRIX=1` 挂 gate.sh 可选档）；实测 28/0 全绿 |
| （方法论单一源在工程领域层） | `东来/Agent/05-领域/01-工程/06-测试/01-技能/test-system/` | 跨项目通用方法论+可参数化工具（verify-kernel-compat / verify-deployed-content）；中央登记 GATE-ENG-TEST-001/002/003 |

> 使用规则：代码交付走门禁模板 G0–G7；权限/数据范围改动必跑 `verify-perm-matrix.sh`；其余域按迭代填矩阵对应 sheet，问题一律开 05 表单闭环。

## 阶段衔接规则(强制)

```
INTAKE        → 明确本次影响的文档类别
REQUIREMENTS  → 需求确认单(用户确认后才可推进)
PLAN          → 列出要更新的文档清单
IMPLEMENT     → 代码 + 注释(为什么级)
VERIFY        → smoke test 通过
DELIVER       → 文档同步更新 + 残余风险披露 + 门禁通过
```

任何阶段缺失衔接产出 = 交付门禁 FAIL。

## 交付机制(强制)

| 机制 | 脚本 | 触发时机 |
|---|---|---|
| 交付门禁 | scripts/verify-delivery.sh | 交付前(10项:git/API/配置/存储/备份) |
| 冒烟测试 | scripts/smoke-test.sh | 交付前(登录/10核心API/权限隔离) |
| 阶段衔接检查 | scripts/pre-delivery-check.sh | 交付前(知识资产+需求确认+冒烟+门禁) |
| 数据字典 | scripts/gen-data-dictionary.sh | 数据库结构变更后 |
| 权限字典 | scripts/gen-permission-dict.sh | 权限变更后 |
| API 清单 | scripts/gen-api.py | 接口变更后 |
| 部署 | scripts/deploy-*.sh | 发布时 |
| 回滚 | scripts/rollback.sh | 故障时 |

**交付标准**: pre-delivery-check.sh 全 PASS = 可以交付;任一 FAIL = 补齐后重跑。
**需求标准**: 功能包 01-requirements.md 必须含"需求确认=是"(用户确认)才可进入 PLAN_READY;需求变更必须回写并重新确认。
