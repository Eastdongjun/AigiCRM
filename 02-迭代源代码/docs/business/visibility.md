# 数据范围与集团归属

> 来源锚点:`OrgScopeResolver`、`GroupScopeUtil`、`ScopeService`、`MybatisPlusConfig`(租户拦截器)。核实日期 2026-09-24。

## 1. 数据范围五档

| 档 | 语义 |
| --- | --- |
| `all` | 全部(仅 admin 真跨集团;非 admin 的 all 收敛为当前集团) |
| `self` | 仅本人 |
| `team` | 本团队(部门树向下,深度上限 5) |
| `office` | 本办事处 |
| `custom` | 自定义组织集合(sys_role_org) |

- 优先级:用户级 override > 岗位绑定 > 角色默认;多角色并集。
- 模块级细化:`resolveModuleUsers(user, module)` 支持 statistics/reminder/score/system 等模块各自的范围配置。

## 2. 集团归属链(fail-closed)

```
用户集团集合 = 岗位 position.org_id → dept.group_id(有岗位优先)
             ∪ sys_user.org_id → dept.group_id(仅无岗位者回退)
```

- **org_id 为空的非 admin 用户:全部租户白名单表查询恒空**(tenant_id=NULL 恒假)——fail-closed 设计,不是 bug。
- admin 角色豁免租户条件,这会掩盖普通用户的数据不可见问题(单一 admin 账号验收测不出)。
- 历史事故与根修:legacy 库 67 用户 org 从未配置(V2026.09.23.3 回填);新建/编辑用户自动落 org(SysUserService.syncDefaultOrg,2026-09-23)。
- **【修复 2026-10-08 需求对齐】`GroupScopeUtil.userIdsInGroup` 已收口为与登录推导链同口径**:原实现直挂 `sys_position.group_id` 且无条件 UNION(岗位 group_id 与其 org 部门 group_id 不一致时,同一用户登录推导与选人/转移校验归属不同集团);现改为岗位经 `position.org_id→dept.group_id` 推导、无岗位者才回退本人 org——单集团部署下行为不变,多集团上线前隐患已消除。

## 3. 两类"选人/筛选"下拉的分工(09-24 定型)

| 场景 | 接口 | 边界 |
| --- | --- | --- |
| **筛选**(提醒负责人筛选等) | `/common/scope/users` | 数据范围感知(范围感知筛选器,设计文档 §6.3 统一入口) |
| **选人**(转移/分享接收人) | `/customer/users/simple` | 当前集团成员(与业务边界一致,不挂数据范围) |
| 激励人员筛选 | `/incentive/users` | 分享制语义(本人+有 shared 记录的作者) |

误挂后果实证:选人接口挂数据范围 → 跨团队合法转移选不到人;筛选接口挂集团边界 → 选到范围外的人得到空列表。

## 4. 各模块可见性口径对照(易混淆,逐个记)

| 模块/数据 | 口径 |
| --- | --- |
| 客户/文件/提醒/审批/任务 | 数据范围(五档)+集团租户 |
| 激励记录 | **分享制**(独立体系,见 [incentive.md](incentive.md) §3) |
| 激励个人排行/积分榜 | 公司级全量(含未分享聚合) |
| 统计-四项激励客户排行 | 分享制(09-24 对齐) |
| 统计-客情/六项指标 | 客户可见范围(G2 安全收口,矩阵回归钉住;与激励口径**有意不同**) |
| sys_log/score | 集团内(WithinGroup) |

## 5. 排查姿势(沉淀自三次实战)

"某用户看不到数据"类问题,按序:
1. **数据侧计数**(表里有没有、tenant/shared 归属对不对);
2. **同角色临时用户 curl 探测**复现(用完即删;admin 账号看一切正常不能证明没问题);
3. 区分 403(权限码缺)/200 空(范围语义)/200 有数据。
4. 403 越权拒绝有结构化安全审计(BS-A09:uri/用户/IP);但**服务层 400"无权"业务异常只落 WARN**,且文案不进审计——这类仍靠同角色临时用户探测(2026-09-24 更正旧说法)。
