package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysOrgAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysOrgAdminMapper extends BaseMapper<SysOrgAdmin> {

    /** 查询用户管理的组织ID列表（含递归下级） */
    @Select("WITH RECURSIVE org_tree AS (" +
            "  SELECT o.id FROM sys_org o " +
            "  JOIN sys_org_admin a ON o.id = a.org_id " +
            "  WHERE a.user_id = #{userId} AND o.status = 1 " +
            "  UNION ALL " +
            "  SELECT o2.id FROM sys_org o2 INNER JOIN org_tree t ON o2.parent_id = t.id AND o2.status = 1" +
            ") SELECT id FROM org_tree")
    List<Long> selectManagedOrgIds(Long userId);
}
