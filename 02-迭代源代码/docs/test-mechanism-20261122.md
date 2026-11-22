# AIGICRM 测试机制（方法 · 内容 · 结果 三合一绑定）

> 本文是**执行入口**：把已转正的门禁模板（方法）、测试矩阵（内容）、问题闭环（结果）绑成一套日常可跑的机制。
> 方法论单一源已上移至工程领域层 `05-领域/01-工程/06-测试/01-技能/test-system/`（跨项目复用）；本文是该单一源在本项目的实例化。

## 一、测试方法（何时跑什么）

| 场景 | 必跑 | 命令 |
|---|---|---|
| 迭代中自检 | gate.sh --quick 档（不算交付） | `bash scripts/gate.sh --quick` |
| **任何代码交付** | **gate.sh 完整档 = 唯一裁决**（含 [3b] 内核兼容） | `bash scripts/gate.sh` |
| 单任务收尾出报告 | P0 报表出口 | `bash scripts/quality-gate.sh` |
| 权限/数据范围改动 | 七档 ID 集合 diff 全量回归 | `AIGI_TEST_USER=admin AIGI_TEST_PASS=… bash scripts/verify-perm-matrix.sh` |
| 前端部署 | 部署脚本尾部自动内容级核验（无需手工） | `bash scripts/deploy-frontend.sh` |
| web-view 相关改动 | 内核兼容运行级 | `AIGI_KERNEL_RUNTIME=1 bash scripts/verify-webview-kernel-compat.sh` |
| 上线后 | 真机回归清单（用户执行，回填矩阵才算闭环） | 见 §三 |

完整变更类型路由与 G0–G7 分层：`docs/test-gate-template-20261122.md` §1/§3。

## 二、测试内容（测什么，14 sheet 矩阵承载）

- **用例源**：权限码/动作码从生成器单一源出（gen-permission-codes.py），**不手写**
- **组织树 fixture**：矩阵 02 表（埋点：兼岗/子部门嵌套/5 层链+第 6 层探针/双大区互斥），测试环境 `pmx7_` 前缀由 verify-perm-matrix.sh 第 8 段自动造毁
- **执行分工**：权限·数据范围域=脚本全量（第 8 段）；其余域（06 审批链/07 消息/08 日志/09 DB/10 表结构/11 安全/12 性能/13 兼容）=**按迭代填表**，每迭代交付前填对应 sheet 至少一行真实记录
- **专项触发**：改 web-view H5→内核兼容；改统计/列表→性能预算；改迁移→空库重放+快照 validate

## 三、测试结果（记到哪、怎么算闭环）

1. **逐行对账**：矩阵 04 表——期望集（按档位+组织树推导）vs 实测返回集，ID 集合级 MATCH/MISMATCH
2. **问题闭环状态机**：MISMATCH/FAIL → 05 表开单 → 登记→修复中→已修复待复测→**已闭环（复测证据齐）**
3. **证据归档**：`东来/Agent/00-治理/项目执行/01-AIGICRM/runs/<run-id>/evidence/`（门禁全输出+退出码+commit hash+验证报告表+专项+部署记录+真机回归回填），禁含密钥
4. **域闭环判据**：该域矩阵全绿 + 无未闭环问题单 + 门禁完整档 0 ——三者同时成立
5. **发布链位置**：G5 门禁全绿 → deploy-db(如有) → deploy-backend → deploy-frontend（尾部自动内容级核验）→ G7 真机回归

## 四、当前基线（2026-11-22）

- gate.sh 完整档 **PASS=10 / FAIL=0**（HEAD 首次全绿）
- verify-perm-matrix.sh **PASS=28 / FAIL=0**（七档 8/8 精确匹配）
- 内容级核验：线上 241/241 assets 一致
- 问题单：BUG-003/004/005 全部已闭环；待办=06–13 各域按迭代填表

## 五、与领域层的关系（防双源漂移）

| 层 | 职责 | 文件 |
|---|---|---|
| 工程领域（单一源） | 方法论 G0–G7/硬规则/五层内容/通用工具 | `05-领域/01-工程/06-测试/01-技能/test-system/` + `03-标准模板/` |
| 本项目（实例化） | 真实命令绑定 + 矩阵 + 证据目录 | 本文 + `test-gate-template-20261122.md` + 矩阵 xlsx + scripts/ |
| 中央登记 | 门禁 SSOT | `08-控制面/registry/gates.yaml` GATE-ENG-TEST-001/002/003 |

规则漂移处理：方法论变更改领域层再向下同步；本项目命令变化只改本文与项目模板，不回写领域层。
