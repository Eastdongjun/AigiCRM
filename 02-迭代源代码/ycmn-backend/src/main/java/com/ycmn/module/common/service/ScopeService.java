package com.ycmn.module.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** 范围感知筛选器（设计文档 §6.3）：所有模块的人员/部门下拉统一入口，按当前用户数据范围过滤 */
@Service
@RequiredArgsConstructor
public class ScopeService {

    private final OrgScopeResolver resolver;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /** 当前用户可见人员列表；deptId 非空时限定该部门子树（含全部子部门）∩ U */
    public List<Map<String, Object>> visibleUsers(SecurityUser user, Long deptId) {
        java.util.Set<Long> deptScope = deptId != null
                ? new java.util.LinkedHashSet<>(com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId))
                : null;
        List<Long> u = resolver.resolve(user);
        if (u == null) { // all：全部启用用户
            return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1)
                            .in(deptScope != null, SysUser::getDeptId, deptScope)
                            .orderByAsc(SysUser::getRealName))
                    .stream().map(this::toUserMap).toList();
        }
        if (u.isEmpty()) return List.of();
        return userMapper.selectByIds(u).stream()
                .filter(x -> deptScope == null || deptScope.contains(x.getDeptId()))
                .map(this::toUserMap)
                .sorted(Comparator.comparing(m -> String.valueOf(m.get("realName"))))
                .toList();
    }

    /**
     * 当前用户可见部门树（U 聚合）。
     * null=all：admin 返回完整树，非 admin 收窄为本人部门+祖先链（组织架构不向非管理员全量暴露，
     * 与 SysDeptService.tree 的 FEAT-20260804-DEPTS-DATASCOPE 收窄口径一致）；空=空树；
     * 否则 resolveVisibleDeptIds 取可见部门集合 → selectByIds 批量查详情（过滤 status=1，与 all 分支口径一致）→
     * 按 parent_id 组装树，层内按 sortOrder 升序；parent 不可见的节点提升为顶层。
     */
    public List<SysDept> visibleDeptTree(SecurityUser user) {
        List<Long> deptIds = resolver.resolveVisibleDeptIds(user);
        if (deptIds == null) { // all
            List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder));
            return buildPartialTree(narrowToOwnChain(user, all));
        }
        if (deptIds.isEmpty()) return List.of();
        List<SysDept> depts = new ArrayList<>(deptMapper.selectByIds(deptIds));
        // 与 all 分支口径一致：禁用部门不进入可见树
        depts.removeIf(d -> !Objects.equals(d.getStatus(), 1));
        depts.sort(Comparator.comparing(SysDept::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysDept::getId));
        return buildPartialTree(depts);
    }

    private Map<String, Object> toUserMap(SysUser x) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", x.getId());
        m.put("realName", x.getRealName());
        m.put("deptId", x.getDeptId());
        return m;
    }

    /**
     * 非 admin 收窄：仅本人部门+祖先链（与 SysDeptService.scopeFilter 同口径，
     * FEAT-20260804-DEPTS-DATASCOPE；以传入 user 为准而非 SecurityContext，可单测）。
     * admin 返回全量；deptId 为空返回空列表。
     */
    private List<SysDept> narrowToOwnChain(SecurityUser user, List<SysDept> all) {
        if (user != null && user.hasRole("admin")) return all;
        Long myDeptId = user != null ? user.getDeptId() : null;
        if (myDeptId == null) return Collections.emptyList();
        Map<Long, SysDept> byId = all.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Set<Long> keep = new HashSet<>();
        Long cur = myDeptId;
        while (cur != null && byId.containsKey(cur)) {
            keep.add(cur);
            cur = byId.get(cur).getParentId();
        }
        return all.stream().filter(d -> keep.contains(d.getId())).collect(Collectors.toList());
    }

    /** 部分集合树组装：parent 不可见的节点提升为顶层；入参已按 sortOrder 排序，挂接顺序即层级顺序 */
    private List<SysDept> buildPartialTree(List<SysDept> depts) {
        Map<Long, SysDept> byId = new HashMap<>();
        for (SysDept d : depts) byId.put(d.getId(), d);
        List<SysDept> roots = new ArrayList<>();
        for (SysDept d : depts) {
            SysDept parent = byId.get(d.getParentId());
            if (parent == null) {
                roots.add(d);
            } else {
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(d);
            }
        }
        return roots;
    }
}
