# 阶段 A · Batch-3 实现与验证（2026-11-18，Builder：zcode）

> 依据：用户四项拍板（经 QC 第 2 轮结论文件 §三/§四 转达）+ QC Batch-3 工单 4 项。
> 生产：零接触；测试服：仅副本演练（临时库用毕 DROP），未部署应用。
> ⚠️ 本文 §二含一处**对工单口径的必要修正**（红线优先），请 QC 第 3 轮重点复核。

---

## 一、工单执行结果

### 1. typecheck 存量修复 ✅
- 根因：`PermScopeManager.dependency.spec.js` 顶层 `import { __test }` 在类型层读**真实模块**导出（vi.mock 工厂提供 `__test`，vue-tsc 不识别）+ 两处 vi.mock 函数的类型断言缺失。
- 修法（零抑制指令、不弱化测试）：`__test` 句柄改 `vi.hoisted` 传递（工厂内 `Object.assign(handle,…)`）；mock 调用改**变量式 JSDoc 断言**（`const m = /** @type {any} */ (fn)`——TS 官方 JS 写法；行内前缀断言不生效且触发 ASI 陷阱，已实证后弃用）。
- 结果：`check-ui-typecheck.sh` **PASS（537/基线 537，0 新增）**；该 spec **8/8 用例通过**。

### 2. 双写故障注入集成测试 ✅（用户拍板 #4）
- 新增 `LegacyFileGrantSyncTxTest`（H2 内存库 MODE=MySQL + **真实 DataSourceTransactionManager** + SqlSessionTemplate 事务绑定连接 + 真实 `FolderTypeDefaultService`/`LegacyFileGrantSyncService`，仅 spy 注入故障）：
  1. 正向：saveGrant → 旧表 1 行 + 新表 2 行 dual_write（`ftd:` 前缀，真实 SQL 全链）；
  2. **syncUpsert 抛错 → 异常传播 + 旧表写入同事务回滚（行数 0）**——声明性 @Transactional 转为实证；
  3. syncDelete 抛错 → 旧表删除回滚（行仍在）。
- pom 新增 `com.h2database:h2`（test scope，版本 Boot BOM 管理）。

### 3. 重复组合并迁移 V2026.11.18.3 ✅（含口径修正，见 §二）

### 4. 全量回归 ✅
| 门禁 | 结果 |
|---|---|
| mvn test | **1160/0/0 skip（117 套件 = 116 基线+1 新）**，exit 0 |
| 契约 | **191 PASS / 0 FAIL** |
| npm test / build | **47 files 340/0**；build exit 0 |
| typecheck | **PASS（537/537，0 新增）** |
| 空库重放 | **✅ 全链 153 支 0 fail（含 V2026.11.18.3）断言码 8/8** |

---

## 二、M3 口径修正（重要，请 QC 复核）

**工单原口径**：真重复组合并——perms 取并集、expires 任一 NULL→NULL 否则 MAX。

**演练实证的矛盾**（合成夹具：同完整 scope 两行，一行 `view`(expire NULL) + 一行 `download`(expire 2026-12-31)）：
- V2026.11.18.2 迁移的 expire 规则是**动作组级**（GROUP BY 含 action）：view 组→NULL、download 组→2026-12-31；
- 工单口径的合并是**行级**（单行单 expire）：并集行 `view,download` expire=NULL → download 动作的限时被**扩大为永久**；
- 对账实测 **expire_diff=2**（组1/组2 双双漂移）——违反工单红线「合并后权限面=原并集（语义不变）」。

**修正（红线优先）**：仅合并**规范化 perms 等值**的组（规范化=saveGrant 同款归一：edit→rename+replace、词表序重排）。此时并集=自身、行级 expire 归一与迁移动作组级规则**数学一致**；perms 不同的真重复组**不自动合并**，附清单 SQL（M3 文末注释）供切换窗口人工处置。

**修正后演练全绿**（快照 62 行+8 夹具行，完整序列 M1→夹具→M2→M3）：
| 断言 | 结果 |
|---|---|
| 对账 #1（M2 后，含夹具） | 四数 0（186=186） |
| M3 合并语义（夹具） | 组201（perms 等值+任一永久）→1 行 `view,download`+NULL ✅；组202（等值+双值）→1 行 `view,upload`+MAX(2027-06-30) ✅ |
| 红线 | 组203（perms 不同）保持 2 行+入清单 ✅；组204（异 scope）保持 2 行 ✅ |
| 对账 #2（M3 后） | **四数仍 0（186=186）** ✅（修正前为 expire_diff=2） |
| 行为快照（展开集合 EXCEPT 对比） | only_before=0 / only_after=0 ✅（语义不变） |
| 冲突组 | 38→34（两个 perms 等值组退出）✅ |
| 幂等 | 重跑 M3 **0 行受影响** ✅；终态对账仍 0 |
| 清理 | 副本 DROP、暂存删、源库 62 行未动 ✅ |

**关键数据事实**：测试快照 32 冲突组**全部为异 scope 组（真重复=0）**——M3 在快照上是空操作，合并逻辑靠夹具验证；**生产 43 组的真重复分布未知**（§2.10 口径本身是映射键，不区分 scope），切换窗口跑清单 SQL 实测后处置。

**顺手确认（工单要求）**：旧入口 `saveGrant` select-before-write 按 (folder_type, object_type, folder_id, subject) upsert——**不会再产生同 scope 重复**（update 路径天然去重；跨 scope 亦为覆盖语义，属既有行为未动）。

---

## 三、验证命令与证据

- mvn：`mvn test` → 1160/0/0；`-Dtest=LegacyFileGrantSyncTxTest` → 3/3
- typecheck：`check-ui-typecheck.sh` → PASS 537/537；spec 单跑 8/8
- 演练：`out/阶段A-A2-Batch3演练存档-20261118/drill3.sh`（可复现）；日志 `out/阶段A-A2-M3合并演练日志-20261118.txt`
- 空库重放：`replay-empty-db.sh` → ✅（153 支含 18.3）
- 迭代过程留痕（均在演练暂存，未污染仓库）：括号失衡×2、MySQL 1093 同表子查询（派生表绕过）、聚合嵌套 1111（行级表达式修正）、CONCAT_WS 空段（NULL 跳过）、`bash -s` stdin 与 docker exec -i 竞态导致脚本静默截断（结构性隔离+全文件化 SQL 规避）

## 四、本轮不做
- 生产切换窗口全部动作（等 QC 第 3 轮通过+用户时间点确认）
- perms 不同真重复组的处置（清单已备，等切换窗口实测+人工拍板）
- 按钮视图收口（切 new 硬前置，仍列后续批次）
