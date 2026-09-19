package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysOrgAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysOrgAdminMapper extends BaseMapper<SysOrgAdmin> {

    /** 查询用户管理的组织ID列表（含递归下级） */
    @Select("WITH RECURSIVE org_tree AS (" +
            "  SELECT d.id FROM sys_dept d " +
            "  JOIN sys_org_admin a ON d.id = a.org_id " +
            "  WHERE a.user_id = #{userId} AND d.status = 1 " +
            "  UNION ALL " +
            "  SELECT d2.id FROM sys_dept d2 INNER JOIN org_tree t ON d2.parent_id = t.id AND d2.status = 1" +
            ") SELECT id FROM org_tree")
    List<Long> selectManagedOrgIds(Long userId);
}
