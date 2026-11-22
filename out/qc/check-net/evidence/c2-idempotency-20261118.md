# C2 幂等/重复请求 — 代码级核查（2026-11-18，HEAD 10de7dd7 工作区）

> 静态核查；运行时重放实验留 W3（需测试服窗口）。生产零写入。

## 一、已具备幂等/防重（代码级 PASS）

| 场景 | 位置 | 机制 |
|---|---|---|
| 审批 approve/reject | `ApprovalService.java:635-644/750-779` | 并发/重复点击 → 「该审批已处理，请刷新后查看」；多人同时通过结果唯一 |
| 存储空间申请 submit | `ApprovalService.java:266-274` | `SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE` 串行化同一申请人；`hasPendingStorageApply` 防在途重复单 |
| 公海捞取 submit | `ApprovalService.java:284+` | 检查池状态（available/pending_approval），已被他人申请则拒 |
| 文件删除 | `FileService.java:581/638` | `affected==1` 才归还配额；重复/并发删除幂等返回成功且不二次退款 |
| 提醒处理 | `ReminderService.java:506` | 已处理提醒幂等跳过（P2 2026-11-02） |
| 提醒物化 | `ReminderService.java:403` | 物化幂等（POST 显式触发，GET 不写库） |
| 文件授权迁移 | `BizFileGrant.java:43` | `legacy_ref` 双写 upsert 幂等键 |
| 角色关联/用户组织 | `SysRoleService.java:203`、`SysUserService.java:498` | 清空重建/同值重算幂等 |

## 二、部分/设计选择

- **客户创建/改名**：`CustomerService.java:421/714` 名称唯一（`uk_customer_active_name`）→ 重复提交报「客户名称已存在」，非静默重复；`CustomerService.java:1837` 注释承认"用户重试可能重复建客户"，靠单事务整体回滚防半量状态。**创建者并发双击可能第二次 400（可接受）**。
- **Excel 导入**：`ImportGuard` 仅限 5MB/5000 行/扩展名（DoS 防护）；**无导入幂等**——重复导入同一文件逐行触发名称唯一冲突并整体失败（不产生重复客户，但报错中断）。可接受；如需"跳过已存在行"属产品口径。
- **文件上传**：`FileService.updateMd5` 仅回填分片上传 md5，**无内容去重**——同文件可多次上传（版本机制覆盖同文件场景）。设计选择，非缺陷。
- **导出**：只读，无副作用。

## 三、待运行时验证（W3）

- 审批 approve 并发重放 ×N（两账号同时点通过，断言仅一条成功、状态/节点/业务副作用唯一）；
- 存储申请并发双击（断言仅一单）；
- 公海捞取并发（断言仅一人）；
- 重复导入同一文件（断言客户数不变、报错信息一致）。

## 四、结论

- **C2 静态 = PASS（核心写点均有守卫）**；运行时重放实验 = BLOCKED（待 W3 窗口）。
- 证据：本文件；代码行号基于 HEAD `10de7dd7` 工作区（含 0b 未提交 C-js-perm 改动不影响上述文件）。
