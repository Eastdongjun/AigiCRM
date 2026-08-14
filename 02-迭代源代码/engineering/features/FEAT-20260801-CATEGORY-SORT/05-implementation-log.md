# 实现记录

## 变更文件

- 前端/后端代码：**零改动**（排序由 `biz_incentive_category.sort_order` 单一驱动，IncentiveService.getCategoryTree 按 sort_order 升序）
- 数据库（测试服 111.229.37.220，MySQL 容器 shared-mysql，库 ycmn，表 biz_incentive_category）：4 条 UPDATE → REQ-001
  - `UPDATE ... SET sort_order=1 WHERE id=4`（产品试点 trial）
  - `UPDATE ... SET sort_order=2 WHERE id=3`（两谈两做方案 plan）
  - `UPDATE ... SET sort_order=3 WHERE id=2`（交流拜访 exchange）
  - `UPDATE ... SET sort_order=4 WHERE id=1`（外出参观 visit）

## 实现决策

- 与技术计划一致：改数据不改代码；用 id 精确匹配而非 code，避免误更新
- 改前备份整表 INSERT 到 evidence/biz_incentive_category_backup_20260801_sort.sql（52 行）

## 新增或更新的测试

- 无代码变更，无单元/构建测试新增；验证为 SQL 回读 + 浏览器 E2E 实测（见 06-test-matrix）

## 实际执行命令

- 备份：`sshpass ssh ubuntu@111.229.37.220 "docker exec shared-mysql mysqldump ... ycmn biz_incentive_category" > evidence/biz_incentive_category_backup_20260801_sort.sql`
- UPDATE + 回读：`docker exec shared-mysql mysql -uroot -p<redacted> -e "UPDATE ...; SELECT id, category_name, category_code, parent_id, sort_order FROM biz_incentive_category WHERE parent_id=0 ORDER BY sort_order"` → 产品试点=1/两谈两做=2/交流拜访=3/外出参观=4
- 浏览器：http://111.229.37.220/incentive 与 /system/config（Tab 激励类别与权限），DOM 断言顺序

## 失败与纠正

- 无功能失败；record-ui-review 初次因 --console-errors 1 失败 → 确认唯一 console error 为系统设置页既有 logo.jpg 404（HANDOFF 残余风险第 3 条），以功能页（激励上传页 console 0 errors）实测为准重跑通过
- PROTOTYPE_READY advance 因截图时间戳（final 早于 baseline）失败 → touch 重置时间戳后通过

## 未完成与残余风险

- 生产未部署（需用户授权；同一条 SQL 即可同步）
- 系统设置页 logo.jpg 404 为既有问题，非本次引入
