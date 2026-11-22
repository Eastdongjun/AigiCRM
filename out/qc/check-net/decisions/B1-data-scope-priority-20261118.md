# B1 口径拍板包：数据范围优先级（2026-11-18）

> 目的：B1 未决会阻塞 §8 组合语义的闭环；本包把事实、选项、影响摊开，供用户一次性拍板。
> 依据：`evidence/scope-batch0-20261118/`、登记册 §9 DEF-1/F2/F3、`check-scope-axis-contract.sh` 18 轴。

## 一、现状事实（新库 `aigicrm_prod_20261118`）

- 28 活跃用户 × 18 轴全部有授权（none=0）；范围类型只出现 all/self/team/office/custom，9 种缺失（需夹具）。
- `customer.auto_share = {"superior":true,"admin":true,"roles":[],"users":[]}`；`perm.org.leader.see.org=true`；`perm.superior.see.subordinate` 见 config。
- **DEF-1**：uid 41 TY005 显式 USER/office，配置应见 121；经 auto_share 后最终 17，**少 104**。用户已裁决：office=全办事处，auto_share 不应再收窄已勾选范围。
- **F2**：uid 47/54 的 `data_scope_override=team` 未生效；ROLE 通配 self 抢先（旧库结论，新库待复验）。
- **F3**：`auto_share.admin=true` 是死配置（`getAutoShareOwnerIdsCore` 未消费）。
- 当前 oracle 语义（旧实现）：`winning_config` 顺序 USER > POSITION > ROLE；无显式档时用角色 `data_scope`；`apply_auto_share` 只对非 all 档按 superior 收窄。

## 二、待拍板的四个问题

1. **显式档 vs 角色默认**：USER/ROLE 显式 `role_perm_scope` 档是否优先于角色 `data_scope` 默认？
2. **override vs 角色档**：`sys_user.data_scope_override` 是否最高优先（应压过 ROLE 通配）？
3. **auto_share 方向**：它是"扩大可见"还是"在配置范围内收窄"？DEF-1 已答"不得收窄已勾选范围"。
4. **auto_share.admin 死配置**：实现为"管理员可见下属范围"，还是移除配置？

## 三、选项

### A（推荐）显式优先 + auto_share 只扩不缩
- 优先级：`USER override` > `USER 档` > `ROLE 档` > 角色 `data_scope` 默认。
- auto_share：只在基础集合上**追加**分享/下属可见，不 shrinking 显式档；exempt 用户不受影响。
- F3：`admin=true` 实现为"管理员可见其管理范围内客户"（与 admin 角色 all 区分），或若产品不需要则移除该键并登记。
- 结果：TY005 回到 121；47/54 override 生效；其余 26 用户回归比对；负例（未勾选）仍 0。

### B 保持现状
- auto_share 收窄显式档；override 被 ROLE 通配抢先；admin 死配置保留。
- **与 DEF-1 用户裁决直接冲突**；不推荐。

### C 其他（用户自定义）
- 例如：显式档优先但 auto_share 可收窄 custom；或 override 仅对 self/team 生效。

## 四、影响与验收

| 项 | A 的影响 |
|---|---|
| TY005 | 17 → 121（+104） |
| 47/54 | override team 生效（需复验具体集合） |
| 其余 26 用户 | 期望集不变（回归） |
| 负例 | 未勾选用户仍 fail-closed 0 条 |
| 组合语义 | 可按 A 出具"单一机制 + 组合"两档报告；B1 转 C 后定稿 |

- 验收：新库/测试库 28 用户 expected==actual（列表/counts/导出/选择器同集合）；DEF-1/F2 复验；负例 0。
- 修复归属：实施方（协调方 0b）；QC 复验。

## 五、需要你拍板

- 选 A / B / C；
- 若选 A：`auto_share.admin` 实现还是移除？
- 是否同意"USER override > USER 档 > ROLE 档 > 角色默认"的优先级？

拍板后写入登记册 §10，并作为 §8 组合语义的 oracle 依据。

## 六、已拍板（2026-11-18 用户）

- **选 A**：`USER override > USER 显式档 > ROLE 显式档 > 角色 data_scope 默认`；auto_share **只扩不缩**。
- **移除 `auto_share.admin` 死配置**（F3）：连同迁移/文档/契约清理，登记"已移除"。
- B1-A 期望集已重算：`evidence/scope-batch0-20261118/expected-customer-view-b1a.tsv`；delta 仅 **TY005 17→121**（47/54 override 当前无可见差异）。
- 实施方（0b）修 `resolveScope/applyAutoShare` + override 优先级；QC 按 B1-A oracle 验收。
