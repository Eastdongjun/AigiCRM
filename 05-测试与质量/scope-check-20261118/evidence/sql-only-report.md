# AIGICRM SQL-only 全模块完整性扫描

- 生成：2026-11-18 15:28:18
- 数据源：测试库只读；生产零写入；不调用 API
- 检查项：100；通过 86；发现 4；缺 schema 0；SQL 错误 0；信息 10

## 发现（按模块）

### 权限

- **PERM-01（P2）范围行 ROLE 主体不存在（临时角色残留）**：3
### 文件

- **FIL-01（P1）文件引用不存在文件夹**：2
- **FIL-10（P2）活跃文件 oss_key 重复**：3
### 法布施

- **FAB-01（P2）阅读进度书不存在**：42

## Schema 缺失（迁移未应用）

无。

## SQL 错误/无结果

无。

## 信息项

- AUTH-08（认证/用户）已过期 token 黑名单残留（可清理）：83
- AUTH-09（认证/用户）启用用户中仍要求强制改密：0
- CUS-04（客户）活跃客户名称重复（迁移前存量，需 grandfather 核对）：3
- APP-06（审批）申请人自审自批样本（需策略确认）：0
- INC-05（激励）激励记录业务键重复（需业务确认）：91
- SCO-02（积分）积分条目重复（需业务确认）：0
- REM-05（提醒）overdue_days 为负（未到期天数，信息项）：24
- DRF-02（表单草稿）同一用户同类型草稿重复：0
- STA-01（统计）全量可见客户数（admin 口径，应 294）：294
- STA-02（统计）开票金额合计与回款金额合计（信息）：192.54

## 全部通过项

AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, ORG-01, ORG-02, ORG-03, ORG-04, ORG-05, PERM-02, PERM-03, PERM-04, PERM-05, PERM-06, PERM-07, PERM-08, PERM-09, CUS-01, CUS-02, CUS-03, CUS-05, CUS-06, CUS-07, CUS-08, CUS-09, CUS-10, CUS-11, CUS-12, CUS-13, CUS-14, CUS-15, CUS-16, SEA-01, SEA-02, SEA-03, SEA-04, APP-01, APP-02, APP-03, APP-04, APP-05, APP-07, FIL-02, FIL-03, FIL-04, FIL-05, FIL-06, FIL-07, FIL-08, FIL-09, FIL-11, INC-01, INC-02, INC-03, INC-04, INC-06, INC-07, INC-08, INC-09, SCO-01, SCO-03, SCO-04, SCO-05, REM-01, REM-02, REM-03, REM-04, TAS-01, TAS-02, TAS-03, DRF-01, DRF-03, FAB-02, FAB-03, FAB-04, FAB-05, FAB-06, FAB-07, FAB-08, FAB-09, FAB-10, FAB-11, INC-03P
