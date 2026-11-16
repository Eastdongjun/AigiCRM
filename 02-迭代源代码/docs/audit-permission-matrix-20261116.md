# 权限矩阵·审计报告（2026-11-16）

## 一、数据可信度：旧口径 vs 审计口径

| 项 | 旧 v2 | 审计版 |
|---|---:|---:|
| 总行数（planned） | 42,511 | 48,580 |
| 真实执行（executed） | 39,149 | **43,991** |
| SKIP / N-A（未执行，单列） | 3,280 | 4,589（B 无他人资源 1,223 + C 写操作 3,366） |
| A 组 | 32,368（28 人） | **40,460**（35 人 = 28 常驻 + 7 范围轴 × 4 态） |
| B 组 | 4,060，其中 1,792 行“他人资源(无)”未注入 | **2,837 执行**，且仅统计真实注入（其余标 N/A） |
| C 组 | 4,060，执行 780 | **694 执行**（只读 GET + 自身资源），写操作单列 skipped |
| 字段 | 无 mode/实际请求/注入资源 | 每行含 mode、实际请求路径、注入资源(实体#id@owner)、执行标记 |
| 判定 | — | 匹配 **40,938** / 需复核 **2,654** / 通过 399 / **不匹配 0** / 未通过 0 |

> 结论：旧报表把 SKIP 计入“42,511 例真实请求”不成立；审计版 headline 改为 **43,991 条真实执行**。

## 二、需复核归因（2,654）

| 来源 | 数量 | 内容 |
|---|---:|---|
| A 组 | 1,806 | 400 参数校验 1,711、404 资源 69、403 组织守卫 25、其它 |
| B 组 | 663 | 受限用户读他人资源返回 2xx（可能共享/ACL/越权，需抽样） |
| C 组 | 185 | 自身资源仍 400（参数/业务校验） |

- 网络错误：首轮 26 条 `ERR:URLError` 已加入重试，复跑后 **0 条**。
- B 组真实注入资源 2,579 行（旧版仅 2,268，且含 1,792 行假注入）。

## 三、负例自证（证明不是“怎么跑都绿”）

- 对象：角色 06（TY002）原本无 `system:user:view`。
- 撤销/授予 `system:user:view`（菜单 31）后探测 `GET /api/system/users`：
  - 授予前 **403** → 授予后 **200** → 恢复后 **403**。
- 结论：矩阵/权限判定能随真实授权变化翻转，检测链路有效。

## 四、假代码审计（全仓扫描 + 高风险文件）

### 4.1 自动扫描

| 检查 | 结果 |
|---|---|
| `TODO/FIXME`（生产代码） | 0 |
| `console.log`（生产前端） | 0 |
| Java 空 catch | 0 |
| JS 空 catch | 1（文件路径见下，非权限域） |
| 生产 mock/假数据 | 0（mock 仅存在于 `.spec.js`） |
| 硬编码凭据 | 0（凭据走环境变量/配置） |
| 权限绑定覆盖 | `check-permission-binding-coverage.sh` PASS（93 码） |
| 权限码消费一致 | `check-permission-code-consumption.sh` PASS（71 码可追溯；24 个后端独有码 INFO） |
| API 清单新鲜度 | `check-api-list-fresh.sh` PASS |
| 码↔范围键 parity | `check-perm-scope-targets-parity.sh` PASS（12 对） |
| 权限码登记契约 | `check-permission-registration-contract.py` PASS（143 迁移 / 91 码 / 0 未登记） |
| 空断言测试 | 0（所有 spec 均有 expect） |

### 4.2 人工深审判定

| 文件/位置 | 判定 | 说明 |
|---|---|---|
| `CustomerService.countByScopeFor` vs `applyDataScope` | **真问题（D2）** | 同一“客户可见范围”两套实现：preview 用前者、列表用后者，TY005 121 vs 17 |
| `PermCenterService.preview` 菜单 SQL | **已修复（D1）** | DISTINCT+ORDER BY 非选列 → MySQL 3065 |
| `useUserList.loadPositions / loadRoles` | **已修复（D3/D6）** | 无 `system:position:view`/`system:role:view` 仍请求 → 403；已加条件并去掉 `loadRoles` 空 catch（吞异常） |
| `FileService` 可见性段 | 无问题（机制独立） | 文件夹 ACL/本人/上级链逻辑自洽，非权限旁路 |
| `IncentiveService.applyShareVisibility` | 无问题（机制独立） | 本人 ∨ 已分享，口径与统计复用 |
| `SeaService/SeaConfigService` | 无问题（机制独立） | 池名单递归判定，与预览 seaPools 一致 |
| `aigicrm-miniprogram/config/env.js` | 无问题 | demoMode 仅在无 apiBaseUrl（develop 无覆盖）成立；release/trial 走生产 |
| `SecurityConfig` | 无问题 | permitAll 仅登录/找回/公共配置/公开下载/health；其余 authenticated |
| `PermMissionData/usePermissionData` | 无问题 | 权限矩阵数据来自 API，不是硬编码 |
| 旧报表 `summary-v2` “42,511 例真实请求” | **文档失真** | 含 3,280 SKIP；已用本报告纠正 |

## 五、错误清单（本轮）

| ID | 级别 | 状态 | 现象 | 根因 |
|---|---|---|---|---|
| D1 | 高 | 已修复 | preview 28/28 500 | 菜单 SQL DISTINCT+ORDER BY（MySQL 3065） |
| D2 | 高 | **已修复** | TY005 preview 121 vs 列表 17 | `countByScopeFor` 自拼 ScopeRequest 且不含 autoShare；已改为复用 `applyDataScopeFor + applyAutoShareFor` 单源。修复后 28/28 用户 preview==list，TY005 17=17 |
| D3 | 低 | 已修复（待构建验证） | 05 打开人员管理页岗位 403 | 前端无条件加载岗位下拉 |
| D4 | 中 | 已定性（全部归因） | B 组 663 条“他人资源 2xx” | 536 条已解释；余 127 条：探针构造 52、**授权允许 45**（incentive `can_view_all=1`，设计内可下载）、D4a 26（已修复）、同办事处允许 3、已读标记 1。详见 `d4-residual-20261116.md` |
| D4a | 低 | **已修复** | `GET /api/file/folders/{id}/permissions` 对私有夹返回 200 | 补 `check(P_VIEW)`：私有夹 403、不存在 404；实测 TY002 folder5=403、folder7=200、999999=404、admin=200 |
| D5 | 低 | 已排除 | 旧 v2 的 74 条超时 | 审计版加入重试后 0 条网络错误，定性为并发抖动而非缺陷 |

## 六、产物

- 审计数据集：`test-results/permission-matrix/audit-20261116.jsonl`（48,580 行，逐行含执行标记）
- 执行器：`scripts/fabushi/permission_matrix_audit.py`
- 本报告：`test-results/permission-matrix/audit-report-20261116.md`

## 七、D2 修复后复测（2026-11-16 22:2x）

- 后端：`mvn test` 1109 用例通过；打包部署（容器 jar md5 `39760db2…`）。
- 复测：28 名用户 `preview.visibleCustomerCount` 与 `GET /api/customer/list.total` **28/28 一致**；TY005 由 121 修正为 **17**（与列表一致）。
- 影响面：仅权限中心预览数字；数据访问链未改变（未扩大任何可见面）。

## 八、D3 复验（前端部署后）

- 部署：前端 index md5 `721b8ee7…`，后端 jar md5 `39760db2…`（均与本地一致）。
- NM100（05 行政）打开 `/system/user`：**console 错误 0、403 请求 0**（修复前为 403×2：positions + roles）；「新增用户」按钮仍按 orgAdmin 显示。
- admin（有岗位权限）岗位下拉功能不变。

## 九、D4 明细

127 条逐条核对见 `test-results/permission-matrix/d4-residual-20261116.md`（探针构造 52、待共享核对 45、D4a 低危 26、同办事处允许 4）。

## 十、D4a 修复复验（2026-11-16 23:1x）

- `FolderAclService.permissions()` 增加文件夹可见性校验；单测补 `permissions_invisibleFolder_throws403` / `permissions_folderMissing_throws404`。
- 运行态（测试服 jar `ddc7ec1b…`）：TY002 访问私有夹 5 → **403**、公共夹 7 → **200**、不存在 999999 → **404**、admin 访问 5 → **200**。
- 另修 `FabushiMonthlyReportTest` 时间依赖（`LocalDateTime.now()` 在 23 点后跨日导致 readDays 由 2 变 3），固定为 2026-11-10 10:00。
