package com.aigicrm.common.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【WP3 鉴权内核 2026-11-13】范围查询注入器(规格 §3.3)。
 * 模块唯一允许的数据范围写法: {@code scopeQuery.apply(wrapper, "customer:view")} —
 * 禁止模块自己写 IN owner_id(规格 §3.3), 列表/统计/导出统一走本入口(卡片=列表=导出, 锁定决策#9)。
 *
 * 规则(规格 §3.3):
 *   码未注册 → 1=0(fail-closed; 注册表=V2026.11.13.2 已全量回填, 未注册即异常配置)
 *   scope_required=0 → no-op(入口/路由码)
 *   all → 不加条件(租户拦截器兜底)
 *   OWNER → owner_column IN (...); ORG → dept_column IN (...)
 *   空集 → 1=0(显式恒空, 防 IN () 语法错误=A6 铁律)
 *   可见用户 > 5000 → 交由 WP8 性能批改闭包 join(本版阈值护栏日志告警)
 */
@Component
@RequiredArgsConstructor
public class ScopeQuery {

    /** 大集合护栏阈值(超限告警, WP8 性能批升级为闭包 join) */
    public static final int LARGE_SET_THRESHOLD = 5000;

    private final AuthorizationService authorizationService;
    private final PermActionRegistry registry;

    /** 注入数据范围条件; 返回 true=加了范围, false=no-op(scope_required=0) */
    public boolean apply(QueryWrapper<?> wrapper, String actionCode) {
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        if (def == null) {
            // 码未注册 → fail-closed 恒空(不得静默放行全量)
            wrapper.apply("1=0");
            return true;
        }
        if (!def.isScopeRequired()) {
            return false; // 入口/路由码: 不参与数据范围(规格 §3.3)
        }
        ScopeDecision d = authorizationService.scope(actionCode);
        if (d == null) {
            wrapper.apply("1=0");
            return true;
        }
        ScopeRequest.AnchorType anchor = dominantAnchorOf(actionCode, def);
        if (anchor == ScopeRequest.AnchorType.ORG) {
            List<Long> deptIds = d.getVisibleDeptIds();
            if (deptIds == null) return false; // all: 租户拦截器兜底
            if (deptIds.isEmpty()) {
                wrapper.apply("1=0");
                return true;
            }
            guardLarge(deptIds, actionCode, "dept");
            wrapper.in(def.getDeptColumn(), deptIds);
            return true;
        }
        List<Long> ownerIds = d.getVisibleUserIds();
        if (ownerIds == null) return false; // all
        if (ownerIds.isEmpty()) {
            wrapper.apply("1=0");
            return true;
        }
        guardLarge(ownerIds, actionCode, "owner");
        wrapper.in(def.getOwnerColumn(), ownerIds);
        return true;
    }

    private ScopeRequest.AnchorType dominantAnchorOf(String actionCode, PermActionRegistry.ActionDef def) {
        // 与 AuthorizationService.assertData 同口径(主导锚点), 委托其内部判定避免两处实现漂移
        return authorizationService.dominantAnchorForQuery(actionCode, def);
    }

    private void guardLarge(List<Long> ids, String actionCode, String dim) {
        if (ids.size() > LARGE_SET_THRESHOLD) {
            org.slf4j.LoggerFactory.getLogger(ScopeQuery.class)
                    .warn("范围集合过大({} {} ids, code={})——p95 风险, WP8 性能批应改闭包 join", dim, ids.size(), actionCode);
        }
    }
}
