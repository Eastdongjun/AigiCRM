# 需求规格

## 目标用户与场景

- 销售员直接输 URL 访问无权限页（统计分析等）→ 跳回首页而非 403 报错页

## 事实

- userStore.permissions 由 fetchUserInfo 加载（layout onMounted）
- 10 个页面需权限：statistics/score:entry/score:ranking/indicator/system:user/org/role/position/config/log

## 假设

- 菜单隐藏的页面 = 需权限守卫的页面

## 阻断问题

无

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 无权限页面 URL 访问跳回首页 | 测试报告 | Must |

## 业务规则与权限

- 接口层 403 保留（纵深防御）；有 view 权限的页面（如 /system/config 对销售员）放行（只读，写操作后端拦）

## 异常与边界

- store 未加载不判断（fetchUserInfo 后检查）

## 非功能需求

- 无

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given 销售员，When 访问 /statistics，Then 跳回 /dashboard + 提示 |
| AC-002 | REQ-001 | Given 管理员，When 访问 /statistics，Then 正常进入 |
| AC-003 | REQ-001 | Given 销售员，When 访问业务页 /customer，Then 正常 |

## 追踪关系

- REQ-001→AC-001~003
