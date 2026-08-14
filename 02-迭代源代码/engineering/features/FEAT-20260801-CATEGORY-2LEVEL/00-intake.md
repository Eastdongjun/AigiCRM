# 功能受理

## 用户目标

系统设置 → 激励类别与权限 → 激励类别管理：把类别管理改造成清晰的一级/二级结构展示，并整理现有类别数据（清理孤儿节点与垃圾数据）。

## 当前行为

- 2026-08-01 18:20 实测（测试服 111.229.37.220）：类别表格已是树形（el-table tree-props，default-expand-all），数据两级：4 个一级（外出参观 visit / 交流拜访 exchange / 两谈两做方案 plan / 产品试点 trial）+ 16 个二级子项
- 新增/编辑弹窗"上级类别"为 el-tree-select，选项仅映射一级类别（categoryParentOptions 只 map categoryTree 顶级），理论上只能建到二级
- 后端 IncentiveService.buildTree 递归任意深度，未限制层级
- 数据库 biz_incentive_category 存在孤儿节点：id=46/47/48 的 parent_id=45，而 id=45 已被删除（名称"213/111/213"为垃圾测试数据）
- 用户反馈：树形缩进不明显，希望展示形态更清晰的"一级/二级"分组；数据需按两级整理（用户已在澄清中确认两点）

## 范围

- 前端 ycmn-ui/src/views/system/config.vue：激励类别管理区块展示形态改造（一级/二级分组）
- 数据整理：删除孤儿类别 46/47/48（已确认无激励记录引用，biz_incentive_record 全库 18 条，均不引用这三个 id）
- 后端（如需）：限制类别最多两级（创建/更新时校验 parentId 必须是 0 或顶级类别 id）
- 前端构建、部署测试服（111.229.37.220）、浏览器验证

## 不在范围

- 生产环境部署（需用户另行授权）
- 激励上传页/查询页的类别展示（本次只改系统设置管理页）
- 类别数据的新增（如新建一级/二级类别，由页面现有功能支持）

## 来源与事实

- 用户输入："激励类别管理 设置成 一级 二级 这种结构"，并确认指向"系统设置 - 激励类别与权限"
- 澄清结果：展示形态不同 + 数据要按两级整理
- 源码：config.vue:144-203（类别管理区块）、795-797（categoryParentOptions 仅顶级）、IncentiveService.java:838-843（buildTree 递归）

## 约束

- 源码基准：/Users/donglai/东来/01-Projects/01-aigicrm/04-源码（git main，最新 3 提交 ee6fc71/c61acb0/e0a803c 为 8-1 功能包交付，未 push）
- 测试服：111.229.37.220；MySQL root/<redacted>（容器 shared-mysql）；前端容器 ycmn-frontend（80 端口）
- 本机 JDK25 与 Lombok 不兼容，后端构建需 JDK17（~/.mavenrc 已配，临时目录）
- 页面入口：系统设置 /system/config → Tab"激励类别与权限"（无独立菜单）

## 初始风险

- 删除孤儿类别不可逆 → 已确认无业务引用，仍按门禁先备份（SELECT 导出）再删
- 展示改造影响系统设置页其它 Tab 的风险低（局部 scoped 样式 + 组件替换）
- 用户浏览器旧 chunk 需强刷
- 展示形态最终方案待 UI 原型阶段确认（分组卡片 vs 大类列+子类列）
