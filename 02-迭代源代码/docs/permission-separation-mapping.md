# PR-06 权限分离:影响映射与预览

> **版本**:2026-09-19 · **原则**:新增权限码以 OR 模式兼容旧码,存量角色零破坏

## 现状 → 目标映射

| 操作 | 现有权限 | 新增权限码(OR 兼容) | 影响 |
|---|---|---|---|
| 客户基础编辑(名称/行业等) | `customer:edit` | 不变 | 无变化 |
| 联系人增/改/删 | `customer:edit` | + `contact:manage` OR | 可单独授权联系人管理,不必给客户编辑 |
| 财务录入(开票/回款) | `customer:edit` AND `finance:view` | + `finance:manage` OR(原组合) | 财务人员可录票据,不必有客户编辑权 |
| 跟进日志 | `customer:edit` | 不变(销售自服务) | 无变化 |
| 财务查看(字段级) | `finance:view` | 不变(已有) | 无变化 |

## 新权限码

| 码 | 含义 | 典型持有者 |
|---|---|---|
| `contact:manage` | 联系人 CRUD(与客户编辑解耦) | 行政/客服 |
| `finance:manage` | 开票/回款录入与删除(与客户编辑解耦) | 财务 |

## 兼容性保证

- 存量持 `customer:edit` 的角色:行为不变(OR 条件天然兼容)
- 存量持 `customer:edit + finance:view` 的角色:财务写入不变
- 新增角色可只给 `contact:manage` 或 `finance:manage`,实现职责分离
