# §8 Batch 0 摘要 — customer:view 新库期望集（2026-11-18）

- 数据集：`aigicrm_prod_20261118`；用户总数 69；**活跃（status=1,deleted=0）28**；错误 0
- 客户基数：非删/tenant=1/非 sea/archived = **295**；有 owner 分布 25 人；有 dept 分布 4 个部门
- 角色分布（活跃用户）：{'admin': 1, '0': 1, '06': 23, '05': 3}
- 有 manager_id 的用户：12

## 范围类型分布（活跃用户）

- ROLE: 20
- USER: 8

## auto_share 结果

- no-share: 22
- narrowed: 4
- all/no-config: 2

## 锚点分布

- OWNER: 25
- ORG: 3

## final_count=0 的用户

- 数量 0：[]

## config_count vs final_count

- 差 0: 27 人
- 差 104: 1 人

## customer:view 的 scope 配置行（role_perm_scope）

- scope_level=team / assignee=USER: 3
- scope_level=custom / assignee=USER: 3
- scope_level=office / assignee=ROLE: 2
- scope_level=all / assignee=ROLE: 2
- scope_level=self / assignee=ROLE: 1
- scope_level=office / assignee=USER: 1
- scope_level=all / assignee=USER: 1

## 范围类型覆盖（§8.1 要求）

- 已出现：['all', 'custom', 'office', 'self', 'team']
- 未出现（需夹具/待核）：['dept', 'dept_and_children', 'field', 'group', 'legal_entity', 'none', 'org', 'org_and_children', 'region']