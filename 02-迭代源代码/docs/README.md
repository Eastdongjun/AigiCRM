# YCMN 知识资产(活文档)

> 本目录是与代码同源维护的**活文档**:数据库/权限码/API 由脚本自动生成,业务与架构文档随功能包交付同步更新。
> **规则**:任何功能包交付时,若涉及数据/接口/权限/业务规则,必须同步更新对应文档(交付门禁检查)。

## 目录

| 目录 | 内容 | 生成方式 |
|---|---|---|
| architecture/ | 架构设计、模块说明、数据流 | 人工维护(随交付更新) |
| database/ | 数据字典(50表)、ER 关系 | **脚本自动生成** scripts/gen-data-dictionary.sh |
| permission/ | 权限设计、权限码字典、角色矩阵 | 设计人工 + 字典脚本生成 |
| api/ | API 清单(路径/方法/权限码) | **脚本自动生成** scripts/gen-api-list.sh |
| business/ | 业务规则(积分/保护期/审批/公海/激励) | 人工维护(需求确认时录入) |
| ops/ | 运维手册(NAS/备份/回滚/部署) | 人工维护 |

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
