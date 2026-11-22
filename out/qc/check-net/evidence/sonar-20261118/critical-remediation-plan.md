# 455 条 CRITICAL code smell 整改分账（2026-11-18）

> 用户拍板：**全部整改，清零后销账；不豁免**。另 4 条 CRITICAL BUG（scripts 排序）一并整改。
> 数据源：本地 Sonar `Eastdongjun_AigiCRM-local`，`severities=CRITICAL & types=CODE_SMELL`，`total=455`，`effortTotal=6,208 分钟`。
> 注意：全仓 CRITICAL 总计 459 = 455 code smell + 4 CRITICAL BUG；P0-5 严格口径要求两者都归零。

## 一、按规则分账（完整）

| 规则 | 数量 | 含义 | 风险/整改方式 |
|---|---|---|---|
| java:S1192 | **250** | 重复字符串字面量 | 机械：提取常量；低风险，量大 |
| java:S3776 | **87** | 认知复杂度过高（>15） | 重构方法；**中高风险**，需测试护航 |
| python:S3776 | 34 | 认知复杂度（scripts） | 重构脚本；低风险 |
| javascript:S3776 | 24 | 认知复杂度（UI/scripts） | 重构；低风险 |
| javascript:S4123 | 19 | 不必要的 await/异步误用 | 逐条核对；低风险 |
| python:S1192 | 14 | 重复字面量（scripts） | 提取常量 |
| java:S1186 | 6 | 空方法 | 删除/注释/实现 |
| javascript:S2310 | 5 | 赋值给函数参数 | 重命名/重构 |
| java:S131 | 5 | switch 缺 default | 补 default |
| java:S2479 | 4 | 多余的 public 修饰（接口） | 清理 |
| java:S5826 | 2 | 循环条件不变 | 修正 |
| javascript:S2004 | 2 | 循环内函数声明 | 提取 |
| java:S1948 | 1 | 可序列化字段非瞬态 | 评估 |
| java:S2093 | 1 | try 资源未关闭 | 修正 |
| python:S5797 | 1 | 不必要的列表拷贝 | 清理 |

## 二、按模块/热点文件（文件 facet 前 20，覆盖 433/455；其余为长尾文件）

- **backend 356 条**：IncentiveService 22、StatisticsQueryService 21、ApprovalService 19、FabushiDashboardService 18、FileService 18、CustomerService 17、FolderPermissionService 14、LifecycleService 13、RolePermScopeService 13、RecycleService 12、ReminderService 11、RecycleExportService 11、ScoreService 9、SysUserService 9、Fabushi 多个 8…
- **scripts 59 条**：check-feature-audit-contract.py 10、check-mp-view-bindings.mjs 10、其余脚本长尾。
- **PC-UI 18 条**：Vue/JS 长尾。
- 另有 22 条分布在未进前 20 的文件（长尾）。

## 三、整改批次建议（全部整改，分批复验）

| 批次 | 范围 | 预计 | 风险 | 验收 |
|---|---|---|---|---|
| A | java:S1192 250 条（重复字面量） | 低 | 低 | 单测/契约绿；Sonar S1192=0 |
| B | java:S3776 87 条 + 其余 Java 规则（S1186/S131/S2479/S5826/S1948/S2093） | 中高 | 高（改逻辑结构） | 全量 1187/0 + 契约 191/1/0 + 定向回归；Sonar java CRITICAL=0 |
| C | scripts/UI 规则（python/JS S3776、S4123、S1192、S2310、S2004、S5797）+ 4 条 CRITICAL BUG（S2871 排序） | 低 | 低 | 前端 340/0 + 契约绿；Sonar scripts/UI CRITICAL=0 |
| D | 3 条 CSS Blocker（DICT-RPX-001）+ 2 条 secrets 误报改写（`-p$VAR`→`MYSQL_PWD`）+ 1 条测试夹具排除（`test-secret-scan.sh` 加入 `sonar.exclusions` 并注明理由） | 低 | 低 | Sonar BLOCKER=0；前端 build/typecheck 绿 |
| E | 12 个后端 BUG 中 4 条防御性修复（S2142×2、S5998、S3655）；其余 8 条债务/误报登记 | 低 | 低 | 单测绿；Sonar BUG 下降；P0-5 只要求 critical/blocker，BUG 清零另行决定 |

**总工作量**：Sonar effortTotal 6,208 分钟（约 103 人时），不含测试回归与评审；实际按批次可能更高。

## 四、销账条件（P0-5 严格口径）

1. `Eastdongjun_AigiCRM-local` 重扫：**BLOCKER=0 且 CRITICAL=0**（含 4 条 CRITICAL BUG）；
2. 每批次本地门禁绿：后端 1187/0、契约 191/1/0、前端 340/0 + 537/537；
3. 实施方 commit + QC 复扫证据（同一 HEAD）；证据落 `out/qc/check-net/evidence/sonar-*/`；
4. Gate 配置修复（SONAR-GATE-001）完成，gate 不再是空门；
5. 在以上全部满足前，P0-5 保持 FAIL，不写通过、不销账。

## 五、证据

- 分账原始：`critical-facets-full.json`（本目录）
- 全仓度量/issue：`measures-full.json`、`issues-facets-full.json`、`blockers-full.json`、`critical-bugs-full.json`
- 扫描日志：`scanner-full.log`
