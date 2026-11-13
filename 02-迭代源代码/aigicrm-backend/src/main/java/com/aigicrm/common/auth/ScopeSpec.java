package com.aigicrm.common.auth;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 【WP3 鉴权内核 2026-11-13】范围解析结果值对象(规格 §3.1)。
 * 三态: all=本集团不过滤(锁定决策#6: ALL=本集团); 空集=不可见(fail-closed); 非空=可见集。
 * OWNER 面=ownerIds(负责人集合); ORG 面=deptIds(组织集合)——两维度各自并集, 不混算。
 */
@Data
public class ScopeSpec {

    private boolean all;
    /** OWNER 锚点可见负责人集合(all 时无意义) */
    private Set<Long> ownerIds = new LinkedHashSet<>();
    /** ORG 锚点可见组织集合(all 时无意义) */
    private Set<Long> deptIds = new LinkedHashSet<>();

    public static ScopeSpec all() {
        ScopeSpec s = new ScopeSpec();
        s.all = true;
        return s;
    }

    public static ScopeSpec none() {
        return new ScopeSpec();
    }

    public static ScopeSpec ofOwners(Collection<Long> ids) {
        ScopeSpec s = new ScopeSpec();
        if (ids != null) s.ownerIds.addAll(ids);
        return s;
    }

    public static ScopeSpec ofDepts(Collection<Long> ids) {
        ScopeSpec s = new ScopeSpec();
        if (ids != null) s.deptIds.addAll(ids);
        return s;
    }

    /** 并集合并(锁定决策#1: 多主体/多范围=并集)。任一 all → all 短路。 */
    public static ScopeSpec union(Collection<ScopeSpec> specs) {
        ScopeSpec out = new ScopeSpec();
        for (ScopeSpec s : specs) {
            if (s == null) continue;
            if (s.all) return all();
            out.ownerIds.addAll(s.ownerIds);
            out.deptIds.addAll(s.deptIds);
        }
        return out;
    }

    public void merge(ScopeSpec other) {
        if (other == null) return;
        if (other.all) {
            this.all = true;
            this.ownerIds.clear();
            this.deptIds.clear();
            return;
        }
        if (this.all) return;
        this.ownerIds.addAll(other.ownerIds);
        this.deptIds.addAll(other.deptIds);
    }

    public boolean isEmpty() {
        return !all && ownerIds.isEmpty() && deptIds.isEmpty();
    }

    /** OWNER 面可见负责人集合(null=all 不过滤; 空集=不可见) */
    public List<Long> visibleOwners() {
        return all ? null : new ArrayList<>(ownerIds);
    }

    /** ORG 面可见组织集合(null=all; 空集=不可见) */
    public List<Long> visibleDepts() {
        return all ? null : new ArrayList<>(deptIds);
    }

    /** OWNER 单条判定(本人恒可=兼容现状, 与旧 canAccess 同口径) */
    public boolean canOwner(Long selfId, Long ownerId) {
        if (all) return true;
        if (ownerId != null && ownerId.equals(selfId)) return true;
        return ownerIds.contains(ownerId);
    }

    public boolean canDept(Long deptId) {
        if (all) return true;
        return deptId != null && deptIds.contains(deptId);
    }
}
