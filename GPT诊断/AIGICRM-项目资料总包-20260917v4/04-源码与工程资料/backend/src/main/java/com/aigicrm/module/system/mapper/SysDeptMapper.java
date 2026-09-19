package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysDept;
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

    /**
     * 沿父链取最近一个非空 group_id（含自身；整链全空返回 null）。
     * 用于解析组织"有效集团"——sys_dept.group_id 历史多未回填，子部门多为 NULL，须继承最近祖先的集团归属。
     */
    @Select("WITH RECURSIVE anc AS (" +
            "  SELECT id, parent_id, group_id, 0 AS depth FROM sys_dept WHERE id = #{deptId} " +
            "  UNION ALL " +
            "  SELECT d.id, d.parent_id, d.group_id, a.depth + 1 FROM sys_dept d " +
            "    INNER JOIN anc a ON d.id = a.parent_id " +
            ") SELECT group_id FROM anc WHERE group_id IS NOT NULL ORDER BY depth ASC LIMIT 1")
    Long selectEffectiveGroupId(Long deptId);

    /** 子树内(不含自身)所有显式 group_id 集合，用于校验 include_children 展开的子树不跨集团 */
    @Select("WITH RECURSIVE dept_tree AS (" +
            "  SELECT id, parent_id, group_id FROM sys_dept WHERE id = #{deptId} AND status = 1 " +
            "  UNION ALL " +
            "  SELECT d.id, d.parent_id, d.group_id FROM sys_dept d INNER JOIN dept_tree t ON d.parent_id = t.id AND d.status = 1" +
            ") SELECT DISTINCT group_id FROM dept_tree WHERE id <> #{deptId} AND group_id IS NOT NULL")
    List<Long> selectDescendantGroupIds(Long deptId);
}
