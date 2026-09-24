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
| [file-center.md](file-center.md) | 文件中心:ACL/配额/分享/公开下载 | FileController、BizFolderAcl |
| [score.md](score.md) | 积分模块:积分项/录入/排行(与激励分是两套体系) | ScoreController、ScoreService |
| [task.md](task.md) | 待办任务:可见性 IDOR 收口/状态机/工作台联动 | TaskController、TaskService |
| [finance.md](finance.md) | 财务记录:开票/回款与保护期联动 | BizInvoice/BizPayment、CustomerController |
| [system-admin.md](system-admin.md) | 系统管理:orgAdmin 边界/用户规则/角色纪律 | SysUserController、OrgAdminService |

## 关键规则数值速查(均为生产配置现值)

| 配置键 | 现值 | 含义 |
| --- | --- | --- |
| `customer.protection.days` | 360 | 保护期天数(到期日=基准日+此值+累计已批延期) |
| `customer.warning.days` | 30 | 临期预警窗口 |
| `customer.extend.days` | 360(默认,未见覆盖) | 审批通过一次延期增加的天数 |
| `customer.adjust.days` | 180 | (用途待补录:超期调整场景) |
| `invoice.overdue.days` | 365 | 最近开票距今超过此值 → 开票超期提醒 |
| `sea.claim.lock.days` | 3(默认,管理员可改) | 公海捞取审批锁超时自动释放天数(0=不启用) |
| 登录限流 | 5 次(IP+用户名)/ 20 次(IP) | 锁 15 分钟 |
| 默认存储配额 | 10 GB/人 | sys_user.storage_quota 默认值 |

## 已裁决口径(2026-09-24 批)

- 排行榜**允许暴露**未分享记录的聚合分(明细仍走分享制)。
- 到期提醒**维持每日轻量循环**,不加重跟进表单。
- 行政(05)等一切角色的权限均由**管理员后台勾选**(菜单/数据范围/模块/列表级),系统已支持,无代码级特殊放开。

## 待补录(未核实,勿引用)

- `customer.adjust.days`(180)的确切业务用途(超期调整场景,未逐行核实)。
- 回款(payment)是否有独立提醒/统计口径;开票金额与核心客户判定的联动细节(见 finance.md §5)。
