# 实现记录

## 变更文件

- `aigicrm-ui/src/views/incentive/index.vue`（需求1已改于交接；需求2：isSingleSlot/slotLabel/detailLabel computed、单槽文件区、跟踪记录隐藏、canSubmit 单槽≥1校验、提交不传空槽/追加保留旧槽；需求3：mainOrderOf + filteredRecords 四区排序）
- `aigicrm-ui/src/views/incentive/search.vue`（需求3：latestRecord 解耦"最近更新"；需求4：同事姓名 allow-create、产品小类级联下拉、主搜索框传 customerName+keyword、doReset 重置新字段）
- `aigicrm-backend .../incentive/service/IncentiveService.java`（calcScore 重写：plan 每文件+base 上限5×base、visit/exchange 任一槽有文件+base、trial 三齐全；isVisitExchangeCategory；categoryMainOrder；getCustomerRecords 加 product/keyword 参数与组内四区排序）
- `aigicrm-backend .../incentive/controller/IncentiveController.java`（customerRecords 加 product/keyword 参数）

## 实现决策

- detail.vue 无需改动：激励 Tab 消费 customer-records，后端排序直接生效
- 单槽追加：保留旧记录 mid/post/track 槽文件（避免历史数据丢失），只编辑 pre 槽
- keyword 与 customerName 同传时后端 OR 组合（客户名命中 或 relatedProducts 命中）
- 需求1 代码保持交接原样（构建验证通过，未额外改动）

## 新增或更新的测试

- 未新增单测（后端单测 IncentiveScoreTest 待验证阶段扩展）；E2E 走浏览器验证

## 实际执行命令

- npm run build --prefix aigicrm-ui ✓（404ms）
- JAVA_HOME=openjdk@21 mvn clean package -DskipTests ✓（aigicrm-backend-1.0.0.jar）
- 部署：前端 tar→scp→docker cp→restart；后端 jar scp→docker cp→restart（验证阶段执行）

## 失败与纠正

- engineering_gate.py write_manifest 无限递归（line 128 误写为自调用，应为 atomic_json）→ 修复脚本 → 状态推进恢复正常；此缺陷使旧 FEAT 的 fingerprint 从未落盘，已修复为正常写入
- 截图保存路径权限问题 → 先存工作区再复制到 evidence

## 未完成与残余风险

- 后端重算与积分验证待执行；四区排序/搜索待浏览器验证
- plan 旧记录重算后分数可能上升（3→5×base 上限），用户已确认
- 生产部署未授权
