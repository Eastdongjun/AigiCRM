# 跨集团与原生 SQL 审计(审计产出 3/4,2026-09-21)

## 租户拦截机制结论:存在自动注入,但默认关闭(部分生效)
1. TenantLineInnerInterceptor 存在(MybatisPlusConfig.java:107-130),租户列 tenant_id,值取 SecurityUser.getCurrentGroupId()(无→集团集合最小值,空→tenant_id=NULL 恒假 fail-closed L145-165);admin 跳过租户条件(L126)
2. 拦截范围:仅白名单 25 张 biz_* 表(L35-42);**sys_* 全部、role_perm_scope、biz_form_draft、biz_incentive_grant/read、biz_token_blacklist 不在名单**,靠业务层 GroupScopeUtil 手工过滤
3. **开关默认关闭:application.yml:65 `${TENANT_ENABLE:false}`;prod 有未开启拒启动兜底(L63-66)但 application-prod.yml 未写死,依赖部署 env**——漏配即拒启,dev/test 默认裸奔
4. 拦截器只覆盖 MyBatis 通道(Wrapper+@Select 均改写);JdbcTemplate 完全绕过
5. 已知收紧缺陷:一人多集团按最小集团过滤(过严非越权,L160-163)

## currentGroupId 链路(不可客户端伪造)
登录取集团集合最小值写 JWT claim(AuthService:122)→JwtAuthFilter:83-92 每请求实时校验归属,失效回退最小集团,无归属 null→SecurityUser.currentGroupId。**不来自 header/参数**。

## 原生 SQL 清单(JdbcTemplate 15 业务处 + 运维 8 处,全量已核)
- **安全(显式租户)**: 客户排行3条(CustomerService:899/915/969,groupCondition 注入 c.tenant_id=? 无集团 AND 1=0)→ **16项#1 客户排行JdbcTemplate路径:安全**;查重(1585/1595);激励阅读统计非admin(IncentiveService:1126,r.tenant_id=?+u.id IN 集团)→ **#3:非admin安全,admin全集团为设计**;canViewRecord Java层比对(1016-1021);激励专项列表非admin按集团成员(IPC:51-59)→ **#5:安全**;GroupScopeUtil 边界工具;删角色连带(SysRoleService:250 按 tenant_id);perm_version/黑名单等运维语句
- **依赖调用方**: SysUserMapper.selectUserPage → applyGroupScope 单入口收敛(SysUserService:93,111-137)→ **#6 all选择器:非admin套集团边界(ScopeService:45-49),安全**
- **@Select 注解 SQL(插件关=裸奔)**: 取号 BizCustomerMapper:10/13 + BizApprovalMapper:9(MAX 编码跨租户,编码语义泄漏);BizApprovalConfigMapper:12(审批配置全集团共用);**评分物理删 BizScoreEntryMapper:10/BizScoreParticipantMapper:10(WHERE period 无租户,score:edit 即触发,A 集团保存周期会删全部集团该周期数据——插件开启时拦截器缓解,关闭时真实跨租户破坏)**

## 风险 Top 项
- **Top1[高] 统计模块全员口径绕开集团边界**: StatisticsQueryService.java:216-432 indicatorStats + :439 customerRankings,selectList(null) 无范围无集团,注释自述"全员可见";数据源 getAllActiveCustomers(CustomerService:827-831)无集团条件。唯一防线=租户插件,TENANT_ENABLE=false 时任何 statistics:view 用户可见**全部集团**客户名/负责人/开票回款/排行 → **16项#14 统计突破明细:仍存在(条件=TENANT_ENABLE=false)**
- **Top2[高] 租户开关默认关**: 与 Top1 组合即真实跨集团泄露;prod 兜底依赖 prod profile 激活(同 Swagger 放行判定 SecurityConfig:42,53-55——默认 profile 启动则 Swagger 匿名全量)→ **16项#10 生产TENANT开关:不一致确认(部署假设而非代码保证)**
- **Top3[中] 评分周期物理删跨租户**(见上)
- **Top4[中低] 取号/审批配置无显式租户**(插件依赖)
