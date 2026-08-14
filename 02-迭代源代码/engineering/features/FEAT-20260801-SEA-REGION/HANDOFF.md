# HANDOFF — FEAT-20260801-SEA-REGION（已完成）

> 公海池组织架构全层级可选 + 组织类型中文显示。AC-001~004 全 PASS，测试服已部署。

## 当前状态（2026-08-01 20:35）

- **DELIVERED**（待 gate 状态收尾）：公海池可见范围树 大区/办事处/部门/人员全可选（总部灰显），勾选/标签/清空/编辑回显完整
- 修复既有 bug：config.vue 模板引用 onSeaTreeCheck 等 4 个未定义函数（公海池可见范围从未可用）
- 后端：isVisibleTo 部门上溯匹配、deriveRegionIds 大区向下展开（验证 region_ids=4 正确）
- 组织架构页类型中文显示（1总部/2大区/3办事处/4部门）

## 关键事实

- 组织层级 dept_type：1 总部/2 大区/3 办事处/4 部门（sys_dept 与 sys_org 同语义）
- 测试池"山西大区测试池"验证后已删除（DELETE 中文条件有 SSH 编码坑，按 id 删）
- 回滚：后端 /app/app.jar.bak-sea-region、前端 /tmp/html-rollback2/

## 残余风险（待用户确认）

1. 生产未部署（需授权）
2. 普通用户大区可见性未实测（admin dataScope=all 覆盖不了）

## 下一步（若用户要求）

1. 生产同步
2. 字典管理 UI 美化（用户已提，另开功能包）
