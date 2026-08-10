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
