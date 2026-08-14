# 需求规格

## 目标用户与场景

- 角色：激励上传用户（销售/管理员）、公海池用户（销售/管理员）、系统管理员（建用户）。
- 场景：
  1. 销售/管理员在激励上传弹窗点击"+"选择安装文件——现在点不到/点了没反应；
  2. 非管理员通过接口捞取公海客户——现在可捞取不可见池客户；
  3. 管理员新增用户——现在空账号/空姓名/无角色可保存。

## 事实

- 弹窗布局：`.dlg-body` max-height 62vh、内容 924px（scrollH）、scrollTop=0；"+"卡片 y=624~724 与 `.dlg-footer-btns`（y=633 起）重叠（浏览器实测，1440x900 视口）。
- `pickFile` 调用 `fileInputRef.click()` 依赖 user activation；布局重叠导致用户点击命中 footer。
- `ApprovalService.submit()`（ApprovalService.java:95-117）claim 分支无可见范围校验；`SeaService.visiblePoolIdsOrNull`（SeaService.java:50-53）已有可见池计算逻辑可复用。
- `user.vue` formRules（157-165 行）定义 realName/username/roleId/phone 必填；账号（80 行）、姓名（89 行）、角色（108 行）el-form-item 缺 prop；`SysUserService.create()`（51-77 行）无必填校验。
- 部署版与源码 dist 一致（incentive-BQqAaFz-.js 等，15:15 构建）。

## 假设

- ①修复后用户点击"+"应弹出系统文件选择框；判断依据为布局不再遮挡 + user activation 下 input.click() 正常（真实浏览器行为）。
- ②可见范围校验只针对非 all 数据范围用户；admin 全可见不受影响。
- ③所属组织（deptId/officeId）不新增必填（用户未要求）。
- 验证方式：测试服浏览器实测 + 接口直接调用验证。

## 阻断问题

无。

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 激励上传弹窗布局修复：文件槽位"+"卡片与文件卡片完整可见、不被底部按钮区覆盖，点击"+"可触发文件选择 | 用户① | Must |
| REQ-002 | 公海池捞取申请接口增加可见范围校验：申请人须可见目标客户所在公海池，否则拒绝并提示 | 用户② | Must |
| REQ-003 | 新增用户表单必填校验生效：账号/姓名/角色/手机号必填（前端 prop + rules） | 用户③ | Must |
| REQ-004 | 后端新增用户接口增加必填兜底校验（账号/姓名/角色/手机号非空），防止绕过前端 | 用户③ | Must |
| REQ-005 | 重新构建并部署前端（与后端）到测试服，修复生效且无回归 | 交付 | Must |

## 业务规则与权限

- 公海池可见性：all 数据范围全可见；非 all 用户须命中池的 visible_user_ids 或 visible_dept_ids（SeaConfigService.isVisibleTo）。
- 捞取权限：sea:view（列表）保持不变；捞取申请在现有"在公海中/未被他人申请"校验基础上增加"申请人可见该池"。
- 用户管理：sea:config:edit 等权限不变；新增用户仍由系统管理员操作。

## 异常与边界

- ①：单槽模式（visit/exchange/plan）与三槽（trial）均需验证；小视口（1280x720）与大视口（1440x900）均需验证。
- ②：客户所在池 pool_id 为 NULL（历史隐身客户）时捞取拒绝（不可见池不存在）——需确认提示友好；已被他人申请仍在先校验。
- ③：编辑用户不强制必填（已有数据可能缺字段）；仅新增时校验。
- 后端兜底：roleId 为空时拒绝，避免建出无角色用户。

## 非功能需求

- 性能：新增校验为内存过滤（listVisible 已有），无额外 DB 开销。
- 安全：接口层校验防绕过前端。
- 兼容性：Chrome/Edge 主流浏览器；1440x900 与 1280x720 分辨率。
- 可维护性：复用现有 visiblePoolIdsOrNull/listVisible，不新增平行逻辑。

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | 打开激励上传弹窗（1440x900），文件槽位"+"卡片完整可见且不被底部按钮遮挡，点击"+"弹出系统文件选择框（可选中文件后显示卡片） |
| AC-002 | REQ-001 | 1280x720 视口下同样可见可点 |
| AC-003 | REQ-001 | 追加模式（点表格行）弹窗同样修复；单槽/三槽均无遮挡 |
| AC-004 | REQ-002 | wangleilei（无可视池）直接 POST /api/approval/submit type=claim 捞取不可见池客户 → 拒绝并提示 |
| AC-005 | REQ-002 | admin 捞取任意 available 客户仍正常（all 数据范围不受限） |
| AC-006 | REQ-003 | 新增用户弹窗不填账号/姓名/角色点保存 → 表单提示必填且不提交 |
| AC-007 | REQ-004 | 绕过前端直接 POST /api/system/user 空账号/空姓名 → 后端拒绝 |
| AC-008 | REQ-005 | 前端构建成功、部署测试服；修复后回归：激励上传页 20 页主路径无 console 错误 |

## 追踪关系

REQ-001 → AC-001/002/003；REQ-002 → AC-004/005；REQ-003 → AC-006；REQ-004 → AC-007；REQ-005 → AC-008
