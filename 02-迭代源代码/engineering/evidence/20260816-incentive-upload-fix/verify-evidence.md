# 激励上传修复验证证据（2026-08-16）

## 背景
代码审查发现：激励上传接口 `POST /api/incentive/records` 依赖 `incentive:add` 权限码，
但 Flyway 迁移链（V1→V16）只有 V16 补了 sys_menu 菜单行、**没有角色绑定**；
权限判定 = 菜单行 ∩ sys_role_menu 绑定（SysMenuMapper.selectPermissionsByUserId），
标准链重建的库上传全员 403。另有 addRecord 零校验、updateRecord 归属客户可被本人改挂两个问题。

## 修复内容
1. `ycmn-backend/src/main/resources/db/migration/V17__bind_permission_codes_to_roles.sql`
   - incentive:add → 角色 1/9/10/11；file:acl:manage → 1；sea:import → 1/9/10（幂等 NOT EXISTS + uk_role_menu）
2. `IncentiveService.addRecord` 增校验：分类/客户必填且存在、至少一个文件槽、文件引用存在且属于当前用户
3. `IncentiveService.updateRecord`：归属客户 customerId 仅 admin/incentive:manage 可变更，本人传不同值 403
4. 新增单测 `IncentiveUploadValidationTest`（10 用例）

## 验证证据

### 编译与单测
- `mvn -q compile` → exit 0（JDK 21）
- `mvn -q test -Dtest=IncentiveUploadValidationTest` → 10/10 通过
- `mvn -q test -Dtest='com.ycmn.module.incentive.**'` → 激励模块全绿
- 后端全量 `mvn test` → **Tests run: 423, Failures: 0, Errors: 0**（BUILD SUCCESS）

### 项目门禁 verify.sh（2026-08-16）
- ✅ 全量验证完成：后端编译+单测 / 前端单测 / 前端生产构建 / 三脚本交叉核对全部通过
- 集成测试步骤因 MySQL 3308 未启动而跳过（脚本自带跳过逻辑，非失败）

### V17 迁移演练（一次性 MySQL 8.4.11 实例，独立数据目录 /tmp/v17-mysql，练后已删）
- 模拟生产现状：sys_menu 含 114(incentive:add,type=2) 等三权限行；admin 已手工绑定 incentive:add
- V17 执行成功；执行后绑定矩阵：
  - incentive:add → roles 1,9,10,11 ✔
  - file:acl:manage → roles 1 ✔
  - sea:import → roles 1,9,10 ✔
- 幂等重放：再次执行 V17 无报错，绑定总数保持 8 条不变
- 回滚演练：执行回滚 SQL 后绑定归 0

### 注释门禁
- 03-comment-check（G8-G12）对改动文件：G10/G11/G12 PASS（见门禁后台输出）

## 残余风险
- 生产/测试库已有手工绑定（08-05 perm-codes 脚本），V17 上线不改变现网行为，仅补齐重建链
- V17 生产执行需经 `scripts/deploy-db.sh`（SSH 生产，未执行，待用户授权）
- 测试服务器部署未执行（未授权）
