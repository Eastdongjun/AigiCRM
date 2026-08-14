# 技术实施计划

## 已确认影响范围

| 文件 | 改动 |
|---|---|
| `ycmn-ui/src/views/incentive/index.vue` | 弹窗布局 CSS：`.upload-dlg :deep(.el-dialog)` 定高 flex 布局；`.dlg-body` 去 62vh 双滚动 |
| `ycmn-ui/src/views/system/user.vue` | 账号/姓名/角色 el-form-item 补 `prop` |
| `ycmn-backend/.../approval/service/ApprovalService.java` | submit claim 分支加可见范围校验 |
| `ycmn-backend/.../system/service/SysUserService.java` | create 加必填兜底校验 |
| 构建产物 | ycmn-ui/dist（vite build）、后端 jar（mvn package）→ 部署测试服 |

## 架构与关键决策

1. **弹窗布局**：`.upload-dlg :deep(.el-dialog){height:96vh;max-height:96vh;display:flex;flex-direction:column;margin-top:2vh}` + 已有 `:deep(.el-dialog__body){flex:1;overflow-y:auto;min-height:0}` → header/footer 固定、body 独立滚动，文件槽位进入首屏，卡片不再与 footer 重叠。
   - 替代方案（改 62vh→更大数值）被拒：分辨率不同仍可能截断；双层滚动（el-dialog__body + .dlg-body）互相干扰。
   - 移除 `.dlg-body{max-height:62vh;overflow-y:auto}` 中的 max-height（保留 overflow 交给外层），避免双滚动。
2. **捞取可见校验**：复用 `SeaConfigService.listVisible()`（内存过滤，无额外 DB 查询）；仅非 all 数据范围用户校验。
3. **用户必填**：前端补 prop（最小改动）；后端 create 首行校验 username/realName/roleId/phone 非空，抛 BusinessException。

## 数据、API 与事件契约

- 无数据表变更。
- `POST /api/approval/submit`（claim）：新增拒绝场景——申请人不可见目标客户所在公海池 → `BusinessException("您没有该公海池的可见权限，无法捞取")`。
- `POST /api/system/user`：新增拒绝场景——必填空缺 → `BusinessException("账号/姓名/角色/手机号不能为空")`。

## 权限与安全边界

- 捞取校验仅针对 data_scope != all 的用户；admin 路径不变。
- 可见性判定与现有列表一致（visible_user_ids / visible_dept_ids 命中）。
- pool_id 为 NULL 的客户：按不可见处理拒绝（无法确认归属池即视为不可见）。

## 实施顺序

1. 前端 `incentive/index.vue` 弹窗 CSS（最小改动）。
2. 前端 `user.vue` 补 prop。
3. 后端 `ApprovalService.submit` 加校验。
4. 后端 `SysUserService.create` 加校验。
5. 构建：前端 `npm run build`；后端 `mvn package -DskipTests`。
6. 部署测试服（dist → docker cp ycmn-frontend；jar → docker cp ycmn-backend + 重启）。
7. 浏览器验证 AC-001~008（含 1440x900 / 1280x720 截图）。

## 测试策略

- AC-001/002/003：浏览器实测——弹窗打开、+ 卡片可见可点、真实手势点击后文件可选中；追加/单槽/三槽全过。
- AC-004：wangleilei 直接 POST claim 不可见池客户 → 拒绝。
- AC-005：admin 捞取 available 客户 → 正常发起审批。
- AC-006：新增用户空必填点保存 → 前端标红。
- AC-007：POST /api/system/user 空账号/姓名 → 后端拒绝。
- AC-008：构建成功 + 部署后回归（激励上传页无 console 错误）。

## 迁移、发布与兼容

- 无数据迁移。前端 dist 整体替换（备份原 assets）；后端 jar 替换重启。
- 浏览器缓存：部署后 index.html 更新，旧 chunk 由用户强刷解决（已知残余风险）。

## 可观测性

- 新增校验异常走现有 GlobalExceptionHandler（BusinessException → 统一 message）。
- 不新增日志；异常场景用户可见提示即可。

## 回滚

- 前端：保留原 dist 备份 → 重新 docker cp 还原。
- 后端：保留原 jar → docker cp 还原 + 重启容器。
- 数据：无变更，无需回滚。

## 风险与盲区

- CDP 自动化无法直接验证原生文件选择框弹出（无 user activation），以布局命中（elementFromPoint = 卡片）为代理指标 + 真实用户确认。
- 弹窗定高 96vh 在超矮屏（<700px 高）可能压缩 body；风险低（内部滚动兜底）。
- 后端校验新增拒绝可能影响自动化/代理场景——已限制非 all 用户，风险低。
