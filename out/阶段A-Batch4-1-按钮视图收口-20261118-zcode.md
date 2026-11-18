# 阶段 A · Batch-4 #1 按钮视图收口（2026-11-18，Builder：zcode）

> 依据：QC Batch-4 工单第 1 项（切 new 硬前置主项）。红线=每动作按钮显示与 API 判定 parity，三模式一致。

## 一、实现

### 1. `FileGrantService` 多动作批量判定（新增）
- `decideFilesMulti(files, actions, userId)` → `Map<fileId, Map<action, Decision>>`；
- `decideFoldersMulti(folders, actions, userId)` → 夹侧同构；
- 一次 load（动作集=各请求动作的归并词并集）、org/folder 祖先缓存复用 decideFiles 模式（无 N+1）；
- mode=legacy 恒空 Map（按钮走旧链=现状）；
- **归并语义与 decideFiles 完全一致**：`grant.action ∈ actionsFor(请求动作)` 即服务该动作（view grant 服务 download、manage/* 服务全部）——初版按 grant.action 分组遍历**漏了这语义**，被 H2 parity 测试抓出（t3 download 破裂）后修正为全 grants 遍历按 covering 集判定。

### 2. `FolderPermissionService` 三处接入
- **permsForFiles**：DENY(view) 行隐藏保留；hit 循环 per-action——引擎 DENY 压按钮（矩阵/旧表亮也压，与判定链同序）、引擎 ALLOW 直接亮、否则矩阵/旧表照旧；
- **permsForBatch**（夹行 7 文件动作）：decideFoldersMulti 替代原逐夹 decideFolder(P_VIEW)；per-action DENY 压/ALLOW 亮同上；
- **folderActionsForBatch**（夹自身 rename/delete）：check() 结果之上引擎 ALLOW 补亮（DENY 时 check 已拒，无双亮）。

### 3. 三模式一致性（设计）
- legacy：enginePerms 空 → 行为=现状（t4 断言零引擎查询）；
- dual：引擎命中 ∪ 旧表/矩阵（按钮=引擎 ALLOW 也亮——修复 A2 发现①的「按钮与判定脱节」：dual 下引擎放行但按钮不亮的场景消除）；
- new：引擎命中 ∪ 矩阵（旧表空集）——修复 new 模式按钮收窄（A2 D 阶段发现：100/102 public 6→2）。

## 二、测试：`FolderPermissionParityTest`（H2 混合真链）6/6

t1 new 收窄修复（view/rename/share 引擎亮）/ t2 per-action DENY 压制 / **t3 六动作按钮⇔checkFile 逐项 parity** / t4 legacy 现状 / t5 批量输出 / t6 夹行命中与压制。

**工程教训留痕（报告与测试头注释双记录）**：初版用 Mockito mock FileGrantService + wrapper 参数提取动作集——MP 的 `MPGENVAL` 参数形态差异令提取不可靠（假绿/假红两连），转 **H2 真链（biz_file_grant/biz_folder_type_default 真 mapper 真 SQL 过滤）+ 其余协作者 mock** 的混合形态后 parity 永真——且立刻抓到归并语义真缺陷。此后权限引擎类 parity 测试以此形态为模板。

## 三、门禁

| 门禁 | 结果 |
|---|---|
| mvn test | **1187/0/0（120 套件=119+1，+6 parity）** |
| 契约 | 191 PASS / 0 FAIL |
| 空库重放 | ✅（无新迁移） |
| typecheck | PASS 537/537 |

## 四、说明
1. **测试服 new 模式按钮面对账**（工单交付项）：本地 parity 已六动作逐项断言；运行时对账（采样 new vs dual diff=0）请并入 QC 第 7 轮测试服抽核（与 A3/A4 模式一致）——测试服数据集（3 角色×3 空间）在窗口还原时已清，重建后采样即可；若 QC 希望我方先自跑一遍说一声。
2. load 两次说明：permsForFiles 内 decideFiles(view 行隐藏预载) + decideFilesMulti 各一次——仍 O(1)/页（t5+既有 decideFilesLoadsGrantsOnceForPage 覆盖），合并为一次的优化留待后续（避免本轮动 decideFiles 稳定路径）。
