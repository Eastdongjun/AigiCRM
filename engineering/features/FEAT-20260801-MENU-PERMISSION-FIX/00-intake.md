# 功能受理

## 用户目标

销售员/同事账号点击菜单出现"没有访问权限，请联系管理员授权"，排查并修复。

## 当前行为

- 前端 layout/index.vue 菜单为硬编码（非后端菜单接口渲染），部分子菜单项缺权限判断：
  1. "学分录入"（/score/entry）无条件渲染——销售员有 score:ranking（父组展开）但无 score:entry → 点击 403
  2. "岗位管理"用 system:role:view 判断（实际权限码 system:position:view）——不精确
- 权限体系静态+动态排查：后端 52 权限码与数据库菜单全集无缺口；admin(61)/销售员(24) 权限覆盖核心业务；销售员 28 接口遍历仅 /system/roles 403（合理管控）

## 范围

- layout/index.vue：学分录入加 v-if="hasPerm('score:entry')"；学分排行加 v-if="hasPerm('score:ranking')"；岗位管理改用 system:position:view

## 不在范围

- 后端权限调整（无缺口）；生产部署

## 来源与事实

- 用户确认：销售员账号、菜单场景
- 王磊磊（role 11）菜单实测："学分管理"显示"学分录入+学分排行"，点击学分录入 403（score:entry 接口拦截）

## 约束

- 测试服；纯前端

## 初始风险

- 低
