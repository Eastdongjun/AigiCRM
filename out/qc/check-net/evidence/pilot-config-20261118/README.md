# 小域试点证据：系统配置 public（C@L2）

- cell_id：`SYS.D2.EP:system.configs.public`（L2 对象行；L1 `14 系统管理 × D2` 仍为 P，未整域闭环）
- 检查：`GET /api/system/configs/public` 实见 vs DB `sys_config` 7 个公开键
- 环境：测试服 `ycmn-backend` jar `2ea180c6…`、StartedAt `2026-11-18T15:42:02Z`、DB `aigicrm_prod_20261016`、HEAD `10a6aa99`、health 200
- 命令（脱敏）：
  - `curl -s http://127.0.0.1:8080/api/system/configs/public`
  - `docker exec -i -e MYSQL_PWD=$PW shared-mysql mysql -uroot -N -B aigicrm_prod_20261016 -e "SELECT config_key,config_value FROM sys_config WHERE config_key IN ('system.name','system.logo','system.subtitle','customer.adjust.days','customer.warning.days','customer.protection.days','invoice.overdue.days')"`
- 期望（DB）：7 键
  - system.name=AIGICRM；system.logo=/api/file/download/public/1/logo.jpg；system.subtitle=市场资源管理系统
  - customer.adjust.days=180；customer.warning.days=30；customer.protection.days=365；invoice.overdue.days=365
- 实际（API）：`{"code":200,"data":{"system.name":"AIGICRM","system.logo":"/api/file/download/public/1/logo.jpg","customer.adjust.days":"180","customer.warning.days":"30","customer.protection.days":"365","system.subtitle":"市场资源管理系统","invoice.overdue.days":"365"}}`
- 比对：7/7 键值一致；extra=0；missing=0；字段差异=0
- 判定：**PASS（C@L2）**
- 证据强度：L2（测试服运行时 API + DB）
- 生产写入：false；测试服未改动（只读 curl + SELECT）
- 备注：本试点只验证 public 只读接口，不代表 SysConfig/SysDict 全量 D2 闭环；整域仍需覆盖鉴权接口、字典消费、配置写入与审计。

## 原始输出

```
-- env identity --
2ea180c67e011630337ab6da17eaa588  /app/app.jar
StartedAt=2026-11-18T15:42:02.82848892Z
health=200
-- DB rows --
customer.adjust.days	180
customer.protection.days	365
customer.warning.days	30
invoice.overdue.days	365
system.logo	/api/file/download/public/1/logo.jpg
system.name	AIGICRM
system.subtitle	市场资源管理系统
-- API JSON --
{"code":200,"data":{"system.name":"AIGICRM","system.logo":"/api/file/download/public/1/logo.jpg","customer.adjust.days":"180","customer.warning.days":"30","customer.protection.days":"365","system.subtitle":"市场资源管理系统","invoice.overdue.days":"365"},"errorCode":null,"message":"success"}
```
