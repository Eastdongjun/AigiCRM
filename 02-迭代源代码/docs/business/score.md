# 积分模块

> 来源锚点:`ScoreController`、`ScoreService`、`BizScoreItem/BizScoreEntry/BizScoreParticipant`。核实日期 2026-09-24。
>
> **先澄清一个易混点:积分(score)与激励(incentive)是两套独立体系**——积分=管理方定义积分项、按人按期录入的考核分;激励=四项激励分类下带佐证文件的业务记录分(见 [incentive.md](incentive.md))。两者数据源、权限码、页面均独立,不可混称。

## 1. 三层模型

| 层 | 实体 | 说明 |
| --- | --- | --- |
| 积分项 | `biz_score_item` | 管理方定义的可打分项(名称/分值),`score:manage` 维护 |
| 录入 | `biz_score_entry` | 按人按期(score:entry 批量录入)的实际得分 |
| 参与者 | `biz_score_participant` | 参与积分活动的人员集合 |

## 2. 权限与操作面

| 操作 | 权限码 |
| --- | --- |
| 积分项增删改 | `score:manage` |
| 批量录入(`/score/entries/batch`) | `score:entry`(录入页);含 Excel 导入导出(导出文件名 `{period}积分录入.xlsx`) |
| 网格/排行/统计查看 | `score:view` |

## 3. 可见性口径

- 录入候选人员=**集团内**积分可见集合(resolveModuleUsersWithinGroup(score),矩阵显式配置优先,未配置回退角色数据范围)。
- **积分排行为公司级全员可见**(2026-08-08 设计规则:查全部条目,不做数据范围过滤)——与激励个人排行同口径,与统计-客情排行(客户可见范围)不同。

## 4. 页面入口

- PC `/score/entry` 录入页(PC-only)、`/score/ranking` 排行;小程序排行中心积分排行区块+客户下钻。
