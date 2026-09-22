# 业务完整性规则(Agent 审查输入,规范§22/§23)

## 追踪矩阵
- BIZ-01 每条需求(01-产品需求/)→ UI(视图/页面)→ API → Service → 表 → 测试 六层可追踪
- BIZ-02 Requirement 存在但代码缺失 → FUNCTION_MISSING;代码存在无需求 → UNTRACED_IMPLEMENTATION
- BIZ-03 小程序与 PC 共有业务面按 06-pc-mini-parity 矩阵对齐;差异必须是管理面 PC-only 设计

## 语义审查清单(六轮审计实证项)
- BIZ-10 删除主记录前必须先清引用子表(外键约束=500,deleteRecord FK 实证)
- BIZ-11 全量校验 vs 增量校验:编辑场景只校验"本次新引入"的引用,存量引用以原校验为准(编辑误拒实证)
- BIZ-12 分页选项(如客户下拉前100条)不得成为功能可用性边界(编辑回填失败实证)
- BIZ-13 统计口径:archived 统一 notIn(sea,archived);lookup 中性口径注释钉住
- BIZ-14 定时任务逐客户 try/catch+汇总告警,单条 Duplicate 不得中断整晚任务
- BIZ-15 测试真实性:断言必须验行为(403/400/total 值),结构断言(源码 contains)须随契约同改

## 状态机
- BIZ-20 客户:正常→公海(保护:票款/在途审批)→捞取;删除走 assertDeletable 统一口径
- BIZ-21 审批:申请→审批中(对象不可变)→通过/驳回;未知 tab 落最窄范围(applyDataScope)
