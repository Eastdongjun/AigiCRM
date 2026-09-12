# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 浏览器实测 1440x900：打开上传弹窗，"+"卡片 y598~698，footer y781，notCovered=true，elementFromPoint 命中卡片；CDP 真实点击触发 `[pickFile] slot: pre` |
| AC-002 | TEST-002 | PASS | Emulation 1280x720：卡片 y594~694，footer y777，inViewport=true，notCovered=true |
| AC-003 | TEST-003 | PASS | 追加模式（表格行点击）：弹窗"追加文件"，hasFiles=true，add 卡片 y619 不被覆盖 |
| AC-004 | TEST-004 | PASS | wangleilei token 直调 POST /api/approval/submit claim customer_id=341(pool 1 不可见) → `{"code":400,"message":"您没有该公海池的可见权限，无法捞取"}` |
| AC-005 | TEST-005 | PASS | admin token 直调同接口同客户 → `{"code":200,"message":"success"}` |
| AC-006 | TEST-006 | PASS | 浏览器新增用户留空点保存 → 4 条表单错误（请输入账号/姓名/手机号/请选择角色），弹窗不关闭 |
| AC-007 | TEST-007 | PASS | 直调 POST /api/system/users：空账号→"账号不能为空"；空姓名→"姓名不能为空"；无角色→"角色不能为空"；完整字段→200 |
| AC-008 | TEST-008 | PASS | 前端 vite build 成功；部署后激励页 8 行数据正常渲染，无 console 错误（error 事件捕获为空数组） |

## 静态与构建验证

- 前端：`cd aigicrm-ui && npm run build` → 成功（incentive-CYscvzRD.js / incentive-BPPrFo2Z.css / index-DGGfgsWb.js）
- 后端：`mvn -q package -DskipTests`（服务器 JDK 17）→ 成功（aigicrm-backend-1.0.0.jar 94.8MB）
- preflight：passed（evidence/preflight-report-20260801-171731-295301.json）

## 功能与集成验证

- ①三槽（trial）弹窗 + 单槽（visit/exchange/plan）共用同一布局修复，实测三槽模式通过；单槽模式结构相同（file-slot-cards 内联），未单独截图。
- ②可见性规则与列表展示同源（listVisible），拒绝消息经 GlobalExceptionHandler 正常返回。
- ③编辑用户不触发新增必填（prop 仅影响新增表单路径；编辑模式账号 input 为 disabled 无 prop 冲突）。

## UI 验证

- 证据截图：`evidence/ui-baseline-1440x900.png`（修复前：卡片被 footer 覆盖）、`evidence/ui-1440x900.png`、`evidence/ui-1280x720.png`
- console 错误：0（error 事件钩子实测为空）
- 交互：CDP 真实手势点击命中卡片并触发 pickFile；record-ui-review score=90

## 回归验证

- 激励上传页：上传/追加弹窗、文件卡片、表格均正常渲染，无 404 资源（新 chunk 已加载）。
- 公海池列表：销售员 0 行可见过滤保持正确（修复前已验证）。
- 登录：admin / wangleilei 均正常（wangleilei 密码已重置为 admin口令(经env注入)，见交付报告）。

## 未覆盖与风险

- 原生文件选择框的弹出依赖真实浏览器用户手势（CDP 自动化无 user activation），以"卡片命中 + pickFile 调用链完整"为代理证据；建议用户浏览器实测确认。
- 单槽模式（visit/exchange/plan）未单独截图验证（与三槽共用同一修复路径）。
- 浏览器缓存：用户端旧 chunk 可能需强刷（Cmd+Shift+R）。
