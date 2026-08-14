# VSCode 诊断修复任务清单（2026-08-12 · 更新）

> 生成：监督面板（Reasonix）｜ 处理人：Claude 会话（或人工）
> 原则：**只做零行为变更的修复**。不碰业务逻辑、数据库、运行行为。

## 进度里程碑

- ✅ **误报 721 条已全部消除**（`.settings` 配置生效，commit `9e6ae15`，VSCode reload 后）
- ✅ 真实信号修复两轮：`a408360`（45 条）+ Claude `b115308`/`cbf403f`（29 处 import/注解清理，测试 365 全绿）
- ✅ `FileServicePermissionTest:295` 未使用资源变量（本轮）
- 📍 **当前剩余：27 条真实信号**（见下）

## 剩余清单（27 条）

### A. 业务敏感，需单独评估（4 条）⚠️
- [ ] `UserDetailsServiceImpl.java:208/209/212/213`：`DataScopeType.ORG_AND_CHILDREN` 废弃字段——数据范围核心逻辑，替换需评估新枚举语义，**单独排期**

### B. CustomerService 遗留（8 条）— 建议由当前 Claude 会话处理（文件刚由它提交）
- [ ] `CustomerService.java:90` `else if (false)` 显式禁用分支（建议保留，警告接受）
- [ ] `CustomerService.java:113/130/195` `wrapper.in(SFunction, String[])` → `(Object[])` 显式转换（语义相同）
- [ ] `CustomerService.java:749` 未使用方法 `applyDeptIdScope`（确认无调用后删）
- [ ] `CustomerService.java:786` 未使用方法 `applyDeptScopeUserIds`（同上）
- [ ] `CustomerService.java:1019/1021` 未使用局部变量 `custName`/`sharerName`（看代码后删赋值）

### C. 并行会话文件（2 条）— Claude 处理中
- [ ] `PermissionCacheServiceTest.java:105` forClass(Class<Set>) unchecked
- [ ] `SysUserPageGroupScopeTest.java:92` 未使用局部变量 result

### D. 测试构造器依赖，保留合理（3 条）✅ 无需处理
- `IncentiveService.java:36` customerShareMapper｜`CustomerAccessService.java:42/43` deptMapper/userMapper——删除会破坏 8 参测试构造器

### E. 可安全处理（待办，约 10 条）
- [ ] 其他测试/工具类未使用变量（按诊断面板实时清单逐个确认后清理）

## 验证方式

1. 诊断面板应只剩上述真实项（误报已清零）
2. `mvn -q test-compile` 编译通过；相关单测 `mvn -q test -Dtest=受影响类`
