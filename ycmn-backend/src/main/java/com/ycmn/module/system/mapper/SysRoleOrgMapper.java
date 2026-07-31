package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysRoleOrg;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-组织关联 Mapper
 */
@Mapper
public interface SysRoleOrgMapper extends BaseMapper<SysRoleOrg> {

    /** 根据角色ID查询关联的组织列表 */
    @Select("SELECT * FROM sys_role_org WHERE role_id = #{roleId}")
    List<SysRoleOrg> selectByRoleId(@Param("roleId") Long roleId);

    /** 根据角色ID删除所有关联 */
    @Delete("DELETE FROM sys_role_org WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 查询用户所有角色关联的组织ID列表（含 include_children 标记） */
    @Select("SELECT DISTINCT ro.* FROM sys_role_org ro " +
            "INNER JOIN sys_user_role ur ON ro.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysRoleOrg> selectByUserId(@Param("userId") Long userId);
}
