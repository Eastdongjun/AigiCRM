# 技术实施计划

## 已确认影响范围

### 影响范围清单

| 层 | 涉及项 | 核对结果 |
|---|---|---|
| 数据库 | 无 | 无变更 |
| 后端 | SysDeptService.java(tree/list 数据范围过滤) | ycmn-backend 源码 |
| 前端 | 无(loadOrgs 通用) | 零改动 |
| 其他 | 无 | — |

### 隐藏入口检查

- [x] 后台管理员操作:管理员路径不受影响(isAdmin 全量)
- [x] 定时任务/自动流程:无
- [x] 历史数据兼容:非管理员 dept_id 为空 → 空列表(前端容错)

## 架构与关键决策

1. **修复方式**:保留 system:dept:view 权限与菜单绑定(前端激励查询页部门筛选依赖该接口),
   通过查询数据范围收窄实现:非管理员仅返回本人部门及祖先链
2. **备选拒绝**:解绑权限会破坏销售员激励查询部门筛选(不可取);脱敏接口需前端改动(过重)
3. **实现位置**:SysDeptService 私有方法 scopeFilter(),tree()/list() 共用

## 数据、API 与事件契约

- GET /api/system/depts、/tree:响应结构不变(List<SysDept>);非管理员返回子集
- 过滤规则:isAdmin → 全部;否则取 getCurrentDeptId(),按 parentId 上溯至根,保留祖先链

## 权限与安全边界

- system:dept:view 权限码不变;数据范围与销售员 self 口径一致
- 无敏感字段新增/暴露

## 实施顺序

1. 修改 SysDeptService.java(scopeFilter + tree/list 接入)
2. mvn compile/test 回归
3. 构建 jar → 测试环境部署验证(TY101 只见部门4+祖先;admin 全部)
4. 生产部署(deploy-backend.sh,用户已授权修复)
5. 生产验证 + 激励页回归

## 测试策略

- AC-001:TY101 GET /api/system/depts → 仅部门4+祖先链,无其他部门
- AC-002:admin GET → 全部启用部门
- AC-003:激励查询页 TY101 打开 → loadOrgs 建树正常无报错
- 回归:mvn test 全量

## 迁移、发布与兼容

- 无迁移;jar 构建后测试→生产标准流程;前端无需联动

## 可观测性

- 变更记录在功能包 evidence;部署记录 deploy.log

## 回滚

- 回滚旧 jar(NAS 备份 ycmn-backend-jar-20260804-002106.jar + rollback.sh)
- 回滚后验证:TY101 depts 恢复全量(旧行为)

## 风险与盲区

- 非管理员 dept_id 为空 → 空列表(激励页部门筛选为空,页面不报错)——可接受
- 祖先链若被禁用(status!=1)→ 截断,仅返回可见部分
