# AIGICRM 权限设计

> 人工维护文档, 权限码清单见 `permission-dict.md`(自动生成)。
> **修改权限体系必须先更新本文再改代码**(文件新鲜度已纳入 gate 文档项)。
> 最近全量重写: 2026-11-11(此前版本停在 2026-09-13, 落后代码两月——按"底座评估"重写;
> 旧文"两层授权/数据范围来源=sys_permission_rule/一人只取第一个角色/登出后端无状态"
> 四处表述均与现状不符, 已废弃)。

## 一、认证与会话

```
浏览器/小程序 → JWT(Authorization: Bearer) → JwtAuthFilter
  → 解析 token → 查 sys_user(校验 deleted/status)
  → 权限码集合 = sys_user_role → sys_role_menu → sys_menu.permission (纯 JOIN, 无 admin 隐式旁路)
```

- **多角色并集**: 一人多角色时权限码/数据范围取并集(旧文"只取第一个角色"已废弃);
  数据范围优先级 USER > POSITION > ROLE(见 OrgScopeResolver)。
- **登出/token 撤销**: jti 黑名单(Redis) + DB 兜底 + pwd_changed_at DB 级撤销(旧文"无状态"已废弃)。
- **权限缓存**: perm:ctx(Redis 10min) + 写时 evict + perm_version 比对自愈;
  文件/激励/公海授权**不走缓存**(每请求直查, 勾选即时生效)。
- **租户**: fail-closed(非 admin 无集团 → tenant_id=NULL 恒假), 见 INV-TENANT-001。

### 微信绑定设计（账号密码登录稳定后的接入规格, 保留自旧文）

| 操作 | 前置条件 | 持久化不变量 | 失败处理 | 审计要求 |
|---|---|---|---|---|
| 绑定 | 已登录账号；微信授权返回有效 `openid`，必要时带 `unionid` | 一个 `openid` 只能属于一个未删除用户；同一用户重复绑定必须幂等 | `openid` 已属于其他用户时拒绝，不覆盖原绑定 | 记录用户、时间、客户端和结果，不记录授权原文 |
| 解绑 | 已登录账号；至少保留账号密码登录能力 | 解绑后账号仍可用密码登录；不存在绑定时幂等返回 | 不满足保底登录条件时拒绝解绑 | 记录用户、时间和结果 |
| 换绑 | 先完成旧绑定校验，再绑定新 | 旧绑定释放与新绑定建立必须在同一事务中完成 | 任一步失败整体回滚，禁止双绑定/无绑定半状态 | 分别记录旧绑定释放和新绑定结果 |
| 登录 | 微信身份已绑定 | 只按唯一 `openid` 查找有效用户，并复用现有权限/数据范围装载 | 未绑定时引导账号密码登录后再绑定，不自动创建高权限账号 | 复用登录审计，不把 `openid` 写入普通业务日志 |

实现约束：新增 `openid` 唯一索引/唯一约束前必须先清理历史重复值；微信能力未接入前，本表是设计规格，不代表后端接口已存在。

## 二、授权模型(四层, 2026-11 现状)

| 层 | 机制 | 载体 | 生效时效 |
|---|---|---|---|
| ① 功能码 (RBAC) | `@PreAuthorize`(69 注解码) + 命令式 `SecurityUtils.hasAuthority`(9 码, 双引号形态) | sys_role_menu | Redis 缓存+evict, 秒级 |
| ② 数据范围 (ABAC) | role_perm_scope (module,action) × 七档 → OrgScopeResolver/ScopeDecision | role_perm_scope | 30s 配置缓存 |
| ③ 对象级 (ownership) | customer: CustomerAccessService; file: FolderPermissionService 判定链; sea: 池名单 | 业务表 + grant/ACL 表 | 直查, 即时 |
| ④ 例外授权 | 文件夹对象优先授权(biz_folder_type_default); 激励分享(biz_incentive_grant) | 同上 | 直查, 即时 |

**组合优先级(对象判定链, 以文件为例)**:
`admin > 本人恒管(isOwn) > 上级链 > 例外授权(grant) > 组织矩阵底座(role_perm_scope file 域)`,
任何一层 fail-closed(空集≠全部; OrgScopeResolver null=全部是**显式语义**, 已有测试钉住)。

**功能码×数据范围是两道门**: 端点先过 ①(@PreAuthorize), service 层再过 ②③。
"码有了范围忘了/范围有了码忘了"由三契约防:
- `check-permission-binding-coverage.sh` —— 码→迁移绑定(注解+命令式, 版本序≥种子);
- `check-permission-code-consumption.sh` —— 前端码⊆后端码∪白名单(三端词表);
- `check-scope-axis-contract.sh` —— 动作轴声明=配置面/消费面对账。

**规则引擎(sys_permission_rule)是第 4 套维度**, 目前仅 customer 列表/统计两个入口真 eval——
"有模型没能力"的模块不要在权限中心暴露其配置。

## 三、数据范围动作轴(实况声明表)

权威声明见 `scripts/check-scope-axis-contract.sh`(机器对账, 本表为人读版):

| module | 档位 | 消费点 | 备注 |
|---|---|---|---|
| customer | view/edit/delete/finance_edit | CustomerService 列表/详情/导出/统计; CustomerAccessService | 全档真消费 |
| file | view/manage/delete | FolderPermissionService 组织矩阵(view/download→view; upload/rename/replace/share/edit→manage; delete 独立拍板③) | 2026-11-10 终版 |
| statistics | view | StatisticsQueryService 排行可见集 | ⚠️ 与 customer.view 双 key 并存(已知问题#1) |
| approval/reminder/sea | 仅 view | resolveModuleUsers 可见用户集 | 池级编辑走池名单不入此表 |
| task/score/system | (无) | 不消费 role_perm_scope | 配置面如有行=死配置(已知问题#2) |

## 四、角色体系

4 角色(admin / 05行政 / 0销售总监 / 06销售员, **role_code≠id, 勿硬编码**);
角色×码勾选唯一入口=权限中心(PermCenter 四页签); 生效预览用真实 UserDetailsService
重建 SecurityUser 喂各引擎(PermCenterService)。

## 四b、菜单行与权限码关系(T5 2026-11-12 立)

- **一码一行原则**: 同一 permission 的 menu_type=2 页面行应唯一——多行=勾不同行效果不同、
  字典合并显示, 是"勾了看不到/没勾也能进"歧义源。机器拦截=check-menu-code-uniqueness.sh
  (静态层防新增; 最终态对账走空库重放, 本机无 Docker 时以静态层为准)。
- **多页面行白名单(有意设计, 非违例)**: 同功能域多路由共享码(列表/详情/分组页,
  如 statistics/score/customer/incentive/sea/file:view)与 visible=0 隐藏字典登记行
  (system:user/dept:view); 时序重登记(system:log:view, 05.2 合并→05.3 重登运行态唯一)。
- **NULL permission 禁止新增**(工作台菜单行 NULL 系 2026-11-12 裁决A 特例: dashboard:view
  退役, 菜单可见性与发码解耦, 路由守卫单键 statistics:view)。
- **2/3 混合(页面行+按钮行同码)**: 仅限白名单 customer:add / incentive:stats:view(历史形态)。
- **待产品裁决**: system:config:view 3 页面行(配置组/字典/系统设置)是否合并——台账
  docs/REQUIREMENTS.md。

## 五、关键安全点(踩坑记录, 含旧文保留)

- admin 无隐式旁路: 权限绑定缺一行=全员 403(含 admin)——空库重放契约/绑定覆盖契约防此。
- 越权 403 落结构化安全审计(securityAudit.permissionChange); 留痕失败 log.warn 不阻断业务(T5)。
- 命令式 hasAuthority 双引号形态曾被绑定契约漏扫(customer:invoice-date:edit 空库裸奔被抓出)。
- 迁移禁硬编码行 id 定位/插入(INV-MIG-001/002), 按 permission 自然键锚定。

## 六、已知问题与待拍板(2026-11-11 底座评估)

1. **统计双 key**: ✅2026-11-12 裁决A已执行——statistics 域统一单键 statistics:view(V2026.11.12.2:
   行政按"给全"补绑统计三菜单+dashboard:view 菜单行 permission 置 NULL 退役), 后端 4 端点
   hasAnyAuthority 收窄单码, PC 路由门禁/布局兜底/MP home 同步。逐角色×端点对账(生产实况推演):
   系统管理员 7→7 / 销售总监 7→7 / 销售员 7→7(已持 statistics:view, 补绑条件不触发,
   不误增"统计分析"入口) / 行政 4→7(拍板"给全"接受: 侧栏+统计分析/六项指标/客户排行) /
   其余角色 0→0。客户域 (customer,view) 与统计域分键是业务分层(列表权限≠看板权限), 不在本次统一范围。
2. **role_perm_scope 死配置**: ✅2026-11-12 裁决B已登记——生产实测 31 组中 19 组死(含 7 行空
   role_code 通配死行), 全景+判死依据+A步删行草案入册
   [role-perm-scope-dead-configs.md](role-perm-scope-dead-configs.md); `*/*` 通配 05/06 两行为活兜底勿动。
   A(迁移删行)挂观察期后待令, 观察判据见登记册第四节(file 域死配置已由 V2026.11.09.1 清过)。
3. **孤儿码**: ~~ranking:customer:view~~ ✅2026-11-12 裁决A已执行——V2026.11.12.1 删 sys_menu 行
   与销售员绑定, CustomerController:155 三码 OR 收窄为 score:view ∨ statistics:view(销售员双兜底
   实存, 端点可见集零变化); 生产部署后 gen-permission-dict.sh 重生成即自洽。
4. **词表仍三端手写**(PC 51/MP 30/后端 78): 当前由消费侧契约+词表单一源契约(check-file-action-vocab)
   机器拦截; P1 方案=permission-codes 生成物 + AuthorizationFacade 统一决策点(前端 can() 数据驱动),
   属 1-2 周专项待排期。
5. **0 个 @SpringBootTest**: 权限行为矩阵(角色×端点×预期 401/403/200/空)是 P2 专项; 当前防线=
   单测(Mockito 953) + 三契约 + e2e 钉(CI scheduled) + 空库重放(CI empty-db-replay)。

## 七、演进史(五批)

08-10 统一层(V7/9 权限架构重构) → 09-19 权限分离矩阵 → 10-17 矩阵诚实化 →
11-03 PermCenter 拆码(customer:finance:edit 等) → 11-09/11-10 文件域组织矩阵终版
(逐夹 ACL 端点退役→type-defaults 对象优先授权台)。每批只覆盖当时在做的模块——
"部分模块已接矩阵、部分没接"的断层由此而来, 收敛路线见第六节#4。
