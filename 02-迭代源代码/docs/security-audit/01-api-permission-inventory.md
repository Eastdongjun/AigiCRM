# AIGICRM API 权限清单(审计产出 1/4,2026-09-21)

> 审计代理全量枚举: 31 个 Controller / **213 个端点**。所有控制器无类级 @PreAuthorize。全局兜底 SecurityConfig.java:48-57: permitAll = /api/auth/login、/api/auth/forgot-password/**、/api/system/configs/public、/api/system/configs/logo、/api/file/download/public/**(另 /api/auth/captcha 在放行名单但无对应端点,疑死配置); 其余 anyRequest().authenticated()。

## 总量
- 端点 213:权限码端点 188 / 仅 isAuthenticated() 9 / NO-AUTH 16(匿名 7 + 登录即可 9)
- hasAnyAuthority 11 个;hasAuthority+or 复合 15 个;权限码去重 64 个

## NO-AUTH 清单(16)
匿名 7: login、forgot-password/lookup(脱敏查询,有审计+60s限流)、forgot-password/send-code(短信轰炸面,有IP限流)、forgot-password/reset、configs/public(白名单7键)、configs/logo、**file/download/public/**(public/ 前缀匿名开放,防穿越+SVG attachment)。
登录即可 9: auth/info、logout、switch-group(service校验归属)、users/change-password(本人)、common/scope/users|depts(范围service强制)、**system/task/check-expire(全库批处理,方法内 isAdmin 守卫非声明式)**、file/download-token(本人1h)、incentive/permission/check(布尔自检)。
另有 9 个 isAuthenticated() 放宽: FormDraft×4、dict data 读×2、depts tree/list×2、permission-explain×1。

## hasAnyAuthority / OR 复合清单(过宽分析对象)
| 端点 | 表达式 |
|---|---|
| system/users GET/GET{id}/POST/PUT/reset-pwd/DELETE(6) | hasAnyAuthority(x, 'system:org:admin'),方法内 canManageUser 收敛 |
| positions/users/{userId} GET | system:position:view ∨ system:user:view |
| statistics dashboard/daily-stats/weekly-stats/team-activities(4) | statistics:view ∨ dashboard:view |
| customer/group-names | customer:add ∨ customer:edit |
| customer/rankings | score:view ∨ ranking:customer:view ∨ statistics:view(3路OR) |
| customer contacts 写×3 | customer:edit ∨ contact:manage |
| customer 发票/收款写×8 | finance:manage ∨ (customer:edit ∧ finance:view) |

## 各控制器端点→权限速览
- **Auth(7)**: login/info/logout/switch-group/forgot×3 全 NO-AUTH;permission-explain isAuth
- **Customer(43)**: view×14(read族) / add×2 / edit×10 / delete×1 / import×2 / export×1 / OR复合×15(见上)
- **Sea(11)**: view×5 / import×1 / config:view×2 / config:edit×3
- **Task(7)**: view×2 / manage×5
- **Approval(7)+Config(4)**: view×5(submit也是approval:view!) / approve / reject; config→system:config:view/edit
- **Statistics(7)**: 4个 statistics∨dashboard / 3个 statistics:view
- **Score(8)**: manage×3 / entry×1 / view×4
- **Incentive(18)+Permission(4)**: view×10 / edit×4 / add×1 / read-stats / read-ranking;**PUT+DELETE /records/{id} 仅 incentive:view(写操作靠方法内归属守卫: admin∨canEdit∨本人)**; permission: list=system:config:view, grant/delete=system:config:edit, check=NO-AUTH
- **File(16)+FolderAcl(5)+Reconciliation(1)**: view×8 / upload / folder:manage×4+reconciliation / delete / edit(replace) / share / acl:manage×5;download-token NO-AUTH;download/public 匿名
- **Reminder(7)**: 全部 reminder:view(process 也是 view)
- **System 族**: users(6+OR) / roles×7 / depts×8(tree+list isAuth,写 dept:edit) / positions×8 / groups×4 / menus tree=system:menu:view / dict×9(types 读=config:view,data 读 isAuth,写 config:edit) / rules×4 / rule-versions / perm-scopes×4 / configs×4 / logs×2+audit-logs(system:log:view) / task×1

## 权限码去重(64)
system: config:view/edit, log:view, position:view/add/edit/delete, user:view/add/edit/delete, org:admin, dept:view/edit, group:view/add/edit/delete, menu:view, rule:view/add/edit/delete, role:view/add/edit/delete · customer: view/add/edit/delete/import/export · contact:manage · finance: view/manage · sea: view/import, config:view/edit · task: view/manage · approval: view/approve/reject · reminder:view · file: view/upload/edit/delete/share, folder:manage, acl:manage · score: view/manage/entry · ranking:customer:view · statistics:view · dashboard:view · incentive: view/add/edit, read-stats, read-ranking:view

## 关键风险提示(待深审)
1. Incentive PUT/DELETE /records/{id} 仅 view 码,写靠方法内守卫(注释称有意,需行为验证)
2. /api/auth/captcha 放行但无端点(死配置)
3. ScheduledTaskController 方法内 if 鉴权,绕开声明式切面
4. approval submit 用 approval:view(提交审批门槛=查看门槛)
5. customer/rankings 三路 OR(排行榜暴露面待核数据范围)
