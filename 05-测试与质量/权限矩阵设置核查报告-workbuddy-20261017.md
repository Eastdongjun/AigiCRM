# 权限矩阵设置核查报告（截图 vs 数据库 vs 实测）

- **核查对象**：用户提供的「系统设置 > 权限管理 > 按角色」权限矩阵截图（艾志（南京），用户文志/系统管理员，2026-10-17 09:42 截取）
- **核查方式**：4 个专家组并行——① DB 对账（生产快照库）② 后端生效链路代码审查 ③ 前端 UI 语义与版本定位 ④ nm005 账号实测验证
- **数据基准**：测试环境 MySQL = 生产快照 `aigicrm_prod_20261016`（2026-10-16 恢复）；快照含 31 用户、4 角色、95 行 role_perm_scope
- **报告时间**：2026-10-17 10:10

---

## 〇、结论速览

| 问题 | 结论 |
|---|---|
| 截图的 4 个角色是否对应真实角色？ | ✅ 完全对应（admin/0/05/06） |
| 每个设置（数据范围）是否真实落库？ | ✅ 全部落库在 role_perm_scope，持久化链路完整 |
| 权限是否真正生效（不是配了不执行）？ | ✅ 生效——代码链路完整 + nm005 实测行为与配置逐项吻合 |
| 截图显示的值与数据库是否一致？ | ⚠️ 3/4 列吻合角色 data_scope 口径；**系统管理员列与快照存在硬矛盾**，且截图 UI 版本无法在任何代码库中定位 |
| 有无配置层面的坑？ | ⚠️ 有——销售员"team 默认被 self 通配覆盖"、销售总监两处与"全部"展示矛盾、1 行永不生效的死配置 |

**一句话**：权限体系本身是真实生效的（实测验证通过），但截图中"系统管理员=仅本人"与生产快照数据矛盾，且截图 UI 版本来源不明——这两点需要向截图来源核实。

---

## 一、截图逐项拆解

### 1.1 截图的矩阵结构

每格 = 功能勾选（能做什么）+ 范围下拉（对谁的数据）+ 锚点（按人/按组织）。截图展示的是**范围**部分（功能勾选需点开单元格才可见）。

**行（11 个权限模块）**：工作台、客户管理、文件管理、四项激励、提醒管理、公海管理（含 5 子项：捞取记录/公海池view/公海配置查看/公海配置编辑/公海导入）、审批管理、统计分析、积分管理、任务管理、系统管理

**列（4 个角色）及截图显示值**：

| 角色 | DB 角色 | 角色 data_scope | 截图显示值 | 对应档位推断 |
|---|---|---|---|---|
| 销售员 | 06 (id=11) | team | 按人 | team（本人及下级） |
| 系统管理员 | admin (id=1) | all | 仅本人 | self |
| 销售总监 | 0 (id=10) | all | 全部 | all |
| 行政 | 05 (id=9) | office | 仅本人办事处 | office（本办事处及以下） |

### 1.2 数据库真实配置（快照 2026-10-16）

role_perm_scope 全表 95 行 = ROLE 级 74 + USER 级 21 + POSITION 级 0：

| 角色 | 通配行 `* *` | 模块级配置 |
|---|---|---|
| admin | **无** | 仅 customer 模块 7 行（view/add/edit/delete/export/import/date 全 = all） |
| 0 销售总监 | **无** | 8 模块 28 行：customer/dashboard/score/sea/statistics/system 全 all；**incentive:add=self**；**file:acl=region**（本大区） |
| 05 行政 | **office** | customer 7 行 office；dashboard=region；file 混 all/region；incentive 混 all/region；sea:view=office |
| 06 销售员 | **self** | dashboard=self；incentive 混 all/self；**sea:view=office**；score:ranking=all（死配置，见问题 P2-4） |

USER 级覆盖（按用户 tab 的口径，21 行）：用户 11（NO001，组织管理员）= customer 全 all；用户 40/42/44/47/54=team；**41=office**；43/61/62=custom（自定义组织锚点）；69=dashboard:view=all。

### 1.3 功能勾选（复选框）落库情况

sys_role_menu（角色→菜单→功能权限码）：

| 角色 | 菜单数 | 06 销售员的 40 个功能码明细（示例） |
|---|---|---|
| admin | 81 | — |
| 0 | 78 | — |
| 05 | 48 | — |
| 06 | 40 | customer:view/add/edit/delete/export/import/date:edit；file:view/upload/delete/edit/share/folder:manage/acl:manage；incentive:view/add/edit/read-stats；sea:view/claim；approval:view/approve/reject；statistics:view；score:view；reminder:view；dashboard:view；task:view/manage；**无任何 system:*** |

---

## 二、逐格对账矩阵

判定口径说明：生效优先级为 **USER > POSITION > ROLE**，同主体内**精确 (module,action) > 通配 `*`**，无配置回退角色 data_scope（查看类动作）；编辑/删除类默认 self。

| 模块 | 06 截图=按人 | 0 截图=全部 | 05 截图=仅本人办事处 | admin 截图=仅本人 |
|---|---|---|---|---|
| 工作台 | self（通配）✗ | all ✓ | region ✗ | 回退 all ✗ |
| 客户管理 | 回退 team（无配置行）≈按人 ✓存疑 | all ✓ | office ✓ | all ✗ |
| 文件管理 | 无配置→回退 ✗ | all ✓ / file:acl=region ⚠️ | all+region 混 ✗ | 无配置→回退 all ✗ |
| 四项激励 | 混 all/self ✗ | incentive:add=self ✗ | 混 all/region ✗ | 无配置 ✗ |
| 提醒管理 | **无任何配置行**→回退 | 无配置行→回退 all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |
| 公海管理 | sea:view=office ✗ | all ✓ | office ✓ | 无配置→回退 all ✗ |
| 审批管理 | 无配置行→回退 | 无配置行→回退 all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |
| 统计分析 | 无配置行→回退 | all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |
| 积分管理 | score:ranking=all（**永不生效**） | all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |
| 任务管理 | 无配置行→回退 | 无配置行→回退 all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |
| 系统管理 | 无配置行→回退 | system:dept=all ✓ | 无配置行→回退 office ✓ | 无配置行→回退 all ✗ |

**对账结论**：
1. 截图每列值全模块统一 → 截图展示的是**角色级 data_scope 口径**（而非模块级配置）
2. 3/4 列吻合：销售员 team≈按人（存疑，见问题 P1-2）、销售总监 all=全部 ✓、行政 office=仅本人办事处 ✓
3. **系统管理员列是硬矛盾**：快照中 admin 全链路（data_scope=all、customer=all、无通配行）**不存在任何 self 配置**，截图却显示"仅本人"。两种可能：① 生产在 10-16 快照之后（10-17）改过配置；② 截图来自与快照不同的数据源/UI 版本
4. 截图 UI 版本**无法定位**：「仅本人办事处」「不配置默认」两个标签在本地 git 全历史、本地工作区、生产 JS（108 个 chunk 全量下载）、测试环境 JS（109 个 chunk）中**均 0 次出现**；生产 bundle 自今晨抓取后未变动（index-BVJbvEj9.js）。截图最可能来自未入库的构建或设计稿——**需向截图来源核实环境与版本**

---

## 三、生效链路验证（代码级，本地 HEAD = 生产代码 + 修复）

### 3.1 持久化 → 即时生效（配置保存侧）

```
PUT /api/system/perm-scopes/{roleCode}
 → RolePermScopeController @PreAuthorize('system:config:edit') + requireAdmin() 防提权
 → RolePermScopeService.updateScopes：scope_level 合法值校验（all/custom/region/office/dept/dept_and_children/org/org_and_children/team/self）+ anchor 仅 OWNER/ORG
 → UPSERT（唯一键 assignee_type+key+module+action+anchor）
 → 即时生效：evictAll() + bumpAllPermVersions()（全表 perm_version+1）
 → 登录时比对 perm_version 不一致即重建权限（Redis TTL 10min，最长残留 1 个请求）
```

### 3.2 运行时过滤（请求侧）

```
登录：loadScopeConfigs（USER→POSITION→ROLE 装载序）→ SecurityUser.scopeConfigs 随上下文缓存
请求：@PreAuthorize(功能码) → Service 列表 → applyDataScope
     → OrgScopeResolver.resolveScope（内存匹配：USER>POSITION>ROLE 首主体优先，主体内精确>通配，无配置回退 data_scope）
     → OWNER 锚点：wrapper.in(owner_id, visibleUserIds)；ORG 锚点：wrapper.in(dept_id)
     → all：不过滤；空集：owner_id=-1（不可见而非放行）
```

### 3.3 六档范围执行证据（全部有真实实现，无"定义了没实现"的档位）

| 档位 | 实现位置 | 判定 |
|---|---|---|
| all | OrgScopeResolver resolveSingle:387 | ✅ |
| region | resolveByAncestorType(TYPE_REGION=2) | ✅ |
| office | resolveByAncestorType(TYPE_OFFICE=3) | ✅ |
| dept | resolveDeptTree 本部门及子部门 | ✅ |
| team | resolveTeam，manager_id 链递归 ≤5 层 | ✅ |
| self | List.of(userId) 仅本人 | ✅ |

default 分支收敛 self（fail-safe）；role_perm_scope 读取失败统一抛 503（不静默回退）；租户/集团边界取不到即拒。**fail-open 风险仅 3 处低危**：保存端 null 配置行静默跳过、DataScopeGuard 为死代码（治理冗余）、两个开关 30s 缓存。

### 3.4 功能权限与数据范围的关系

两层正交、串联无缺口：**功能码管"能不能做"**（sys_role_menu → @PreAuthorize），**矩阵管"对谁的数据"**（role_perm_scope → SQL 过滤）。nm005 实测的 29 个功能码确由其角色菜单产生。

---

## 四、实测验证（行为级，nm005/王磊磊，销售员）

测试环境 = 生产快照，nm005 登录成功（userId=51，dept 6 内蒙办销售部，org 6，无 USER 级覆盖、无岗位 → 全走角色默认）：

| 实测项 | 配置预期 | 实际行为 | 判定 |
|---|---|---|---|
| 功能权限码 | 29 个无 system | /auth/info 一致；/system/users → **403** 拒绝 | ✅ |
| 客户数据范围 | 通配 self（覆盖 team） | 列表 total=**11** 全本人（DB：本人 13 条-2 条公海）；同部门他人 90 条不可见 | ✅ self 生效 |
| 边界防护 | — | 查看/编辑他人客户 259 → **400** 无权，备注未写入 | ✅ |
| 归属保护 | owner 强制本人 | 新建客户 id=619，DB owner_id=**51** | ✅ |
| 删除权限 | customer:delete 在码内 | DELETE 619 → 200 软删落库 | ✅ |
| 公海范围 | sea:view=office | total=**16**，与 DB org6 公海客户 ID **逐一相等**；org4/38/44 的 11 条不可见 | ✅ office 生效 |

**关键实测发现**：nm005 的客户可见集 = 仅本人（self），**不是**角色 data_scope 的 team——因为 role_perm_scope 通配 `06 * * self` 的优先级高于角色 data_scope=team。配置口径与运行时行为在此处分叉（见问题 P1-2）。注：nm005 无下属，team 档也会退化为 self，此账号无法区分两种路径，但行为与通配 self 吻合。

---

## 五、问题清单

### P1（需排期处理/核实）

1. **截图"系统管理员=仅本人"与生产快照矛盾**：快照（10-16）中 admin 全链路无 self 配置，截图（10-17）显示仅本人。需核实：生产是否在快照后改过配置，或截图来自其他数据源。
2. **销售员 team 默认被 self 通配覆盖**：角色 data_scope=team，但 `06 * * self` 运行时优先 → 实际仅本人。若产品意图是"销售员看本人+下级"，当前配置达不到；截图若展示 team 口径则更添误导。建议：明确 06 的意图后二选一——删通配行走 team，或改角色 data_scope=self。
3. **截图 UI 版本无法定位**：「仅本人办事处」「不配置默认」标签不存在于任何代码库（git 全历史/工作区/生产 JS/测试 JS）。若存在未入库的生产构建，属发布治理缺口，需收编入库；若为设计稿，需与实现对齐。
4. **销售总监两处与"全部"展示矛盾**：0 的 incentive:add=self、file:acl=region。若属有意设计（总监不直接录激励、文件 ACL 按大区）应在矩阵上明示，否则属配置遗漏。

### P2（建议改进）

5. **死配置行**：`06 score:ranking=all`——score:ranking 非菜单权限码，该行永不生效。
6. **reminder/approval/task 三模块无任何角色级 scope 配置**：全靠回退默认（view=角色 data_scope），若产品有细分意图需显式配置。
7. **custom 档 UI 选不出**：3 个用户（43/61/62）配了 custom（自定义组织锚点），但前端下拉无此选项——只能通过 API 配，UI 不可见不可改。
8. **USER 41=office 与同组其他用户（team）口径不一**：按用户 tab 的团队口径不统一，需确认是否有意。
9. **perm_scope_org 2 条悬空记录**（scope_id=4/11 对应行已删）；sys_role_rule / sys_role_org / perm_scope_field 三表全空（治理对象冗余或预留）。
10. **admin 仅配 customer 模块**：其余 10 模块全靠 data_scope=all 兜底——配置覆盖率低，意图不显式。

---

## 六、总结

- **权限是否生效：是**。从配置保存（校验+即时生效）→ 运行时解析（USER>POSITION>ROLE，精确>通配）→ SQL 过滤（六档全实现）→ 实测行为（nm005 六项全吻合），整条链路真实闭环，无 fail-open 放大面。
- **截图中的设置：4 角色与真实角色一一对应，数据范围配置真实落库**；3/4 列与角色 data_scope 口径吻合。
- **两个待核实点**：① 系统管理员列的 self 显示与快照矛盾（快照后变更 or 数据源不同）；② 截图 UI 版本来源不明（标签不在任何代码库）。
- **配置层面有 4 处 P1 级口径分叉/矛盾**（team 被 self 覆盖、销售总监两处、截图矛盾、UI 版本），建议按第五节逐项裁决后处理。

---

## 附录 A：角色×数据范围速查（快照 2026-10-16）

| 角色 | data_scope | 通配 | 客户 | 公海 | 激励 | 文件 | 其他 |
|---|---|---|---|---|---|---|---|
| admin 系统管理员 | all | 无 | all | 回退 all | 回退 all | 回退 all | 回退 all |
| 0 销售总监 | all | 无 | all | all | add=self，余 all | view=all，acl=region | 全 all |
| 05 行政 | office | office | office | office | 混 all/region | 混 all/region | dashboard=region |
| 06 销售员 | team | **self** | 回退 team | office | 混 all/self | 回退 team | dashboard=self |

## 附录 B：复现命令

```bash
# DB 对账（只读）
ssh -o BatchMode=yes aigicrm-test 'set -a; . ~/aigicrm/.env; echo "SET NAMES utf8mb4; SELECT * FROM role_perm_scope ORDER BY id" | docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"'

# nm005 实测
source /tmp/wb-audit/tokens.env
curl -s "http://111.229.37.220/api/auth/info" -H "Authorization: Bearer $TOKEN_NM005"
curl -s "http://111.229.37.220/api/customer/list?current=1&size=500" -H "Authorization: Bearer $TOKEN_NM005"
curl -s "http://111.229.37.220/api/sea/list?current=1&size=500" -H "Authorization: Bearer $TOKEN_NM005"

# 生产前端版本核对
curl -s -k "https://www.aigicrm.com/" | grep -oE 'index-[A-Za-z0-9_-]+\.js'
```

*报告生成：2026-10-17 10:10，workbuddy 权限矩阵专项核查（4 专家组并行 + 本人复核）。测试数据全部带 WB 前缀并已软删清理。*
