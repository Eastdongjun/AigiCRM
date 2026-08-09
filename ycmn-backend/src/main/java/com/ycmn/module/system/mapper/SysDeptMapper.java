package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysDept;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 递归查询所有下级部门ID（含自身；org 与 dept 同一 ID 空间，语义等价原 SysOrgMapper.selectDescendantIds） */
    @Select("WITH RECURSIVE dept_tree AS (" +
            "  SELECT id FROM sys_dept WHERE id = #{deptId} AND status = 1 " +
            "  UNION ALL " +
            "  SELECT d.id FROM sys_dept d INNER JOIN dept_tree t ON d.parent_id = t.id AND d.status = 1" +
            ") SELECT id FROM dept_tree")
    List<Long> selectDescendantIds(Long deptId);
}
