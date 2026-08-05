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

    /** 当前用户可见人员列表；deptId 非空时限定该部门 ∩ U */
    public List<Map<String, Object>> visibleUsers(SecurityUser user, Long deptId) {
        List<Long> u = resolver.resolve(user);
        if (u == null) { // all：全部启用用户
            return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1)
                            .eq(deptId != null, SysUser::getDeptId, deptId)
                            .orderByAsc(SysUser::getRealName))
                    .stream().map(this::toUserMap).toList();
        }
        if (u.isEmpty()) return List.of();
        return userMapper.selectBatchIds(u).stream()
                .filter(x -> deptId == null || deptId.equals(x.getDeptId()))
                .map(this::toUserMap)
                .sorted(Comparator.comparing(m -> String.valueOf(m.get("realName"))))
                .toList();
    }

    /**
     * 当前用户可见部门树（U 聚合）。
     * null=all 返回完整树（组装与 SysDeptService.tree 一致）；空=空树；
     * 否则 resolveVisibleDeptIds 取可见部门集合 → selectBatchIds 批量查详情 →
     * 按 parent_id 组装树，层内按 sortOrder 升序；parent 不可见的节点提升为顶层。
     */
    public List<SysDept> visibleDeptTree(SecurityUser user) {
        List<Long> deptIds = resolver.resolveVisibleDeptIds(user);
        if (deptIds == null) { // all：完整部门树
            return buildTree(deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                            .eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder)),
                    0L);
        }
        if (deptIds.isEmpty()) return List.of();
        List<SysDept> depts = new ArrayList<>(deptMapper.selectBatchIds(deptIds));
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

    /** 完整树组装（与 SysDeptService.tree 相同模式：根 parentId=0） */
    private List<SysDept> buildTree(List<SysDept> list, Long parentId) {
        return list.stream()
                .filter(d -> Objects.equals(d.getParentId(), parentId))
                .peek(d -> d.setChildren(buildTree(list, d.getId())))
                .collect(Collectors.toList());
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
