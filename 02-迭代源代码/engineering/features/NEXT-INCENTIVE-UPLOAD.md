# 交接:四项激励上传页 + 查询页改造(2026-08-01,上下文超限新会话恢复用)

## 状态

- FEAT-20260801-FIX-PARSEID 已 DELIVERED(详情页文件400已修,已部署测试)
- 源码: /Users/donglai/东来/01-Projects/01-aigicrm/04-源码/ycmn-ui/src/views/incentive/index.vue
- 测试服务器: 111.229.37.220,部署: npm run build → tar → scp → docker cp ycmn-frontend → 覆盖 /usr/share/nginx/html → docker restart ycmn-frontend

## 需求 1:相关产品下拉 ✅ 代码已改完(未构建部署)

- index.vue:156 自填 input → 两级下拉(大类+小类),提交值"大类-小类",编辑回填拆分,历史文本兼容
- 选项:涂层类[防腐/加固/钢结构/煤气管道/其他];防滑板类[公共区/装置区];机封类[水泵/渣泵/介质泵];密封类[静密封/动密封/橡胶密封];润滑类[润滑油/润滑脂/润滑剂]
- 新增:productCats 常量、relatedCat/relatedSub refs、watch 合成 relatedProducts、清空/回填同步

## 需求 2:安装详情改造(未做)

- 外出参观/交流拜访:安装详情字段改"参观交流详情描述";安装文件改"现场照片"(至少1张,最多5张);删除跟踪记录上传
- 两谈两做方案:安装详情改"文件详情描述";安装文件改 1~5 个文件(至少1最多5);删除跟踪记录上传
- 注意:index.vue 文件槽位 fileSlots 现为 pre/mid/post 三段,跟踪记录区需按品类条件隐藏

## 需求 3:上传后排序(未做)

- 客户相关激励记录展示按四区排序:试点(最前)→ 两谈两做方案 → 交流拜访 → 外出参观(最后)
- 涉及:详情页四项激励 Tab 与/或激励上传页列表,需确认品类编码 mapping(试点=试制?查 category_code)

## 需求 4:激励查询首页(search.vue)搜索完善(未做)

- 增加产品小类搜索(五大类小类选项,同需求1选项表)
- 增加同事姓名搜索(搜索某同事,其共享案例全出)
- 增加客户名称 + 关键字搜索(搜客户=该客户共享内容;搜关键字如"煤气管道"=相关案例全出)
- 涉及:search.vue 筛选栏 + 后端 /api/incentive 查询参数(需查后端搜索实现是否支持)

## 建议

- 新 FEAT: FEAT-20260801-INCENTIVE-UPGRADE
- 先完成需求1构建部署验证,再做2/3/4
- 后端改动需 JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home mvn clean package -DskipTests
