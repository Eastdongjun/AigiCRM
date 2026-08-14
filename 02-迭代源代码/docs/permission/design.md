# YCMN 权限设计

> 人工维护文档。权限码清单见 `permission-dict.md`(自动生成)。
> 修改权限体系时必须先更新本文,再改代码。

## 一、认证链路

```
浏览器 → JWT(Authorization: Bearer) → JwtAuthFilter
  → 解析 token → 查 sys_user(校验 deleted/status)
  → 查 sys_user_role → 角色
  → 查 sys_role_menu + sys_menu.permission → 权限码集合
  → 注入 SecurityContext
```

- Token: JWT(HS256),有效期 24h,存 localStorage
- 登录接口 `/api/auth/login` 匿名可访问;失败 5 次锁定 15 分钟
- 登出:前端清 token(后端无状态)

## 二、授权机制(两层)

| 层 | 实现 | 说明 |
|---|---|---|
| 接口层 | `@PreAuthorize("hasAuthority('xxx')")` | 后端强制,POST/PUT/DELETE 全部覆盖 |
| 页面层 | 前端路由 meta.permission + layout 检查 | 无权限直接输 URL 跳回首页 |

**规则**:权限码 = `模块:动作`(如 customer:view / system:user:add);
菜单表中的 permission 列是唯一权限码来源(与代码 @PreAuthorize 对应);
**新增接口必须同时**:① 控制器加 @PreAuthorize ② sys_menu 加权限码行 ③ 角色绑定 sys_role_menu。

## 三、数据范围(行级权限)

- 配置来源: `sys_permission_rule` 表(规则引擎)+ 角色关联 `sys_role_rule`
- 规则类型:
  - `org_filter`: 组织过滤(org_id in 子组织)
  - `column_filter`: 字段过滤(如 owner_id = 当前用户)
  - `time_range`: 时间范围(本月/本季度)
  - `field_visible`: 字段可见性(隐藏财务/联系方式)
  - `action_control`: 操作控制(禁止导出/删除/只读)
- 生效链路: 登录时组装 → Service 层拼接 SQL where → 执行

## 四、角色体系(4 角色)

| 角色 | 特点 |
|---|---|
| 系统管理员(role 1) | 全部权限 |
| 行政(role 9) | 管理类权限,无销售数据 |
| 销售总监(role 10) | 管理 + 销售,数据范围全组织 |
| 销售员(role 11) | 业务权限,数据范围 org_and_children |

> 注意: 当前一人只取**第一个角色**(UserDetailsServiceImpl),一人多角色时仅第一角色生效(已知限制)。

## 五、关键安全点(踩坑记录)

1. **权限码必须查库确认**: 改权限前先查 sys_menu(勿猜,曾因 dept:view 保护写操作出事)
2. **所有写接口都要 @PreAuthorize**: 不只第一个,GET 也可能泄漏(用户列表曾无权限码全量泄漏)
3. **数据范围是 SQL 层强制**: 前端隐藏不等于安全,越权测试必须直接调 API
4. 登录失败锁定、密码 BCrypt 加密、软删除(deleted 标记)为既有安全基线

## 六、与代码的对应

- `JwtAuthFilter`(com.ycmn.security): 认证
- `SecurityConfig`(com.ycmn.config): 安全规则/CORS
- `PermissionRuleEngine`(com.ycmn.common.engine): 数据范围规则
- `applyDataScope()`: Service 层数据范围拼装
- `SysDeptController` 等: @PreAuthorize 示例
