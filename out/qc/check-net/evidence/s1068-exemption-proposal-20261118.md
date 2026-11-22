# Sonar 规则豁免提案：java:S1068（MyBatis entity 未用字段，2026-11-18）

> 提出：QC `01-claude-1c` ｜ 依据：两轮抽样（20/20 + 200 条/43 文件）判定 ≈93%+ 为规则误报 ｜ 复核人：协调方 `01-claude-0b`

## 一、问题

`java:S1068`（Remove unused private field）在项目报 **605 条**，抽样确认绝大多数为 **MyBatis-Plus entity 映射字段**：这些字段仅经 MyBatis 反射/注解映射读写，Java 静态分析看不到引用，Sonar 误判"未使用"。

- 首轮 20/20：`BizFileGrant.java`×17、`BizFile.java`×3，全部为 entity 字段（spaceType/tenantId/subjectId/objectType/action/effect/…）。
- 扩样 200 条/43 文件：Top15 中 13 个为 `Biz*` entity（BizFileGrant 17、BizTask 16、BizFabushiBook 12、BizFolderTypeDefault 12、BizFabushiPost 10、BizFileVersion 10、BizCustomerEvent 8、BizFabushiActivity 7、BizCustomerStageAmount 7、BizFile 6、BizFabushiComment 6、BizFabushiSection 5、BizFabushiUserSetting 5）；仅 `PermActionRegistry`(7)/`GrantReader`(6) 等少量非 entity。
- **结论：≈563/605（93%+）为误报；真实死字段约 10–20 条（非 entity 文件），个案清理。**

## 二、豁免方案（推荐 A）

### 方案 A：规则级多准则忽略（推荐）

在 `sonar-project.properties` 增加：

```properties
# java:S1068 在 MyBatis entity 目录内为反射映射误报（2026-11-18 QC 抽样判定，复核人 0b）
sonar.issue.ignore.multicriteria=e1
sonar.issue.ignore.multicriteria.e1.ruleKey=java:S1068
sonar.issue.ignore.multicriteria.e1.resourceKey=**/module/*/entity/*.java
```

- 优点：只忽略该规则在该路径；entity 文件的其他规则仍生效。
- 注意：`resourceKey` 用相对 `sonar.sources` 的路径模式；需在 `sonar-project.properties` 的 sources 下验证模式匹配（本地 Sonar 实测一次确认 count 从 605 降至 ~42）。
- 非 entity 的 `PermActionRegistry`/`GrantReader` 等（约 10–20 条）不受影响，留个案清理。

### 方案 B（不推荐）：`sonar.exclusions` 排除 entity 目录

会同时丢弃 entity 的其他规则（S1104 等 27 条），不建议。

### 方案 C（不推荐）：逐条标"Won't Fix"

605 条手工标注不可维护。

## 三、复核与登记

1. 实施：0b 在 `sonar-project.properties` + `scripts/sonar-quality-gate.sh` 同步（若 gate 脚本含规则清单）；
2. 复核：QC 本地 Sonar 重扫，断言 `java:S1068` 从 605 → ≤45（非 entity 残留），其他规则计数不变；
3. 登记：登记册记「S1068 605 为误报口径，真实债务按 ~700」；豁免理由与复核人入 `docs/sonar-quality-gate.md`；
4. 风险：若未来新增真死字段在 entity 内，将被豁免掩盖——以代码评审覆盖（entity 字段变更走 PR 评审）。

## 四、提案状态

PROPOSED（2026-11-18，待 0b 实施 + QC 复扫确认）。
