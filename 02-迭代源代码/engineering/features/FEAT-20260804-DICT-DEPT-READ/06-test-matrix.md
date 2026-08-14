# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 生产实测：NM005 登录打开 /customer/add 无「没有访问权限」弹窗（Playwright 快照 f3e3/f6e3） |
| AC-002 | TEST-002 | PASS | 生产实测：NM005 打开新增客户页，「所属大区」下拉展开含「内蒙古大区」（find 快照 f3e355） |
| AC-003 | TEST-003 | PASS | 生产实测：行业下拉 9 个字典项（冶金/煤矿/化工/电力等，listbox f3e340-348）；GET dict/data/industry=200 |
| AC-004 | TEST-004 | PASS | 生产实测：客户列表/统计页控制台 0 error；接口 depts/dict 全 200（curl 断言） |
| AC-005 | TEST-005 | PASS | 生产实测：NM005 POST /api/system/dict/types → 403（curl） |
| AC-006 | TEST-006 | PASS | 生产实测：NM005 GET /api/system/users → 403（curl） |
| AC-007 | TEST-007 | PASS | 生产实测：无 token GET /api/system/dict/data/industry → 403（未认证拒绝，系统统一 403 语义） |
| AC-008 | TEST-008 | PASS | 生产实测：admin 组织架构页正常渲染（总部/大区树、编辑删除按钮），控制台 0 error；admin analysis=全量 249/3 办事处 |
| AC-009 | TEST-009 | PASS | 生产部署完成：新 jar md5=fda32552...（=新包），Started 22:43:51；NM005 全部实测通过 |
| AC-010 | TEST-010 | PASS | 生产实测：NM005 统计页客户总数 12（=名下 12 客户）、办事处仅内蒙办销售部、行业分布个人范围 |
| AC-011 | TEST-011 | PASS | 测试环境实测：wangleilei（org_and_children）analysis=98/1 办事处 vs admin=254/4 办事处（范围过滤生效） |
| AC-012 | TEST-012 | PASS | 生产实测：admin analysis=249/3 办事处（与修复前一致，全量无回归）；测试环境 admin=254/4 |
| AC-013 | TEST-013 | PASS | 测试环境实测：wangleilei daily 文件 8 份 vs admin 72 份；生产 NM005 analysis 月度文件 39（个人上传） |
| AC-014 | TEST-014 | PASS | 生产实测：NM005 POST /api/system/dict/types → 403、POST /api/system/depts → 403（写操作严格） |

## 自测场景分类（8 类必查）

| 场景 | 说明 | 结果 |
|---|---|---|
| 正常路径 | NM005 新增客户页表单加载+下拉可选+统计页正常 | PASS |
| 权限 | 读接口放开为登录可读；写操作/系统接口 403；未登录拒绝 | PASS |
| 状态 | 无状态机变更 | PASS（不适用） |
| 重复操作 | 无新增可重复操作 | PASS（不适用） |
| 参数 | analysis officeId 参数与数据范围 AND 合并 | PASS |
| 并发 | 无新增并发路径 | PASS（不适用） |
| 异常 | 空 JWT_SECRET 启动失败已纠正（部署中验证）；OSS 占位值启动正常 | PASS |
| 历史数据 | 无数据表变更，历史客户/文件按 owner_id/uploader_id 正常过滤 | PASS |

## 静态与构建验证

- `mvn clean compile`：BUILD SUCCESS（JDK 17，openjdk@17 17.0.20）
- `mvn test`：**38 tests, 0 Failures, 0 Errors**（含新增 4 个数据范围测试：getCurrentScopeUserIds all/self/null/dept_and_children）
- 打包：`mvn package -DskipTests` → aigicrm-backend-1.0.0.jar（94,852,183 字节）

## 功能与集成验证

- 测试环境（111.229.37.220）：部署新 jar → depts 200 / dict 200 / POST depts 403 / GET users 403 / analysis 范围过滤（98 vs 254）/ daily 文件（8 vs 72）
- 生产（www.aigicrm.com）：部署新 jar（md5 校验一致）→ 全接口断言 + 浏览器实测（NM005 新增客户/统计页；admin 组织架构页回归）
- 数据修复：生产库给销售员(11)/行政(9) 勾选隐藏权限 98/99（sys_role_menu INSERT，已验证）；测试库销售员补绑菜单 21（仅测试环境）

## UI 验证

- ui_scope=none：无视觉变更。页面级证据：Playwright 快照 + 控制台 0 error（新增客户页 f6e3、统计分析页 f5e3、组织架构页 f7e143）。

## 回归验证

- **测试环境全量**：mvn test 38/38 PASS（含 ArchitectureTest 架构约束、既有 CustomerService/Approval/Incentive/Role/权限规则测试——本次改动未破坏任何既有行为）。
- **生产 admin 回归**：登录正常、组织架构页（系统管理）正常渲染、analysis=全量 249/3 办事处（与修复前一致）、控制台 0 error。
- **生产销售员回归**：新增客户页/统计页/客户列表全部正常，无新增 403。
- **写操作与系统接口回归**：NM005 POST dict/types 403、POST depts 403、GET users 403（严格权限未放宽）。
- **认证回归**：未登录访问被拒绝；JWT_SECRET 与生产原值一致，存量登录 token 无缝（无强制下线）。

## 未覆盖与风险

- `dashboard()` 的 seaStats（公海统计）仍为全量——公海为全公司共享池，业务常态，本次未纳入范围过滤；若后续要求公海也按数据范围，另立需求。
- 生产 `deploy-backend.sh` 后台运行卡住（[4/5] 重启步骤）——本次手动完成部署；脚本与生产 Dockerfile-run 的 jar 路径约定需后续统一排查。
- AC-011（行政 dept_and_children 生产实测）以测试环境 org_and_children 用户（wangleilei 98 vs admin 254）验证范围过滤语义；生产行政角色可读性由接口放开保证（200），数据范围语义与测试环境同源实现。
- 测试环境 wangleilei 补绑菜单 21 与生产库 98/99 勾选为验证/辅助数据变更，均已记录（见 05 实现日志），如需回滚有对应 SQL。
