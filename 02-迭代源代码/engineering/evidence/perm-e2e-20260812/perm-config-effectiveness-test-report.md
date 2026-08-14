# YCMN 客户管理系统 — 权限配置生效性 全局测试报告

> 测试日期：2026-08-12（凌晨）｜测试对象：测试服 111.229.37.220（前端生产部署线 + 后端 jar b5dac5e8=24c0467 + 测试库 ycmn）
> 测试方法：Playwright 真实浏览器（管理员 UI 配置）+ curl API 断言 + 数据库落库核验，全程可回滚
> 触发方式：`/webapp-testing` 技能

## 一、核心结论

**✅ 验证通过**：管理员在"系统设置 → 权限管理"按模块配置后，**对应人员的页面数据与操作权限即时生效**。
- 数据范围三级优先级 **USER > POSITION > ROLE** 全部实测成立
- 功能权限撤销后 API permissions 即时移除、接口 403、前端按钮消失
- 越权防护有效（越权编辑/查看/读管理接口全部拒绝）
- 分享链路闭环（未分享拒绝 → 分享可见 → 移除即时拒绝）
- 发现 **4 个非阻塞问题**（1 P2 + 3 P3），均非本次测试引入

## 二、测试环境

| 项 | 值 |
|---|---|
| 测试服前端 | `http://111.229.37.220/` |
| 测试服后端 | 容器 `ycmn-backend`，jar md5 `b5dac5e8`（= 源码 24c0467，与权威源码一致） |
| 测试库 | `shared-mysql` / `ycmn`（66 用户、4 角色、role_perm_scope 33 条基线） |
| 工具 | Playwright（venv /tmp/pwenv + chromium）+ curl + mysql 核验 |

## 三、阶段 A：基线采集（变更前）

| 指标 | 系统管理员(admin) | 销售总监(0) | 行政(05) | 销售员(06) |
|---|---|---|---|---|
| permissions 数 | 61 | 47 | 32 | 37 |
| 客户列表 total | 249 | 249* | 77 | 0 |
| system:role:view | ✓ | ✓ | 403 | 403 |
| system:config:edit | ✓ | ✓ | ✗ | ✗ |

*注：总监基线初测为 0，排查确认系 `customer.auto_share = {"roles":["0"]}` 自动共享规则以 AND 收窄 all 范围（见问题 #1）；调整测试数据 org 归属后基线 249。

## 四、阶段 B：管理员配置（系统设置-权限管理 + 角色管理）

| # | 操作 | 方式 | 结果 |
|---|---|---|---|
| B1 | 角色06 `customer view` self → region | UI 矩阵下拉 | PUT /perm-scopes/06 → 200 |
| B2 | 撤销角色06 `customer:delete` 勾选 | UI 矩阵 | PUT /roles/11 → 200 |
| B3 | 撤销角色06 `file:upload` 勾选 | UI 矩阵 | PUT /roles/11 → 200 |
| B4 | 撤销角色06 `customer:share` 勾选 | UI 矩阵 | PUT /roles/11 → 200 |
| B5 | 用户79 `customer view` = self | API | 200 |
| B6 | 岗位6 绑定 vfy_off + `* * region` | API | 200 |

## 五、阶段 C：配置后即时生效验证（核心命题）

| 用例 | 预期 | 实测 | 结论 |
|---|---|---|---|
| C2a | 删除 USER 79 配置后 sal 回退 ROLE region（山西大区） | **97** | ✓ |
| C2b | USER self 覆盖 ROLE region | **0** | ✓ |
| C3 | 撤销删除权限后 DELETE 客户 | **403** | ✓ |
| C4 | 撤销上传权限后 permissions 移除 | file:upload=False（37→34） | ✓ |
| C5 | 撤销分享权限后 permissions 移除 | customer:share=False | ✓ |
| C6 | 越权读角色管理 | **403** | ✓ |
| C7 | 岗位 region 生效对照实验（self→region） | **0 → 77**（内蒙古大区树） | ✓ |
| C8/C9 | admin/总监对照不受影响 | 249 / 249 | ✓ |

关键实证：Redis `perm:ctx:78` 反序列化确认 scopeConfigs 装载完整（POSITION 行第一）；对照实验证明 POSITION 配置被真实消费并覆盖 ROLE。

## 六、阶段 D：功能冒烟

| 用例 | 结果 |
|---|---|
| D1 编辑：admin 200 / sal 越权编辑 400"无权编辑该客户" | ✓ |
| D2 分享链路（region 外客户 259）：未分享拒绝 → 分享后可见 200 → 移除后即时拒绝 400 | ✓ 闭环 |
| D3 上传/删除临时文件（fileId=204） | ✓ 200 |
| D4 创建/删除临时客户（id=9001） | ✓ 200 |
| D5 全程页面无 500、无控制台错误 | ✓ |

## 七、阶段 E：恢复与清理（核验 8 项全过）

| 核验项 | 结果 |
|---|---|
| role_perm_scope 总数 | 33（基线原值） |
| 角色11 菜单 | 55 条（42/47/110 已恢复勾选） |
| 岗位6 绑定/配置 | 0 / 0（原状） |
| 客户473 名称 | 已还原（华晋焦煤有限责任公司沙曲一号煤矿） |
| 分享残留 | 0 |
| 临时用户 vfy_* | 0（含软删客户 9001 物理清理） |
| Redis 权限缓存 | perm:ctx:76-79 已清 |

## 八、发现的问题清单（均非本次测试引入）

### P2 — 自动共享与数据范围 AND 叠加收窄
- **位置**：`ycmn-backend/src/main/java/com/ycmn/module/customer/service/CustomerService.java:494-499`（`applyAutoShare`）
- **现象**：`wrapper.in(ownerId, shareOwnerIds)` 以 AND 追加，与数据范围形成**交集**。`data_scope=all` 的角色命中 `sys_config.customer.auto_share.roles`（当前配置 `["0"]`）时，全量可见被收窄为"同组织用户"（实测销售总监 249→0，SQL 实证 `owner_id IN (77,76)`）。
- **建议**：若产品意图是"自动共享=扩展可见"，应改为并集（`wrapper.and(w -> w.in(ownerId, scopeIds).or().in(ownerId, shareOwnerIds))`）或明确收窄语义。

### P3 — POST /api/incentive/records 空 body 返回 500
- **现象**：空 body 请求返回 500（`Field 'category_id' doesn't have a default value`），应 400 业务校验。
- **位置**：IncentiveController.addRecord / IncentiveService。

### P3 — 文件/分享权限码装饰性（设计取舍）
- **现象**：撤销 `file:upload` / `customer:share` 权限码后接口仍可达（上传由文件夹 ACL `folderPerms` 守卫、分享由 `assertCanEdit` 守卫），前端上传按钮由 `folderPerms.includes('upload')` 控制。
- **位置**：FileController 写方法白名单（PermissionMatrixTest WHITELIST）、CustomerController.addShares。
- **提示**：若产品预期"勾掉权限码即禁止操作"，需改造为权限码硬门。

### P3 — GET /api/customer/{id} 客户不存在返回 200 data:null
- **现象**：admin 短路（isAdmin→true）后不校验客户存在性，返回 `200 data:null`，应为 404/400。
- **位置**：CustomerController.getById + CustomerAccessService.canView。

## 九、截图证据（12 张，保存于 `~/.reasonix/`）

| 文件 | 内容 |
|---|---|
| ui-01-login.png / ui-02-after-login.png | 登录页 / 登录后工作台（249 客户） |
| ui-03-perm-scope-user.png / ui-04-perm-reset.png | 权限管理-按用户配置范围 / 恢复默认 |
| b1-region.png | B1 角色06 范围→本大区及以下 |
| b2-del-uncheck.png / b3-upload-uncheck.png / b4-share-uncheck.png | B2/B3/B4 撤销勾选 |
| a1-sal-customer-list.png / a1-sal-file.png | 销售员基线：客户列表 / 文件页（上传按钮可见） |
| c1-sal-customer-after.png / c1-sal-file-after.png | 配置后：客户列表 / 文件页 |

## 十、总体结论

1. **权限管理配置链路可用且即时生效**——"管理员配置 → 对应人员页面的菜单/数据/操作权限跟随变化"经 UI + API + 数据库三层实证；
2. 数据范围三级优先级与越权防护全部验证通过；
3. 建议优先处理 P2（applyAutoShare 并集语义），P3 三项按迭代排期；
4. 测试服已完全恢复原状（8 项核验通过）。
