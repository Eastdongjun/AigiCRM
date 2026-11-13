# 权限内核切流/回滚 Runbook（WP8 · 二轮审查修订）

> 施工规格书 WP8 交付物。前置：**data_scope_override 变宽名单业务签字未完成前，禁止执行切流**（规格 §10 业务 Gate）。
> 变宽名单：3 人（毛颖娜 NM100 / 李艳梅 TY100 / 肖赟 TY200，override=custom 且角色档=office），证据见中央执行目录
> `runs/perm-kernel-20261113/evidence/wp2/override-analysis.md`。

## 动作轴声明（P0-5 方案二，二轮审查定案）

**动作（谁能做什么）权威 = `sys_role_menu`（现有角色菜单矩阵），本期不迁移**；
`sys_perm_grant` 承载**数据范围**（对谁的数据，槽码行）。规格"一张授权表"的完整含义
（动作+范围同表）留待 P3 动作轴迁移批，本期以"范围表"运行。
配套保障：角色勾选动作时 `SysRoleService.saveRoleMenus` 双写槽码授权行
（scope=匹配 role_perm_scope → 角色 data_scope → SELF），新轨切流后新绑码角色范围不空。

## 现状（二轮修复后）

- 双轨：旧轨（role_perm_scope + dataScopes 并集）为**权威**；新轨（sys_perm_grant）回填+双写保鲜。
- 读开关：`sys_config` 键 `perm.read.<module>`（缺省/old/脏值 = 旧轨），按模块灰度；
  `new` 时 resolveModuleUsers/**resolveScope**（客户列表/详情）/CustomerVisibilityService（统计）
  同走内核——卡片=列表=详情=导出 同轨。
- 维度补齐：新轨 OWNER 锚=SELF/团队行本人 ∪ 部门档在职用户；ORG 锚=部门档 ∪ 用户所属部门
  （P0-1 修复，切 new 不再"本部门数据变空"）。
- CUSTOM：精确按行 scope_config（orgId+includeChildren），false 只到本组织（P0-3 修复）。
- 对账端点：`GET /api/system/perm-scopes/reconcile?sample=50`（admin）——**9 槽全模块**
  （customer/file/approval/reminder/sea/incentive 统计 + task/score/system 集团收口）逐用户双轨对比。
  旧轨基线走 `resolveScopeLegacy`（强制绕过双轨守卫）——**切 new 后对账仍能对比真旧轨**，
  diffCount=0 不是切流假象（P1-6 修复）。
- 版本号：**所有权限写点全覆盖**（三轮修复）——单用户 evict（人员角色/数据范围/部门变更）与
  批量 evictByRole/evictByPosition（角色菜单/岗位变更，收敛为一次自增）经汇聚 → epoch+1 →
  双端下一次请求自动拉新。管理员改角色勾选后，PC/MP 无需刷新即可看到按钮增减。
- 任职/闭包同步同汇聚触发（人员/组织写点后 sys_employment/sys_org_closure 自动保鲜，
  含新建用户/新建/删除/停用部门）。

## 挂账（切流前须补 / P3）

- `sys_position_role`：本期无管理入口（动作轴未迁移），表就绪待动作轴批启用，当前零写入非死表误用。
- `biz_task` 的 assignee_id 维度 / `biz_approval` 审批人列：sys_resource 列声明的对象级细化，
  随对应模块切 new 前补（当前 OWNER 锚与旧轨等价，无行为差）。
- `customer:export` 经视图别名跟随 customer:view 授权（P2-12 修复，运行期已处理）。

## 切流步骤（按模块灰度，禁止大爆炸）

1. **备份**：`mysqldump` role_perm_scope / sys_role / sys_user_role / sys_perm_grant（带时间戳）。
2. **空库重放**：`bash scripts/replay-empty-db.sh` 全绿。
3. **对账 0 差异**：`curl /api/system/perm-scopes/reconcile?sample=200` → `diffCount=0`。
   差异非零 → 逐行看 `onlyOld/onlyNew`，先修数据（多数=双写缺口/历史脏配置），禁止带差异切流。
4. **变宽签字**：override 3 人名单逐人确认（A 接受 / B 换角色 / C 改角色范围 / D 暂缓只读）。
5. **翻开关**（第一个模块 = customer）：
   ```sql
   UPDATE sys_config SET config_value='new' WHERE config_key='perm.read.customer';
   ```
   观察窗口 ≥24h：403 率 / 错误率 / 慢查询（ScopeQuery 大集合告警日志）/ 用户工单。
6. **逐模块推进**：customer → file → approval/reminder/task → sea/incentive/score → system。
   每模块重复步骤 3-5。
7. **旧表只读**：全模块 new 且稳定一周后，`role_perm_scope` 仅读（管理端保存仍双写，不删）。

## 回滚（任一模块）

```sql
UPDATE sys_config SET config_value='old' WHERE config_key='perm.read.<module>';
```

- 读开关 30s 缓存 + epoch 感知 → 最迟一个请求周期回到旧轨。
- 旧表全程未删未改权威地位 → 回滚零数据风险。
- 若需版本号强刷双端：管理端保存任一范围配置（epoch bump）。

## 删旧表（P3 · 单独窗口，本 runbook 不含执行令）

前置：全模块 new 稳定 ≥1 个月 + `role_perm_scope` 只读期零故障 + 备份归档 NAS。
执行：`DROP TABLE role_perm_scope`（单独 Flyway 迁移 + FROZEN 登记）。
同批退役（规格 §5 文件清单，逐一代码清理后）：`PermissionRuleEngine / FieldFilterAdvice / ActionGuardAspect`。

## 监控与告警

- `X-Perm-Epoch` 分布跳变频率（异常频繁=写点风暴）。
- 403 审计（`PERMISSION_CHANGE`/`ACCESS_DENIED` 事件）切流窗口对比基线。
- ScopeQuery `范围集合过大` 告警 → WP8 性能批闭包 join。
- 双写失败日志（`双写失败` 前缀）→ 授予表异常。
