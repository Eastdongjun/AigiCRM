# 实现记录

## 变更文件

- 无代码变更；数据库 DELETE 1 条（biz_approval_config id=1）

## 实现决策

- 删除条件 WHERE id=1 AND flow_name='客户捞取'（双条件防误删）
- 删前确认 biz_approval_flow 无引用（claim/transfer/extend/storage 4 条均不同）

## 新增或更新的测试

- 无（数据清理）

## 实际执行命令

- `DELETE FROM aigicrm.biz_approval_config WHERE id=1 AND flow_name='客户捞取'` → 回读仅剩 id=2"捞取客户审批/claim"

## 失败与纠正

- 无

## 未完成与残余风险

- 生产未清理（用户未要求，生产无此测试数据与否未核实）
