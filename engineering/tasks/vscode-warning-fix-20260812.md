# VSCode 诊断修复任务清单（2026-08-12）

> 生成：监督面板（Reasonix）｜ 处理人：Claude 会话（或人工）
> 原则：**只做零行为变更的修复**。不碰业务逻辑、数据库、运行行为。
> 进度：Claude 会话已自行清理 7 处 import（commit b115308）+ 权限链路 17 项（315a5e7）。

## 背景

- 当前诊断：790 条 = 729 误报 + 58 真实信号（Error 0）
- 729 误报：MyBatis-Plus `LambdaQueryWrapper` 方法引用 × JDT null 分析（已通过 `.settings/org.eclipse.jdt.core.prefs` 配置忽略，需 reload 窗口生效）
- 58 真实信号分组见下

## 待处理清单（按安全等级排序）

### P0 已由监督面板处理 ✅
- [x] `.settings/org.eclipse.jdt.core.prefs`：忽略 nullUncheckedConversion（消 729 误报）

### P1 零风险（删未使用 import/注解/测试死代码，~16 处）
- [ ] `BizIncentiveRecord.java:7` 删 `import java.util.Arrays`
- [ ] `AuthController.java:15` 删 `import java.util.Map`
- [ ] `SysUserController.java:12,17` 删 `import SecurityUtils`、`import java.util.Map`
- [ ] `CustomerAccessService.java:5` 删 `import DataScopeType`
- [ ] `FieldFilterAdvice.java` supports/beforeBodyWrite 补 `@NonNull`/`@Nullable`（org.springframework.lang）— 接口继承注解要求，零行为变更
- [ ] `JwtAuthFilter.java:27,28` 同上补注解
- [ ] `OrgScopeResolverTest.java:34`、`ScopeServiceTest.java:35` 删多余 `@SuppressWarnings`
- [ ] `FolderPermissionServiceTest.java:301` 删未使用方法 `aUser(Long)`
- [ ] `SysRoleServiceTest.java:58` 删未使用方法 `newService(SysRoleOrgMapper)`
- [ ] `SysUserPageGroupScopeTest.java:92` 删未使用局部变量 result

### P2 低风险（测试代码泛型警告，~17 处）
- [ ] `IncentiveShareVisibilityTest.java`（249/269/287/302）`ArgumentCaptor.forClass(LambdaQueryWrapper.class)` → `<LambdaQueryWrapper>forClass(...)` 或加 `@SuppressWarnings("unchecked")`
- [ ] `IncentiveSpecificProductTest.java:83` raw type → 补泛型
- [ ] `SysPositionServiceTest.java`（71/89/107）、`ApprovalServiceTest.java:173`、`SysRoleServiceTest.java`（196/214/232）、`PermissionCacheServiceTest.java:105` 同上
- [ ] `CustomerExpireTaskTest.java`（46/69）泛型数组 varargs → `@SuppressWarnings("unchecked")` 或显式数组

### P3 需读代码后谨慎处理（行为不变但涉及业务文件）
- [ ] `GlobalExceptionHandler.java:73`：`getRequiredType()` 已判空，提取局部变量消除二次调用警告（行为不变）
- [ ] `CustomerService.java`（113/130/195）：`wrapper.in(SFunction, arr)` → `(Object[]) arr` 显式转换（语义相同）
- [ ] `StatisticsQueryService.java:204`：未使用局部变量 qualifyCount——确认后删或保留
- [ ] `IncentiveService.java:36`：未使用字段 customerShareMapper——确认无测试引用后删
- [ ] `CustomerAccessService.java:43,44`：未使用字段 deptMapper/userMapper——同上
- [ ] `CustomerService.java:90`：`else if (false)` 显式禁用分支——建议保留（设计历史），警告接受

### P4 业务敏感，不建议本次处理 ⚠️
- [ ] `UserDetailsServiceImpl.java:208-213`：`DataScopeType.ORG_AND_CHILDREN` 废弃字段（4 处）——数据范围核心逻辑，替换需评估新枚举语义，**单独排期**

## 验证方式

1. VSCode reload window（或 Java: Clean Java Language Server Workspace）
2. 重新拉诊断：误报 729 应消失，只剩真实项
3. `mvn -q compile`（或 `mvn -q test-compile`）确认编译通过
4. 相关模块单测：`mvn -q test -pl . -Dtest=受影响测试类`
