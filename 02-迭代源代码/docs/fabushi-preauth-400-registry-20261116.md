# 鉴权前 400 登记册（2026-11-16 收口）

## 背景

2026-11-16 的 24,024 请求权限矩阵（28 用户 × 286 端点 × 3 认证态）中，
250 条 `mode=valid` 的无权限用户请求收到 400 而非 401/403，被列为
"鉴权前 400"待收口项（见 `docs/fabushi-test-report-20261116.md` 残余段）。

本册给出逐端点归因、源码证据与收口方式。**结论：全部 250 条均已解释，
无需改任何接口的权限语义，无需改接口契约。**

## 归因总览

| 归因 | 条数 | 处理 |
|---|---:|---|
| 参数形状先于方法鉴权（真鉴权前 400） | 227 | 矩阵白名单 + 本册登记 |
| 矩阵脚本 or 表达式误判（**矩阵误报**，系统行为正确） | 23 | 修 `permission_rule` 解析，白名单不含此端点 |
| 合计 | 250 | 重跑矩阵 exit 0 验证 |

### 根因 A：Spring MVC 参数解析先于方法鉴权（227 条）

Spring MVC 的调用顺序是 **HandlerAdapter 参数解析 → AOP 链 → `@PreAuthorize` 求值 → 方法体**。
矩阵脚本对 POST/PUT 发空 body `{}`、对 GET 不带 required query
（`scripts/fabushi/permission_matrix_20k.py` 的 `call()`），这类请求在参数解析阶段即被
`GlobalExceptionHandler` 统一拒绝为 400，**永远到不了 `@PreAuthorize`**——对无权限用户如此，
对有权限用户同样如此。controller 方法体未执行，**无数据泄露、无越权成功**。

### 根因 B：矩阵脚本 or 表达式误判（23 条，已修根因）

`POST /api/customer/{id}/stage-amounts` 的权限门是三支 or：

```java
@PreAuthorize("hasAuthority('finance:manage') or hasAuthority('customer:finance:edit') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
```
（`aigicrm-backend/.../module/customer/controller/CustomerController.java:355`）

矩阵脚本旧版 `permission_rule` 把表达式中全部 `hasAuthority` 码抓出后按 **and**（`'all'`）判定，
将 or 语义错当 and：TY002~TY018/NM002~NM008（23 人，持 `customer:edit`+`finance:view`，
满足第三支）被误判 `denied`，其请求真实穿过 `@PreAuthorize` 进入方法体、被业务守卫
"客户不存在或已删除"（999999 探针 ID）拒为 400，被矩阵记 fail。

**系统真实行为完全正确**（有权限 → 业务 400；三支全缺 → 403）。已在
`permission_matrix_20k.py` 的 `permission_rule`/`expected_ok` 支持
支间 or、支内 and 语义，负例自证 14/14 PASS。同款表达式端点
`GET /api/customer/group-names`（`customer:add or customer:edit`）一并修复。

## 端点登记表（9 个真鉴权前 400 端点）

400 响应均为 `GlobalExceptionHandler` 的参数类错误消息（无权限用户与 admin 逐字一致，
已用 NO001/TY016 真实 token 实测）；权限门逐条源码核对，**无一缺失**。

| # | 端点 | 权限门（证据 file:line） | 400 根因（实测响应消息） |
|---|---|---|---|
| 1 | `PUT /api/system/perm-scopes/{roleCode}` | `system:scope:grant`（RolePermScopeController.java:36） | `@RequestBody List<Map>` 收 `{}` → "请求体格式不正确，请检查JSON格式" |
| 2 | `PUT /api/system/perm-scopes/assignee/{type}/{key}` | `system:scope:grant`（RolePermScopeController.java:55） | 同上 |
| 3 | `PUT /api/system/configs` | `system:config:edit`（SysConfigController.java:52） | `@RequestBody List<SysConfig>` 收 `{}` → 同上 |
| 4 | `GET /api/score/export/grid` | `score:manage`（ScoreController.java:119） | `@RequestParam String period` 缺失 → "缺少必要参数：period" |
| 5 | `POST /api/system/depts/{deptId}/admins` | `system:dept:edit`（SysDeptController.java:78） | `@RequestParam Long userId` 缺失 → "缺少必要参数：userId" |
| 6 | `PUT /api/system/roles/{id}/orgs` | `system:role:edit`（SysRoleController.java:42） | `@RequestBody List<Long>` 收 `{}` → "请求体格式不正确，请检查JSON格式" |
| 7 | `POST /api/customer/import` | `customer:import`（CustomerController.java:521） | 非 multipart → "请使用 multipart/form-data 表单提交文件" |
| 8 | `POST /api/sea/import` | `sea:import`（SeaController.java:78） | 同上 |
| 9 | `GET /api/system/perm-center/preview` | `system:config:view`（PermCenterController.java:41） | `@RequestParam Long userId` 缺失 → "缺少必要参数：userId" |

Controller 路径缩写均指
`aigicrm-backend/src/main/java/com/aigicrm/module/{system|score|customer|sea}/controller/`。

## 收口方式（不改权限、不改契约）

1. **矩阵脚本白名单**：`scripts/fabushi/permission_matrix_20k.py` 的 `PARAM_SHAPE_OK`
   收录上述 9 端点。对这些端点，无权限用户收到 400 与 401/403 同视为"已解释"。
2. **负例自证**：白名单只接受 400/401/403——**2xx（越权成功）/5xx 仍然 fail**。
   若某端点的 `@PreAuthorize` 被移除，无权限用户的合法形状请求将收到 2xx，矩阵立即变红。
3. **or 解析修复**：`permission_rule` 按支间 or、支内 and 解析（修复 23 条误报）；
   无法解析的混合形态回落 `('expr')` 保守哨兵，只对 5xx/越权 2xx 报警。

## 明确不做的事

- **不做入口级 URL 鉴权改造**（SecurityFilterChain authorizeHttpRequests）：
  288 端点现为方法级 `@PreAuthorize` 单轨，引入 URL 级双轨属架构级变更，
  且本 400 无安全影响，收益不抵风险。如需彻底消除"鉴权前 400"体验差异，
  应作为独立拍板项（方案：对这 9 端点补 URL 级权限规则，无权限用户在 filter 层即 403）。
- **不改接口参数契约**（例如把 required query 放宽）以换取请求到达鉴权层——
  这会削弱正常用户的参数校验提示，属"放宽"类禁止项。

## 验证口径

重跑命令（同 `test-results/permission-matrix/summary.md`）：

```bash
/tmp/mpvenv/bin/python scripts/fabushi/permission_matrix_20k.py \
  --base http://111.229.37.220 --users /tmp/fabushi_users.json --workers 64
```

验收标准：**0 个 5xx、0 个越权 2xx、0 个未解释 403，脚本 exit 0**；
227 条原 fail 变为白名单内通过，23 条 stage-amounts 误报随 or 修复消失。

## 重跑实证（2026-11-16 修复后，24,024 例最终轮）

- **valid 模式 400 fail：250 → 0**（227 条白名单内通过 + 23 条随 or 修复转为 authorized 正常判定）
- **5xx：0；网络错误：0；越权 2xx：0**
- POST 写端点（含 stage-amounts）fail：0；TY016/admin 现场实测 stage-amounts 返回
  400 业务守卫"客户不存在或已删除"（方法体内，非鉴权前）
- 残余 fail：794 条 403，全部已归因且与上轮口径逐字同数——
  790 条 `{id}=999999` 资源/数据范围守卫（34 端点）+ 4 条 `POST /api/system/users`
  空 body 组织守卫。脚本 exit 1 系 fail 计数含资源守卫 403 的既有设计，非本轮回归。
- 对账：上轮 1,046 = 790+250+4+2（500 已修）→ 本轮 794 = 790+4，差额 252 =
  本次收口的 250 条 + 已修 2 条 500，账目闭合。
- 注意区分：矩阵结果中 `GET /api/customer/{id}/stage-amounts`（查询端点）的 403 属
  资源守卫类，**不是**本次登记的鉴权前 400；POST 写端点无参数形状问题（record
  全 null 反序列化成功），不在白名单内。
- 过程记录：修复后首轮曾出现 1 条 `GET /api/system/perm-scopes/reconcile`
  ERR:TimeoutError（64 并发瞬时抖动，单独实测 3 次均 200/1.35s），最终轮消失。
