# 20260917 PC-小程序权限一致性活体验证

## 问题

PC 端改权限后，小程序是否生效？

## 链路（代码侧）

- 小程序 `hasPermission` ← `globalData.permissions` ← `/api/auth/info`，
  刷新点：app 启动、登录、首页 show、个人页 show、切换集团（`loadCurrentUser`）。
- 后端接口 `@PreAuthorize` 逐请求经 `JwtAuthFilter → UserDetailsServiceImpl`，
  权限走 Redis 缓存（`permVersion` 版本比对自愈）。
- PC 改角色菜单走 `PUT /api/system/roles/{id}` → 重建绑定 + `evictByRole` +
  `bumpUserPermVersionsByRole`（立即失效 + 版本兜底）。

## 活体测试（本地环境，admin/系统管理员角色，测试权限 statistics:view）

1. 基线：`/auth/info` 58 个权限（含 statistics:view），`indicator-stats` 200。
2. 直连 SQL 删绑定（模拟绕过业务的改法）：`/auth/info` 立刻 57（无缓存），
   但 `indicator-stats` 仍 200 —— Redis 缓存未失效。结论：**绕开业务直接改库
   会残留旧权限**，必须走接口（此为预期设计，非 bug）。
3. 走真实 PC 接口 `PUT /api/system/roles/1`（去掉菜单 21/108）：
   - `/auth/info` → 57，statistics:view 消失（立即）。
   - `/api/statistics/indicator-stats` → **403**（同一 token，未重登，立即）。
4. 全量 menuIds 回写恢复：58 权限恢复，接口 200。本地库回到基线
   （73 个绑定逐项核对一致；GROUP_CONCAT 截断虚惊一场，实际只动了 21/108）。

## 结论

- **PC 改权限，小程序侧实时生效**：`/auth/info` 无缓存；接口 enforcement 经
  evict+bump 立即生效，不用重登。
- 小程序 UI 门控在下一次 `loadCurrentUser`（重进/回首页/个人页/切集团）即同步；
  后端接口则是当次请求即拦。两者一致，无漂移。
- 附带发现：本地库 `sys_menu` 无 finance:view/contact:view 行（V25 迁移在
  LOCAL_DOCKER_MODE 下未执行，FLYWAY_ENABLED=false），字段级权限的活体验证
  在本地做不了，以静态契约为准；要活体需先在本地库执行 V25。

## Status

验证通过，环境已恢复基线。残留物：无（测试绑定已全部恢复，token 已清）。
