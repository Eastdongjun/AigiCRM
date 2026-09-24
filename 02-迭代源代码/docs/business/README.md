# 业务规则与流程(需求级)

> 本目录是 AIGICRM 业务流程与细粒度规则的唯一居所:功能有哪些在 `../unified-feature-manifest.md`(清单层),**业务怎么流转、规则数值是多少、边界分支怎么走**在本目录(流程层)。两层互链,功能交付时同步更新对应文档。
>
> 建立于 2026-09-24。首批五份文档内容以当日代码逐行核实为据(来源锚点标注在各文档),未经核实的领域列在文末"待补录",不做臆测。

## 目录

| 文档 | 领域 | 主要来源锚点 |
| --- | --- | --- |
| [customer-lifecycle.md](customer-lifecycle.md) | 客户生命周期:创建/保护期/到期/公海/转移/分享 | CustomerExpireTask、ApprovalService、CustomerService |
| [approval.md](approval.md) | 审批工作流:类型/节点/执行动作 | ApprovalService、ApprovalConfigService |
| [incentive.md](incentive.md) | 激励与分享制:可见性/分值/排行 | IncentiveService |
| [reminder.md](reminder.md) | 提醒体系:六类提醒/物化/跟进 | ReminderService、CustomerExpireTask |
| [visibility.md](visibility.md) | 数据范围与集团归属:五档范围/分享制边界 | OrgScopeResolver、GroupScopeUtil、ScopeService |
| [security-auth.md](security-auth.md) | 认证与会话:登录限流/JWT/集团切换/找回密码 | AuthService、SecurityConfig |

## 关键规则数值速查(均为生产配置现值)

| 配置键 | 现值 | 含义 |
| --- | --- | --- |
| `customer.protection.days` | 360 | 保护期天数(到期日=基准日+此值+累计已批延期) |
| `customer.warning.days` | 30 | 临期预警窗口 |
| `customer.extend.days` | 360(默认,未见覆盖) | 审批通过一次延期增加的天数 |
| `customer.adjust.days` | 180 | (用途待补录:超期调整场景) |
| `invoice.overdue.days` | 365 | 最近开票距今超过此值 → 开票超期提醒 |
| 登录限流 | 5 次(IP+用户名)/ 20 次(IP) | 锁 15 分钟 |
| 默认存储配额 | 10 GB/人 | sys_user.storage_quota 默认值 |

## 待补录(未核实,勿引用)

以下领域尚未成文,需要专项核实后再补,当前以代码为准:

- 文件中心:文件夹 ACL 模型、配额扣减时机、公开下载链接规则
- 积分模块:积分录入口径、与激励分的关系、排行算法
- 待办任务:任务指派/转交语义、与工作台角标的关系
- 财务记录:增删口径、与开票日的联动
- 系统管理五页(用户/角色/岗位/组织/配置)的管理边界与 org-admin 语义
