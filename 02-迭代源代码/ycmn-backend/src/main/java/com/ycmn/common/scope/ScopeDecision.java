package com.ycmn.common.scope;

import lombok.Data;

import java.util.List;

/**
 * 统一数据访问层解析结果(批② 2026-08-10)。
 * 语义: null=全部可见(不过滤); 空集合=不可见(无数据); 非空=可见集合。
 * OWNER 锚点看 visibleUserIds; ORG 锚点看 visibleDeptIds。
 */
@Data
public class ScopeDecision {

    private List<Long> visibleUserIds;
    private List<Long> visibleDeptIds;

    public static ScopeDecision all() {
        ScopeDecision d = new ScopeDecision();
        d.visibleUserIds = null;
        d.visibleDeptIds = null;
        return d;
    }

    public static ScopeDecision none() {
        ScopeDecision d = new ScopeDecision();
        d.visibleUserIds = List.of();
        d.visibleDeptIds = List.of();
        return d;
    }

    public boolean isAll() {
        return visibleUserIds == null && visibleDeptIds == null;
    }

    /** OWNER 锚点: 目标负责人是否可见 */
    public boolean canAccessOwner(Long ownerId) {
        if (ownerId == null) return false;
        if (visibleUserIds == null) return true; // all
        return visibleUserIds.contains(ownerId);
    }

    /** ORG 锚点: 目标部门是否可见 */
    public boolean canAccessDept(Long deptId) {
        if (deptId == null) return false;
        if (visibleDeptIds == null) return true; // all
        return visibleDeptIds.contains(deptId);
    }
}
