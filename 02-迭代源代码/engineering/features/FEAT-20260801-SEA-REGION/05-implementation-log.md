# 实现记录

## 变更文件

- SeaConfigService.java（REQ-001/003）：buildOrgTree selectable = type!=1；isVisibleTo 部门上溯祖先链匹配；deriveRegionIds 大区向下收集 type=3；新增 collectOfficesUnder
- config.vue（REQ-002）：补 seaTreeRef/onSeaTreeCheck/selectedSeaNodes/removeSeaNode/clearSeaSelection + handleEditPool nextTick 回显 + tree-card 样式 + 提示文案
- org.vue（REQ-004）：typeLabel/typeTag 数字映射（1总部/2大区/3办事处/4部门）+ 表单 option 值改数字 + form 默认 '3'

## 实现决策

- 与计划一致；修复了既有 bug（4 个函数未定义导致公海池可见范围从未可用）

## 新增或更新的测试

- 浏览器 E2E：树状态断言、勾选/标签/回显、保存 SQL 回读（见 06-test-matrix）

## 实际执行命令

- mvn package（JDK17）→ scp jar → docker cp → restart
- npm run build → zip → scp → docker cp 完整替换
- 验证 SQL：SELECT visible_dept_ids, region_ids FROM biz_sea_config WHERE pool_name='山西大区测试池' → 2,4,5 / 4（测试池已清理）

## 失败与纠正

- 初次保存失败：TypeError undefined.length——根因 onSeaTreeCheck 未定义（勾选不更新 visibleKeys）；补全 4 函数后通过
- 中文条件 DELETE 失败（SSH 编码）→ 改用 id 条件删除测试池
- 弹窗在 evaluate 操作中被误关一次 → 用 playwright 原生定位重走流程

## 未完成与残余风险

- isVisibleTo 大区上溯以普通用户视角实测（测试服账号为 admin 数据范围 all，逻辑经代码审查+数据回读验证）
- 生产未部署
