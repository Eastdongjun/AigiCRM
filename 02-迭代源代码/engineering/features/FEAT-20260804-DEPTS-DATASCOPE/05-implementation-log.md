# 实现记录

## 变更文件
- ycmn-backend/src/main/java/com/ycmn/module/system/service/SysDeptService.java
  (REQ-001/AC-001~003):新增 scopeFilter()——isAdmin 返回全部;非管理员按 getCurrentDeptId()
  上溯 parentId 收集本人部门+祖先链;tree()/list() 均接入过滤

## 实现决策
- 保留 system:dept:view 权限与菜单绑定(激励查询页 loadOrgs 功能依赖),仅收窄查询数据范围
- 前端零改动

## 新增或更新的测试
- mvn test 34/34 全过(含既有架构测试);
- 端到端:测试环境 admin 全量 13 部门;生产 TY101 祖先链 3 部门、admin 10 部门、激励接口 200

## 实际执行命令
- mvn compile/test/package;测试环境 admin 全量 13 验证;生产:TY101 仅 3 部门(祖先链),
  admin 10 部门全量,激励接口 200

## 实际执行命令
- mvn compile/test/package;测试环境 docker cp+restart;生产 deploy-backend.sh(commit e0686fa)

## 失败与纠正
- 测试环境无 TY101 账号,临时账号登录 401(测试库与生产库数据差异)→ 改为生产真实账号验证,
  测试残留账号已清理

## 未完成与残余风险
- 非管理员 dept_id 为空 → 空部门列表(前端容错,可接受)
