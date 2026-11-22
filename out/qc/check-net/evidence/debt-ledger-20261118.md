# 存量债务台账（P0-5 判据外，2026-11-18 终局快照）

> 终局 HEAD `4a34bf06`，本地 Sonar 服务端数据。**不属 P0-5 判据**（用户口径=Blocker/Critical=0，已达成）；本台账为后续分批治理依据。

## 一、总量与类型

| 类型 | 数量 |
|---|---|
| CODE_SMELL | 1,264 |
| BUG | 41 |
| VULNERABILITY | **0** |
| **合计未解决** | **1,305**（其中 S1068 605 条经抽样判定为规则误报，**真实债务按 ~700 计**） |

严重级分布：MAJOR 933 / MINOR 371 / INFO 1 / CRITICAL 0 / BLOCKER 0。

## 二、按规则 TOP（治理优先级）

| 规则 | 数量 | 含义 | 处置建议 |
|---|---|---|---|
| `java:S1068` | 605 → **135**（e1 生效）→ 待 e2/e3 | 未使用的私有字段 | 抽样判定 MyBatis entity 误报；**`1b241a19` 实施 e1（`**/module/*/entity/*.java`）后 605→135，其他规则不变、gate OK**；剩余 135 = dto 46 + security 43 + common/auth 13 + 其他 33，已提案 e2/e3 扩 `**/module/*/dto/*.java` 与 `**/security/*.java`（预期 ~46 留个案） |
| `javascript:S6582` | 206 | 建议可选链 `?.` | 纯风格，低风险批量修（前端 340 测试可回归） |
| `java:S125` | 72 | 注释掉的代码 | 逐文件清（拆分过程已顺手清若干） |
| `javascript:S2681` | 39 | 块语句末语句位置 | 风格，低风险 |
| `javascript:S3358` | 27 | 嵌套三元 | 风格，可批 |
| `java:S1104` | 27 | public 字段 | 多为 DTO 常量；评估后注释或改 private |
| `Web:S6853` | 21 | 前端可访问性 | 低优先 |
| `java:S6813` | 18 | 字段注入 | 重构级，建议不批（Spring 惯例，可规则抑制登记） |
| `Web:TableHeaderHasIdOrScopeCheck` | 14 | 表头 a11y | 低优先 |
| `java:S1172` | 14 | 未用参数 | 低风险，顺手清 |
| `java:S107` | 12 | 参数过多（>7） | 重构级 |

## 三、41 个 BUG

- 全部 MAJOR/MINOR（无 CRITICAL/BLOCKER）；前期已逐条定级（见 `evidence/sonar-20261118/README.md` §十：4 条防御性修复、其余债务/误报）。
- 分布：前端可访问性（表头 id/scope 14、鼠标键盘等价 11）+ scripts 排序 4 + 后端存量（S3077/S2184 等）。

## 四、与 P0-5 的关系

- P0-5 判据 = Blocker/Critical=0（**已达成**）；本台账 1,305 条均非阻断级。
- 建议由协调方按「S1068 抽样 → 风格批 → 重构级登记」三段推进；**不建议**在发布前清（回归面大、收益低）。

## 五、备注

- `java:S1068 605` **已两轮抽样判定**：首轮 20/20 全为 MyBatis entity 字段；扩样 200 条覆盖 43 文件，Top15 中 13 个为 `Biz*` entity（BizFileGrant 17、BizTask 16、BizFabushiBook 12、BizFolderTypeDefault 12、BizFabushiPost 10、BizFileVersion 10、BizCustomerEvent 8、BizFabushiActivity 7、BizCustomerStageAmount 7、BizFile 6、BizFabushiComment 6、BizFabushiSection 5、BizFabushiUserSetting 5），仅 PermActionRegistry(7)/GrantReader(6) 等少量非 entity。**判定：≈93%+ 为 Sonar 不识别 MyBatis 反射映射的规则误报**；建议规则豁免/范围排除并登记理由，**不改代码**；非 entity 的少量（约 10-20 条）可个案清理。
- 报告口径：本快照与 `closure-20261118.md` 同源（同一 Sonar 实例、同一 HEAD）。

## 2026-11-19 增量（QC fabushi-upload-security-debt）

| 规则 | 数量 | 位置 | 定性 | 处置 |
|---|---|---|---|---|
| javascript:S6582 | 10 | scripts/test-fabushi-upload-limits.js（F2 新增 case4/5 区段） | 风格建议（`&&` 链→可选链 `?.`） | **保留不清**（实施方 2026-11-19 选择：避免机械替换扩大 diff 半径；MINOR 不在 gate 判据）。后续触碰该文件时顺手清 |
