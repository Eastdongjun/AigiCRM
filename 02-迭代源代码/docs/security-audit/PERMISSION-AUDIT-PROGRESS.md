# AIGICRM 权限体系深度审计进度(任务书执行档案)

> 审计纪律: 不改代码; 未验证不写通过; 部分完成必须写明。启动: 2026-09-21。

## 阶段状态

| 阶段 | 内容 | 状态 | 产出 |
|---|---|---|---|
| 1 | 理解现有权限架构 | 进行中(会话内已实测认证/角色/范围链路) | 本文件+会话证据 |
| 2 | 权限码/API全量Inventory | **静态枚举完成** | 01-api-permission-inventory.md(213端点/188码/16NO-AUTH/64码,5项风险待深审) |
| 3 | 集团/Tenant隔离 | **静态完成** | 04-tenant-native-sql-audit.md(Top1统计绕集团[高]/Top2租户开关默认关[高]/评分跨租户删[中]) |
| 4 | 角色/岗位/用户/DataScope | 部分完成(4角色+范围E2E已实测) | evidence/summary.md |
| 5 | 对象级权限与IDOR | **静态完成** | 02-idor-audit.md(65端点: 53守卫完整/5风险: finance4端点缺assertCanEdit[P1]+task参数IDOR[P1]/7可接受) |
| 6 | 字段权限(contact/finance) | 部分(API级断言已覆盖IDOR线; 字段脱敏返回体未深查) | 02-idor-audit.md |
| 7 | PC权限矩阵 | 未开始 | - |
| 8 | 小程序权限矩阵 | 未开始 | - |
| 9 | 统计/导入导出/文件/审批/公海 | 统计✓(G2+矩阵) 导出✓(矩阵#7) 审批ANY/ALL✓(#15) 导入/公海claim并发未深查 | 本文件+矩阵 |
| 10 | 缓存与fail-open | **静态完成** | 03-fail-open-audit.md(PR-01三项声明核验属实; F1/F2规则静默丢弃P2; anchorOf残留P3) |
| 11 | 测试脚本本身审计 | ✅ 已分类(见16项#16) | 本文件 |
| 12 | 攻击矩阵与负向测试 | ✅ verify-perm-matrix.sh 16用例(含双集团夹具/导出/finance/task/统计) | scripts/verify-perm-matrix.sh |
| 13 | 最终审计报告 | 未开始 | - |

## 已核实事实(本会话, 测试环境实测)
- 授权链路: 菜单绑定→权限码(不分menu_type全量加载)→@PreAuthorize强制; perm_version控制缓存, 勾选/撤销对在线会话即时生效(403→200→403实测)
- 数据范围: role.data_scope + role_perm_scope模块级覆盖(USER>POSITION>ROLE), PUT保存自动全量失效+版本递增; self=0条/all=274条/dept+ORG无部门=0条 实测
- 菜单卫生: V2026.09.21.1迁移清11行垃圾+62归类修正(详见git log)

## 16项特别关注(逐项待填: 仍存在/已修复/误报/无法确认)
1.客户排行rawSQL跨集团? 【已验证】JdbcTemplate路径显式tenant安全;但StatisticsQueryService口径无集团(Top1,条件=TENANT_ENABLE=false) 2.无集团默认集团1? 【已修复】GroupScopeUtil无集团→空集/null/false不回退 3.激励阅读统计跨集团? 【非admin安全/admin全集团(设计)】IncentiveService:1126 r.tenant_id=?+u.id IN集团 4.markRead IDOR? 【行为验证: 无此问题】他人记录POST read→404"记录不存在或无权访问"(idor-bot仅incentive:view,scope=all,记录335) 5.激励专项人员列表跨集团? 【安全】非admin按集团成员(IPC:51-59) 6.all人员选择器全系统? 【安全】ScopeService:45-49非admin套集团边界 7.role_perm_scope异常回退宽权限? 【已验证fail-closed】三处503属实;残留anchorOf:198回退OWNER(P3) 8.JWT blacklist失败fail-open? 【已验证fail-closed】Redis异常→DB回查,DB失败→拒绝(:137/:243) 9.找回密码枚举? 【缓解到位】中性化响应+ANON_PROBE审计+限流429;建议持续监控 10.生产TENANT开关? 【不一致确认】TENANT_ENABLE默认false;prod兜底+Swagger放行均依赖prod profile激活,是部署假设非代码保证 11.改角色后小程序收敛时机? 已实测(perm_version即时) 12.文件IDOR? 【行为验证: 无此问题】无file权限→403; 授file:view后访问他人私人文件1092→仍403(对象级守卫生效) 13.导出突破view? 【已验证不突破】export 复用与列表同一 page() 查询(同 dataScope); 行为实证: self 用户导出 HTTP200 xlsx=6636字节(表头+0行) < admin=431751字节(274行) 14.统计突破明细? 【仍存在(条件性)】StatisticsQueryService全员口径无集团无范围,唯一防线租户插件且默认关 15.ANY/ALL审批执行? 【已验证真正执行】ApprovalService:403-424 或签ANY(任一通过即节点完成/兄弟取消)+会签ALL(全员通过)+串行阶段; 行为单测: ApprovalServiceTest:352(ANY)/388(ALL) 16.测试脚本仅字符串存在? 【已分类】结构性grep: dev-verify系列契约脚本(131项, 存在性检查); 真行为测试: verify-perm-matrix.sh(16用例, 403/200/字节级断言)+后端579单测(含审批ANY/ALL/统计过滤/权限码wrapper); 二者互补, 契约脚本不得单独作为安全结论

---

# 🎯 GOAL(2026-09-21 制定):权限审计整改全链路闭环

> 完成判据全部可机器验证;顺序执行,每批走"修复→测试→负向用例→证据→提交"闭环;只动测试服,不碰生产。

## G1 部署批次1(最高优先) — ✅ 已完成(2026-09-21)
- rsync+docker build 部署成功(ycmn-backend 8.6s 启动 health 200)
- ✅ 判据达成: verify-perm-matrix.sh **13/13 PASS**(finance→"无权编辑该客户"语义拒绝; task 他人→403)

## G2 批次2 数据边界收口
- 统计口径二选一: indicatorStats/customerRankings 套 OrgScopeResolver,或产品拍板"全员口径"独立权限并写需求文档
- biz_task 迁移加 tenant_id 列(回填) + 进租户白名单; biz_incentive_read/grant 同评估
- 评分物理删(BizScoreEntry/ParticipantMapper) + 客户/审批取号 SQL 显式租户条件
- ✅ 判据: TENANT_ENABLE=true 下 578+ 测试绿; 取号/删除 SQL 逐条带 tenant

## G3 批次3 fail-open 残留清理 — ✅ 已完成(2026-09-21)
- PermissionRuleEngine 160-180: ignored→log.error+按最严(隐藏/禁止)
- anchorOf:198 改 503; 两个放宽开关默认 true→false; JWT 无 iat/jti 拒绝
- ✅ 判据: 对应单测断言异常路径行为

## G4 批次4 防回归+补审计 — ✅ 核心判据达成(2026-09-21): 测试服租户插件已开启(自检26表全过), 16项关注全部有结论; 阶段7/8完整PC/小程序矩阵文档未产出(留待后续)
- 攻击矩阵: matrix 脚本加双集团夹具 + 导出/统计负向
- 补完阶段6-9: 字段权限(contact/finance API 级) / PC 矩阵 / 小程序矩阵 / 导出审批公海专项
- ✅ 判据: 16 项特别关注全部有结论(仍存在/已修复/误报/无法确认), 无"未验证"残留

## G5 收尾
- 审计产出文档(docs/security-audit/ 4份+进度)提交入库
- 全量门禁绿(578/188/131/miniprogram/sonar 0) + git push(等代理或 SSH 公钥)
- 挂账提醒: gen-*.py 加固提交仍被 Mimosa 钩子拦, 待用户裁决放行方式

---
# gen-*.py 加固最终挂账方式(2026-09-21)
- 三个加固文件已 **git stash 封存**(stash@{0}, 带裁决说明), 工作树恢复干净——钩子扫全树, 未决改动静默阻塞一切提交的问题就此解除
- 恢复路径: `git stash apply` 后按裁决提交(--no-verify)或 `git stash drop` 放弃; 实测结论与拦截记录见上一节


---
# 2026-09-22 GPT 全景报告裁定与新队列
- 裁定全文: 05-gpt-review-20260922-adjudication.md(旧快照报告的 11 项 P1 中 7 项已在 88fbc3fc 前修复)
- **新开放队列 B1-B8**: B1 ExpireTask逐客户隔离+汇总 B2 addToSea幂等 B3 uk_sea_pool_active收编迁移 B4 公海删除复用生命周期保护 B5 archived统一谓词 B6 lookup响应归一 B7 503 HTTP映射 B8 granularity门禁校准
- 审计纪律新增: 产物必须带 snapshot hash 与 stale 标志(05 已示范)
