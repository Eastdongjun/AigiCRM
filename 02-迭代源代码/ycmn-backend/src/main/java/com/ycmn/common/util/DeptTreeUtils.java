package com.ycmn.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.mapper.SysDeptMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 部门树工具：组织筛选统一"含全部子部门"语义（部门可无限嵌套）。
 * 各模块列表接口的 deptId 参数均通过本工具展开为子树集合。
 */
public final class DeptTreeUtils {

    private DeptTreeUtils() {}

    /** 展开指定部门为子树集合（含自身，递归任意层级） */
    public static List<Long> expandSubtree(SysDeptMapper deptMapper, Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        if (rootId != null) {
            result.add(rootId);
            collect(deptMapper, rootId, result);
        }
        return new ArrayList<>(result);
    }

    private static void collect(SysDeptMapper m, Long parentId, Set<Long> acc) {
        List<SysDept> children = m.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId)
                .eq(SysDept::getStatus, 1));
        for (SysDept d : children) {
            if (acc.add(d.getId())) collect(m, d.getId(), acc);
        }
    }
}
