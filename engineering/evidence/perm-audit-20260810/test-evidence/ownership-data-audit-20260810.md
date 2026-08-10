# 生产数据归属字段完备性核对(2026-08-10 实测)

| 维度 | 字段 | 数据 | 状态 |
|---|---|---|---|
| 客户归属 | biz_customer.owner_id | 285/285 有 | ✅ |
| | biz_customer.region_id | 285/285 有 | ✅ |
| | biz_customer.office_id | 285/285 有 | ✅ |
| | **biz_customer.dept_id** | **285/285 NULL** | ❌ 死字段(0805 加列未回填) |
| | biz_customer.group_id | 285/285 NULL | ❌ 死字段(0809 修的是文本列 group_name) |
| 组织树 | sys_dept | 10 节点: 1 公司+3 大区+3 办事处+3 部门 | ✅ 层级完整 |
| | sys_dept.leader_id | 10/10 有负责人 | ✅ |
| 用户归属 | sys_user.dept_id | 30/30 有 | ✅ |
| | sys_user.manager_id | **20/30 NULL** | ⚠️ 2/3 缺(客户自行维护) |
| 授权配置 | sys_role_org | 1 行 | ⚠️ 角色级授权几乎未配 |
| | sys_user_org | 6 行(2 行政) | ✅ |
| | role_perm_scope | 1 行 | 新模型生产未启用(刚建表) |

## 全模块归属与可见性核对(生产,2026-08-10 17:55)

| 模块 | 归属字段 | 可见性模型 | 生产数据 | 状态 |
|---|---|---|---|---|
| 激励 | biz_incentive_record.user_id + shared 开关 | 分享制(本人+分享+专项授权;统计公司级) | 53 条,user 全有;28 分享/25 未分享 | ✅ |
| 文件 | biz_file.uploader_id + folder_id | 文件夹 ACL(角色 6 权限)+直属上级+公共全员 | 257 文件 uploader 全有;9 文件夹(6 public 系统级无主=设计,3 private 有主) | ✅ |
| 文件 ACL | biz_folder_acl.role_id | 角色授权 | **仅 1 条(role 11)** | ⚠️ 功能配置空转 |
| 提醒 | biz_reminder.owner_id | 本人 | 427 全有 | ✅ |
| 审批 | biz_approval.applicant_id | 范围筛选 + 指定审批人(方法内守卫) | 18 全有 | ✅ |
| 积分 | biz_score_entry.user_id/item_id/period | 数据范围 + 排行公司级 | 26 全有 | ✅ |
| 核心指标 | biz_customer_indicator.customer_id | 公司级(0808 设计) | 18 | ✅ |
| 公海 | biz_sea_pool.customer_id/pool_id/original_owner_id | 池级组织勾选 | 47 全有(available 32/claimed 10/pending 5) | ✅ |
| 客户分享 | biz_customer_share | 分享=读权 | **0 条=从未使用** | ⚠️ 功能空转 |
