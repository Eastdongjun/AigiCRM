# AIGICRM 统一功能清单(权威)

> 本清单是 PC 与微信小程序功能对齐的唯一工作真源(source of truth)。PC 与小程序必须共用同一套后端、数据库、账号、角色、岗位、权限与数据范围模型,允许差异的只有 UI 布局。
>
> 本清单回答"**有哪些功能、什么权限**"(清单层);业务流程/规则数值/边界分支(如保护期公式、审批流解析、分享制口径、提醒去重)在 [business/](business/README.md)(流程层),两层互链、交付时同步更新。
>
> **版本说明(2026-09-24)**:本文件由 09-15 英文版中文化重写而来,内容逐行对译;相对英文版新增或修正的位置以 ⟦补录 09-24⟧ / ⟦修正 09-24⟧ 标注,未标注行与英文版语义一致(英文版在 git 历史中)。

## 1. Feature Key 规则

- Feature Key 格式:`module.action`。
- 页面权限控制页面入口是否可见;按钮权限控制操作是否可见。
- 后端权限与数据范围校验是最终安全边界,前端显隐只是体验层。
- 小程序页面必须与 PC 页面映射到同一组 feature key。
- 首发只在 PC 提供的功能,也必须登记,移动端状态记为 `PC-only`。

## 2. 模块清单

| Feature Key | 模块 | PC 路由/页面 | 小程序页面 | 权限 | 数据范围 | 操作类型 | 移动端状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `auth.login` | 认证 | `/login` | `pages/login/index` | 公开 | 当前账号 | 登录 | 已实现 |
| `auth.passwordRecovery` | 认证 | PC 登录找回/人工支持流程 | `pages/login/forgot/index` | 公开 | 账号身份核验 | 重置本人密码 | 已实现 |
| `auth.currentUser` | 认证 | 布局/全局 store | 所有页面 | 登录即可 | 当前账号/集团 | 读取 | 已实现 |
| `auth.profile` | 认证/个人中心 | PC 用户菜单/个人页 | `pages/profile/index`、`pages/profile/password/index`、`pages/profile/scope/index` | 登录即可 | 当前账号/集团 | 读取/改本人密码/查看范围 | 已实现 |
| `auth.feedback` | 认证/个人中心 | PC 支持/联系兜底入口 | `pages/profile/index` 与 `pages/profile/feedback/index` | 登录即可 | 当前账号本地草稿 | 本地反馈草稿 | 已实现 |
| `auth.wechatBinding` | 认证/个人中心 | 预留微信账号绑定 | `pages/profile/index` 与 `pages/profile/wechat/index` | 登录即可 | 当前账号 | 仅绑定状态说明;OAuth/openid 绑定未接 | 已实现 |
| `auth.legalDocs` | 认证/个人中心 | 公开/法务文档 | `pages/profile/about/index`、`pages/profile/agreement/index`、`pages/profile/privacy/index` | 公开/登录 | 静态产品文档 | 读取 | 已实现 |
| `dashboard.view` | 工作台 | `/` | `pages/home/index` | `dashboard:view` 或 `statistics:view` | 当前用户范围 | 读取/统计 | 已实现 |
| `app.launcher` | 小程序外壳 | PC 左侧菜单/应用导航 | `pages/app/index` | 登录即可 | 当前账号权限 | 导航至已授权模块 | 已实现 |
| `customer.list` | 客户 | `/customer` | `pages/customer/index` | `customer:view` | 统一客户数据范围 | 读取 | 已实现 |
| `customer.detail` | 客户 | `/customer/detail/:id` | `pages/customer/detail/index` | `customer:view` | 负责人/分享/组织范围 | 读取 | 已实现 |
| `customer.add` | 客户 | `/customer/add` | `pages/customer/form/index` | `customer:add` | 当前用户/集团 | 创建 | 已实现 |
| `customer.edit` | 客户 | `/customer/edit/:id`、详情编辑 | `pages/customer/form/index?id=:id` | `customer:edit` | 可编辑客户范围 | 更新 | 已实现 |
| `customer.delete` | 客户 | 客户表格/详情删除 | `pages/customer/detail/index` 危险区删除 | `customer:delete` | 可编辑客户范围 | 删除 | 已实现 |
| `customer.release` | 客户/公海 | 客户表格/详情释放 | `pages/customer/detail/index` | `customer:edit` | 可编辑客户范围 | 工作流/更新 | 已实现 |
| `customer.transfer` | 客户 | 客户详情「转移客户」按钮,提交 transfer 审批 | 审批列表/详情可展示转移类单据;发起入口仅在 PC | 入口 `customer:edit` + 提交端 `approval:view`;执行侧校验接收人在册启用+同集团(admin 可跨集团) | 可编辑客户范围+同集团接收人 | 工作流/创建 | 发起 `PC-only`(审批展示已实现) ⟦补录 09-24⟧ |
| `customer.share` | 客户 | 客户详情分享 | `pages/customer/share/index` | 读 `customer:view`;新增/撤销 `customer:edit`(遗留码 `customer:share` 不作运行时门禁) | 负责人/可编辑范围 | 授予/读取分享 | 已实现 |
| `customer.finance` | 客户 | 客户详情财务页签 | `pages/customer/detail/index`、`pages/customer/finance/index`、`pages/customer/finance/form/index` | 读 `customer:view`+`finance:view`;增删 `finance:manage` 或(`customer:edit`+`finance:view`) ⟦修正 09-24:原文为"增删仅 customer:edit",V2026.09.23.4 起 finance:manage 为专职授权码⟧ | 同客户可见/可编辑范围 | 财务记录读取/创建/删除 | 已实现 |
| `customer.contact` | 客户 | 客户详情联系人页签 | `pages/customer/detail/index`、`pages/customer/contact/index`、`pages/customer/contact/form/index`、`pages/customer/contact/detail/index` | 读 `customer:view`+`contact:view`;增改删 `customer:edit` 或 `contact:manage` ⟦修正 09-24:原文为"增改删仅 customer:edit",V2026.09.23.4 起 contact:manage 为专职授权码⟧ | 同客户可见/可编辑范围 | 联系人读取/创建/更新/删除 | 已实现 |
| `customer.log` | 客户 | 客户详情跟进记录页签 | `pages/customer/detail/index` 与 `pages/customer/log/index` | 读 `customer:view`;创建 `customer:edit` | 同客户可见/可编辑范围 | 跟进记录读取/创建 | 已实现 |
| `customer.timeline` | 客户 | 客户详情完整时间线 | `pages/customer/detail/index` 与 `pages/customer/timeline/index` | `customer:view` | 同客户可见范围 | 读取时间线 | 已实现 |
| `customer.indicator` | 客户 | 客户详情核心指标页签 | `pages/customer/detail/index` 与 `pages/customer/indicator/index` | 读 `customer:view`;保存 `customer:edit` | 同客户可见/可编辑范围 | 六项指标读取/更新 | 已实现 |
| `customer.relationship` | 客户 | 客户详情客情评分 | `pages/customer/detail/index` | `customer:edit` | 同客户可编辑范围 | 更新客情评分 | 已实现 |
| `customer.filter` | 客户 | 客户表格高级筛选 | `pages/customer/filter/index` | `customer:view` | 统一客户数据范围 | 筛选/搜索 | 已实现 |
| `customer.import` | 客户 | 客户工具栏导入 | 默认 `PC-only` | `customer:import` | 当前集团/导入范围 | 导入 | `PC-only` |
| `customer.export` | 客户 | 客户工具栏导出 | 默认 `PC-only` | `customer:export` | 当前查询数据范围 | 导出 | `PC-only` |
| `task.view` | 待办任务 | `/task` 任务页;工作台任务统计角标 | `pages/home/index` 工作台任务区(`/api/task/list`、`/api/task/statistics`) | `task:view` | 本人任务(管理范围见 task.manage) | 读取/统计 | 已实现 ⟦补录 09-24:英文版整模块缺失,模块 09-19 上线且为 V2026.09.23.1 权限事故主角⟧ |
| `task.manage` | 待办任务 | 任务页创建/完成/取消/转交 | 管理操作仅 PC | `task:manage` | 任务指派/管理范围 | 创建/完成/取消/编辑转交 | 管理操作 `PC-only` ⟦补录 09-24⟧ |
| `file.list` | 文件 | `/file` | `pages/files/index` ⟦更正 10-07:旧 file 页及重定向壳随 1b3c865b 死页面清理删除,无存量导航可达⟧ | `file:view` | 文件夹 ACL+负责人/分享 | 读取 | 已实现 |
| `file.download` | 文件 | 文件列表/操作 | `pages/files/detail/index` | `file:view`+文件夹下载权 | 文件夹 ACL | 下载 | 已实现 |
| `file.upload` | 文件 | 文件工具栏/弹窗 | `pages/files/index` | `file:upload`+文件夹 ACL | 文件夹 ACL | 上传 | 已实现 |
| `file.share` | 文件 | 文件行/弹窗分享 | `pages/files/index` 分享操作 | `file:share`+文件夹 ACL | 文件夹 ACL | 授予/分享 | 已实现 |
| `file.replace` | 文件 | 文件行替换 | `pages/files/detail/index` 操作菜单 | `file:edit`+文件夹 ACL | 文件夹 ACL | 更新 | 已实现 |
| `file.delete` | 文件 | 文件行删除 | `pages/files/index` 删除操作 | `file:delete`+文件夹 ACL | 文件夹 ACL | 删除 | 已实现 |
| `file.folderManage` | 文件 | 文件夹面板管理 | `pages/files/index` 与 `pages/files/folder-form/index` | `file:folder:manage`+文件夹 ACL | 文件夹 ACL | 创建/更新/删除 | 已实现 |
| `file.aclView` | 文件 | 文件夹 ACL 弹窗只读 | `pages/files/index` 只读 ACL 弹窗 | `file:acl:manage` | 文件夹管理 ACL | 读取权限 | 已实现 |
| `file.aclManage` | 文件 | 文件夹 ACL 弹窗授权 | 默认 `PC-only` | `file:acl:manage` | 文件夹管理 ACL | 授予/撤销 | `PC-only` |
| `incentive.index` | 激励(四项激励) | `/incentive` | `pages/incentive/index`、`pages/incentive/detail/index`、`pages/incentive/upload/index` | 读 `incentive:view`;上传 `incentive:add` | 激励分类/用户授权范围 | 读取/创建 | 已实现 |
| `incentive.legacyCasesAlias` | 激励 | 旧激励链接 | `pages/incentive/index` ⟦更正 10-07:cases 重定向壳随 1b3c865b 删除,兼容由历史路由表外无引用保证⟧ | `incentive:view` | 激励分类/用户授权范围 | 兼容导航 | 已实现 |
| `incentive.search` | 激励 | `/incentive/search` | `pages/incentive/index` | `incentive:view` | 激励分享制(本人全部+他人已分享) | 读取 | 已实现 |
| `incentive.markRead` | 激励 | 查询详情/已读 | `pages/incentive/index` | `incentive:view` | 可见激励记录 | 更新已读状态 | 已实现 |
| `incentive.readStats` | 激励 | 查询页已读统计面板 | `pages/incentive/index` | `incentive:read-stats` | 当前集团/月份 | 统计 | 已实现 |
| `incentive.readRanking` | 激励 | 系统设置→本月激励查询阅读排行榜页 | `pages/incentive/index` 阅读排行区块 | `incentive:read-ranking:view`(默认仅 admin,其他角色由管理员勾选) | 当前集团/月份 | 统计 | 已实现(权限内可见) ⟦补录 09-24:09-13 上线,英文版缺失⟧ |
| `incentive.upload` | 激励 | `/incentive` 上传弹窗 | `pages/incentive/upload/index` 佐证上传 | `incentive:add` | 当前用户/客户范围 | 创建/上传 | 已实现 |
| `incentive.recordEdit` | 激励 | 记录表格追加编辑 | `pages/incentive/detail/index`(含编辑/追加文件入口) | 本人或授权 `canEdit` | 激励授权范围 | 更新 | 已实现 |
| `incentive.recordDelete` | 激励 | 记录表格删除 | `pages/incentive/index` 最新记录操作与 `pages/incentive/detail/index` 删除 | 本人或授权 `canDelete` | 激励授权范围 | 删除 | 已实现 |
| `incentive.stats` | 激励 | `/incentive/stats`(激励统计表/客户排行 Tab) | `pages/statistics/index` 与 `pages/ranking/index` | `incentive:view` | 激励授权/数据范围 | 统计 | 已实现 |
| `incentive.personRank` | 激励 | `/incentive/stats` 个人排行 Tab;导出按钮 | `pages/ranking/index` 排行中心·激励个人排行区块 | 数据 `incentive:view`(公司级聚合,含未分享);导出 `incentive:person-rank:export`(默认仅 admin,可勾选) | 公司级(按人聚合总积分降序) | 统计/导出 | 已实现 ⟦补录 09-24:09-23 上线,英文版缺失⟧ |
| `reminder.list` | 提醒 | `/reminder` | `pages/reminder/index` | `reminder:view` | 当前用户提醒 | 读取 | 已实现 |
| `reminder.notification` | 提醒/通知 | 提醒通知中心/详情 | `pages/reminder/index` ⟦更正 10-07:notification 两页随 1b3c865b 死页面清理删除,提醒由 reminder 模块+tabBar 角标承载⟧ | `reminder:view` | 当前用户提醒/客户可见范围 | 读取/处理通知 | 已实现 |
| `reminder.expiring` | 提醒 | `/reminder` 到期客户 | `pages/reminder/index` 到期客户 | `reminder:view` | 统一客户范围 | 读取 | 已实现 |
| `reminder.process` | 提醒 | 提醒行处理 | `pages/reminder/index` | `reminder:view` | 当前用户提醒 | 更新 | 已实现 |
| `approval.list` | 审批 | `/approval` | `pages/approval/index` | `approval:view` | 申请人/审批人/组织范围 | 读取 | 已实现 |
| `approval.detail` | 审批 | 审批行详情 | `pages/approval/index` 与 `pages/approval/detail/index` | `approval:view` | 申请人/审批人/组织范围 | 读取/时间线 | 已实现 |
| `approval.approve` | 审批 | 审批行/工作台批准 | `pages/approval/index` 与 `pages/approval/detail/index` | `approval:approve` | 当前审批节点 | 工作流/更新 | 已实现 |
| `approval.reject` | 审批 | 审批行/工作台驳回 | `pages/approval/index` 与 `pages/approval/detail/index` | `approval:reject` | 当前审批节点 | 工作流/更新 | 已实现 |
| `approval.submit` | 审批 | 客户/公海/提醒等流程,`/api/approval/submit` | `pages/customer/detail/index` 与 `pages/sea/index` | 提交端 `approval:view`+来源操作权限 | 来源业务范围 | 工作流/创建 | 已实现 |
| `sea.list` | 公海 | `/sea` | `pages/sea/index` | `sea:view` | 可见池/数据范围 | 读取 | 已实现 |
| `sea.claim` | 公海 | 公海行捞取 | `pages/sea/index` 与 `pages/sea/claim-detail/index` | `sea:claim`+行 `canClaim` | 池/组织/用户规则 | 工作流/创建 | 已实现 |
| `sea.edit` | 公海 | 公海行编辑/客户表单 | `pages/sea/index` 至 `pages/customer/form/index?id=:id&from=sea` | `customer:edit`+行 `canEdit` | 池/客户编辑范围 | 更新 | 已实现 |
| `sea.import` | 公海 | 公海导入弹窗 | 默认 `PC-only` | `sea:import` | 池导入范围 | 导入 | `PC-only` |
| `sea.delete` | 公海 | 公海行删除,`/api/sea/pool/{id}` | `pages/sea/index` 行删除 | `sea:pool:delete`+行 `canDelete` 且 `available` 状态 | 池删除范围 | 删除 | 已实现 |
| `sea.config` | 公海 | 系统配置/公海池管理 | 默认 `PC-only` | `sea:config:view/edit` | 配置范围 | 配置增删改查 | `PC-only` |
| `statistics.dashboard` | 统计 | `/statistics`、`/indicator` | PC 仪表盘;`pages/home/index`、`pages/statistics/index`、`pages/statistics/detail/index`、`pages/indicator/index` | `statistics:view` 或适用的 `dashboard:view` | 统一客户数据范围 | 统计 | 已实现 |
| `statistics.customerRankings` | 统计 | `/ranking/customer`(考核与分析→客户排行:四项激励/客情/六项指标三类排行) | `pages/ranking/index` 排行中心 | `statistics:view` | 四项激励聚合=激励分享制;指标/客情=客户可见范围(口径差异系 G2 安全设计,待产品裁决是否统一) | 统计 | 已实现 ⟦补录 09-24:英文版未单列;口径注释反映 09-24 修复⟧ |
| `score.entry` | 积分 | `/score/entry` | 默认 `PC-only` | 页面 `score:entry`,**批量录入 `/score/entries/batch` 也是 `score:entry`**(积分项 CRUD 才是 `score:manage`) ⟦修正 09-24:原文"写 score:manage"为英文原文笔误,与代码不符;GPT 二轮审查 P1-013 抓出⟧ | 集团内积分可见集合 | 批量录入/创建更新 | `PC-only` |
| `score.ranking` | 积分 | `/score/ranking` | `pages/ranking/index`、`pages/ranking/detail/index`、`pages/customer/detail/index` 客户下钻 | `score:view` | 可见用户 | 读取/统计 | 已实现 |
| `system.user` | 系统 | `/system/user` | `PC-only` | `system:user:*` | 组织管理员/用户管理范围 | 账号增删改查 | `PC-only` |
| `system.role` | 系统 | `/system/role` | `PC-only` | `system:role:*` | 当前集团/全局配置 | 角色增删改查/授权 | `PC-only` |
| `system.position` | 系统 | `/system/position` | `PC-only` | `system:position:*` | 当前集团/组织配置 | 岗位增删改查/规则绑定 | `PC-only` |
| `system.dept` | 系统 | `/system/org` | `PC-only` | `system:dept:view/edit` | 当前集团/组织树 | 组织增删改查/管理员绑定 | `PC-only` |
| `system.config` | 系统 | `/system/config` | `PC-only` | `system:config:view/edit` | 当前集团/系统配置 | 配置增删改查 | `PC-only` |
| `system.log` | 系统 | `/system/log` | `PC-only` | `system:log:view` | 当前管理范围 | 审计读取 | `PC-only` |
| `system.rule` | 系统 | `/system/rule` | `PC-only` | `system:rule:*` | 当前集团/系统配置 | 规则增删改查 | `PC-only` |
| `system.group` | 系统 | `/system/group` | `PC-only` | `system:group:*` | 超管集团范围 | 集团增删改查 | `PC-only` |

## 3. 工程使用规则

- 小程序开发每次只认领一个待办 feature key。
- 每个 feature key 在 UI 完成前必须先过后端权限验证。
- 每个小程序页面必须使用 `/api/auth/info` 下发的权限集合,禁止自建独立角色规则。
- 新增后端端点后必须重跑 `scripts/gen-unified-feature-audit.py` 更新对账输出。
- 新增权限码必须先经迁移/数据库配置登记,再重新生成权限字典。
