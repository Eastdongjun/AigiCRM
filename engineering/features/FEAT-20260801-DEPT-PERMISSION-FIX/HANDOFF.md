# HANDOFF — FEAT-20260801-DEPT-PERMISSION-FIX（已完成）

> 业务角色部门查看权限修复。新增客户等页面 403 根因：页面加载调 /system/depts（system:dept:view），销售员/行政角色缺该权限。

## 当前状态（2026-08-01 22:05）

- role 9/11 已授 system:dept:view（基础数据-部门菜单 id=98），9 个业务页面验证 0 403
- 生产未同步（同一 INSERT 即可）
