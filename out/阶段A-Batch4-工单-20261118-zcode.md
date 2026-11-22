# 阶段 A · Batch-4 工单（切 new 前置批）——2026-11-18 QC 出

> 前置状态：生产 dual 已上线（QC 第 4 轮核验通过）。本批完成后 + 观察期对账持续 0 → 可切 new。
> 与 A3/A4 并行（文件面不相交：本批=文件权限服务/部署脚本；A3=回收站导出；A4=审批人）。
> 生产：本批代码上线随下次部署窗口，不单列。

---

## 1.【主项】按钮视图收口（发现①，切 new 硬前置）

**缺口定位（QC 实读）**：`FolderPermissionService.permsForFiles`（约 L480-540）中 `grantDecisions` 仅用于**行级 DENY 隐藏**；逐动作 `hit` 仍走旧表 `grantHitIn(grants,…)`（`legacyGrants()` 在 mode=new 为空）→ new 模式下新引擎 ALLOW 不产生按钮，行 perms 收窄。`permsForBatch`/`folderActionsFor` 同病。

**方向性说明**：该缺口是"少显"非"多显"（按钮 ≤ 判定链允许），无安全面暴露，属功能面。

**改法要求**：
1. `FileGrantService` 新增多动作批量判定：`decideFilesMulti(files, Set<String> actions, userId) → Map<fileId, Map<action, Decision>>`——一次 load（action IN 各动作 actionsFor 的并集），每 (grant,file) 的 `coversFile` 只算一次（与 action 无关），再按 `grant.action ∈ actionsFor(X)` 归到各动作 X；语义必须与判定链 `checkFile(file, X)` 逐动作完全一致。
2. `permsForFiles`/`permsForBatch`/`folderActionsFor` 的逐动作 hit 接入：`hit = 引擎ALLOW ∨ 旧表(legacy/dual) ∨ 矩阵/owner`；引擎 DENY 已有行级隐藏保持，若某动作存在引擎 DENY 则该动作按钮也应抑制（与判定链一致）。
3. **验收原则（红线）：每个动作的按钮显示 == 该动作 API 判定结果**（parity），legacy/dual/new 三模式一致。

**测试**：
- parity 单测：引擎 ALLOW/DENY（逐动作）、旧表命中、矩阵命中、owner——按钮判定与 checkFile 同口径；
- 性能：一页文件多动作不产生 N+1（load 次数断言）；
- 运行时：测试服 new 模式行为对账**按钮面 diff=0**（复用第 2 轮 D 阶段 18 项矩阵，new 段按钮面与 baseline 一致）。

## 2.【缺陷·中】deploy-db.sh 假绿缺口

P2 事故暴露：`docker run` 失败但输出不含 error/failed 关键词时，成功判定误过。修复：以**容器退出码**为判据（`docker run … ; test $? -eq 0`），文本匹配仅作辅助；补负例测试（模拟拉镜像失败断言脚本非 0 退出）。

## 3.【缺陷·低】基础镜像本地化预置

生产 dockerd 外网拉取不稳。落法：flyway:9.22.3-alpine 已入生产镜像库（`32678f42631f`）；把后端基础镜像（eclipse-temurin/maven 系）一并 docker load 预置，脚本头注释登记本地化清单与更新方法。

## 4.【缺陷·低】reconcile-file-grants.sh 加 prod 模式

现仅通测试通道（shared-mysql）。加 `--prod` 参数：走 ssh_prod 通道 + ycmn-mysql 容器 + 独立备份提示（只读警告横幅）。QC 第 4 轮是手动改道执行的，转正后观察期对账可脚本化。

## 统一门禁

`mvn test` 套件只增不减 / 契约 / npm+typecheck / 空库重放 / `git status` 收口。完成后 QC 第 5 轮验收（与 A3/A4 一并或分批均可，自报时注明范围）。
