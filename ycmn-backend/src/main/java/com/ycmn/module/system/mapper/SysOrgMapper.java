package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /** 递归查询所有下级组织ID */
    @Select("WITH RECURSIVE org_tree AS (" +
            "  SELECT id FROM sys_org WHERE id = #{orgId} AND status = 1 " +
            "  UNION ALL " +
            "  SELECT o.id FROM sys_org o INNER JOIN org_tree t ON o.parent_id = t.id AND o.status = 1" +
            ") SELECT id FROM org_tree")
    List<Long> selectDescendantIds(Long orgId);
}
