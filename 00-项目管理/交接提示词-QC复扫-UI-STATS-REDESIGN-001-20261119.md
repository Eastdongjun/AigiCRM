# 交接提示词 — AIGICRM QC 复扫（统计看板重设计批，Claude 质量窗口）

> 投给新的 Claude QC 会话即可零上下文接手本批独立复扫。本文件自包含；细节在被指向的 `out/` 文件。
> 交接背景：原 QC 窗口（ZCode）API 限额中断；本批由实施岗接续完成后冻结，**本批 QC 必须由独立窗口执行，不采信实施方自报**。

---

## 一、角色与边界

你是 **AIGICRM 本批（UI-STATS-REDESIGN-001）的独立 QC**。项目根：`/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`。

- **铁律**：生产**零写入**；生产动作（部署/迁移/写/重启）须用户明确授权；测试服动作须用户授权窗口；**不采信任何自报**（含申报文件的数值）；只输出 `PASS` / `FAIL` / `BLOCKED` 三态，**`BLOCKED ≠ PASS`**；未验证不得算"没有问题"。
- **QC 结论写 `out/`，不 commit**。凭据绝不打印（`~/.aigicrm-deploy.env`、`JWT_SECRET`、`DB_PASSWORD`）。
- **单一执行人**：测试服窗口内只有你操作。
- 工作区纪律：**dirty 时禁止扫描**（scanner 读磁盘/桥接读 HEAD 快照会崩）；本批源码已冻结。

## 二、本批状态（2026-11-19 07:50 UTC+8 定格）

| 项 | 值 |
|---|---|
| **HEAD** | **`8d2b4344`**（`feat(fabushi): UI-STATS-REDESIGN-001——统计看板阅读优先重构（PC 12 项+MP 6 项）`） |
| 基线 | `45e55eb3`（边框修复批，已 SCAN_DONE） |
| 改动面 | 11 文件 +703/−60（后端 4 服务/控制器 + 新测试 7 用例 + PC stats.vue + MP 3 文件） |
| 工作区 | 源码已冻结（`git status -- 02-迭代源代码` 无 M/D；历史 xlsx/docs 未跟踪噪音与本批无关） |
| 待验收 | PC 12 项 + MP 6 项 + 口径独立复算 + CSV 同源 + 门禁自跑 + 字典跟随实测 |

## 三、材料索引（按顺序读）

1. **批规格（验收口径唯一依据）**：`out/法布施-统计看板重设计批规格-UI-STATS-REDESIGN-001-20261119-01claude.md`
2. **实施完成申报（被验对象）**：`out/法布施-统计看板重设计-实施完成申报-UI-STATS-REDESIGN-001-20261119.md`
3. **设计方案**：`out/法布施-统计看板重设计方案-20261119-01claude.md`
4. **QC 职责/纪律/已知坑位（继承）**：`00-项目管理/交接提示词-QC质量检查续作-Claude转ZCode-20261119.md`（§5 扫描协议/测试服纪律/坑位）
5. **QC 总入口（全项目背景）**：`out/qc/check-net/INDEX-20261118.md`；P0-5 证据 `out/qc/check-net/evidence/sonar-20261118/closure-20261118.md`

## 四、扫描协议

1. 确认 `git rev-parse HEAD == 8d2b4344` 且源码干净；
2. 自跑全门禁（不采信申报数值）：
   - 后端：`cd 02-迭代源代码/aigicrm-backend && mvn test jacoco:report`（预期 1227/0；jacoco 必须本次重生成）
   - 契约：`cd 02-迭代源代码 && bash scripts/dev-verify.sh contracts`（预期 exit 0）
   - 前端：`cd 02-迭代源代码/aigicrm-ui && npm test`（预期 340/340）；`bash scripts/check-ui-typecheck.sh`（基线 536）
3. Sonar 扫描：沿用既有本地 Sonar 实例与 gate `AIGICRM-P0-5`；扫描脚本若 `/tmp/qc-sonar-f85.sh` 已失，按 QC 交接文档 §5.1 重建（token 运行时生成；coverage 取本次 jacoco.xml）。
   预期：gate OK；new blocker/critical/major/bugs/vuln=0；critical/blocker/vulnerabilities=0；S1068 计数不变；new S1192=0；S3776 三族不增。
4. 元素对账：规格 §八 六项（PC 12 + MP 6 / SQL 独立复算 / 截图 / CSV / 门禁 / 字典跟随实测）。
5. 有新增违规 → 回实施岗修 → 重新 commit → 复扫；无新增 → 回 `SCAN_DONE`。

## 五、本批已知关注点（先验这些）

1. **MP M1 标签偏差**：申报称现有两格实为累计口径，故标"累计阅读(人次)/我的内容"而非规格文字"本月"——请裁决（改标签不改数据即可）。
2. **月报后端扩字段**：规格预估零改动，实测扩 `bookRanking`/`categoryReads`（CSV 任意月区块需要）——核对是否与页面同源同值。
3. **达成率分母**：当前实现用"当前提醒设置回溯套整个周期"口径（规格 §二-4 未写死设置时点），复算时按同口径核对，口径差异单独列观察项。
4. **visible=null（全量档）**：`periodCoveragePct`/`coveragePct` 为 null（页面 —），`dailyGoalRate` 仍计算（全员提醒人日）——核对是否符合口径。
5. **新增测试文件**：`FabushiStatsRedesignTest` 7 用例；Sonar 若对测试文件报 S1192/未用参数，按既有测试豁免口径判定，不误伤。

## 六、测试服与推送

- 本批验收如需测试服（字典跟随实测/截图/CSV 抽查），先向用户申请窗口授权，并 pin 基线 A 八项；窗口结束清理 `perm:ctx*` 缓存并还原。
- **推送窗口（用户已预授权）在本批 SCAN_DONE 之后**：PC dist + 后端 jar + MP 上传；QC 负责 L5 终验。

## 七、你的第一步

1. 读 §三 的 1、2 两份文件（10 分钟建立验收口径）；
2. 确认 HEAD 与冻结状态；
3. 自跑后端/契约/前端三门禁；
4. 报告用户：三态结论 + 证据指针；有 FAIL 列出可执行修复项。
