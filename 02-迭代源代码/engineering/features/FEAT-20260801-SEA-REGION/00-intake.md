# 功能受理

## 用户目标

1. 新增公海池时，组织架构**要能选到大区**（原只能选部门/人员）
2. 追加要求：**大区/办事处/部门/人员全部可选**（仅总部灰显）
3. 附带修复：公海池可见范围选择逻辑缺失（onSeaTreeCheck 等 4 个函数未定义，勾选不生效、保存被拦）

## 当前行为

- 后端 buildOrgTree：仅 dept_type=4（部门）与人员可选，大区(2)/办事处(3)/总部(1)灰显（SeaConfigService.java）
- 前端 config.vue 公海池弹窗：模板引用 onSeaTreeCheck/selectedSeaNodes/removeSeaNode/clearSeaSelection 全部未定义 → 勾选树节点抛 TypeError、visibleKeys 永不更新、保存报"请选择可见部门/人员"；tree-card 样式缺失
- 消费逻辑：isVisibleTo 仅精确匹配部门 id（存大区 id 时子部门用户不可见）；deriveRegionIds 仅向上找办事处（大区 id 派生不出办事处）

## 范围

- 后端 SeaConfigService：buildOrgTree selectable 放开（type!=1）；isVisibleTo 部门上溯匹配；deriveRegionIds 大区向下展开收集子树办事处
- 前端 config.vue：补全 4 个缺失函数 + selectedSeaNodes computed + 编辑回显 setCheckedKeys + 样式
- 前端 org.vue：组织类型 1/2/3/4 中文显示（总部/大区/办事处/部门）——用户追加需求

## 不在范围

- 生产部署（需授权）；字典管理 UI 美化（另开功能包）

## 来源与事实

- 用户输入："新增公海池 组织架构 要能选到大区"；"办事处层级也能选择啊 部门层级 也能选啊 人员也可以选的"；"首页/组织架构 那个1 2 3 4 换成中文显示 总部 大区 办事处 部门"
- 数据库：sys_dept dept_type 1=总部/2=大区/3=办事处/4=部门；sys_org.org_type 存数字 1/2/3/4
- 实测：勾选树抛 TypeError（undefined.length）→ 保存失败

## 约束

- 测试服 111.229.37.220；测试池验证后已清理

## 初始风险

- 中：公海池可见性逻辑改动影响面广（公海池页面可见池列表/客户释放路由）——测试服验证 + 回滚点（旧 jar /tmp/app.jar.bak-sea-region）
