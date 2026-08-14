# 交付报告

## 结果
激励类别管理已改造成一级/二级结构：4 个一级类别（外出参观/交流拜访/两谈两做方案/产品试点）以分组卡片展示，12 个二级子项归属清晰；孤儿垃圾数据（213/111）已清理；后端强制两级校验。AC-001~007 全部 PASS，已部署测试服。

## 交付范围
- 激励类别管理一级/二级分组展示（4 一级 + 12 二级）
- 孤儿数据清理（46/47/48）
- 后端两级校验

## 变更清单
- ycmn-ui/src/views/system/config.vue：类别管理区块 → 一级卡片分组 + 二级子项表格 + 折叠交互（collapsed Set/toggleCategory/cat-group 样式）
- ycmn-backend/.../incentive/service/IncentiveService.java：validateCategoryLevel（create/update 校验 parentId 必须 0 或顶级 id，否则 400）
- 数据：biz_incentive_category 删除孤儿 46/47/48（备份 evidence/biz_incentive_category_backup_20260801.sql）

## 安装与启动
- 前端：npm run build（ycmn-ui，rolldown）
- 后端：JAVA_HOME=/tmp/jdk-17.0.20+8/Contents/Home mvn clean package -DskipTests
- 测试服：111.229.37.220（Docker，ycmn-frontend/ycmn-backend）

## 部署状态与步骤
- 已部署测试服：前端 dist → docker cp ycmn-frontend（旧版备份 /tmp/html-backup-cat2level/）；后端 jar → docker cp ycmn-backend → restart（旧 jar /tmp/app.jar.bak-cat2level）
- 生产未部署（未授权）
- 后端重启后登录接口 200，页面渲染正常

## 验证证据
- 浏览器 1440x900/1280x720：evidence/ui-1440x900.png、ui-1280x720.png；视觉评分 88（ui-review.json）
- API：三级创建 parentId=10/999 → 400；parentId=1/0 → 200（测试数据已清理）
- 门禁：run-gates build+typecheck 全过；vitest 8/8
- 数据：删除后 16 条（4 一级 + 12 二级）；激励记录 18 条无孤儿引用

## 回滚与恢复
- 前端：恢复容器 /tmp/html-backup-cat2level/ → html
- 后端：恢复 app.jar.bak-cat2level → restart
- 数据：evidence/biz_incentive_category_backup_20260801.sql 恢复（如需）

## 已知限制与残余风险
- 删除一级类别仍为"仅删自身"，子项会变孤儿（本次孤儿成因）——是否级联删除待用户确认，确认后小改动即可补上
- 用户浏览器旧 chunk 需强刷（Ctrl+F5 / ?v= 参数已验证）
- 生产环境未部署
- 系统设置页控制台 1 error 为 logo.jpg 404（既有问题，非本次引入）

## 版本与追踪
- FEAT-20260801-CATEGORY-2LEVEL；REQ-001~004 → AC-001~007 全 PASS（06-test-matrix.md）
- git：未提交（与 8-1 其他功能包一致，待用户确认后 commit）
- v2（用户确认后追加）：有子项的一级禁止删除（前端 warning + 后端 400，AC-008 PASS），已重新部署测试服验证
