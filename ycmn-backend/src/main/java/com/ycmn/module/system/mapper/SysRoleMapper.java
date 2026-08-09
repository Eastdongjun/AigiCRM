package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 按角色查启用用户 id（审批 ROLE 审批人解析用；JOIN 单查，避免全表扫描+N+1） */
    @Select("SELECT u.id FROM sys_user u INNER JOIN sys_user_role ur ON ur.user_id = u.id " +
            "WHERE ur.role_id = #{roleId} AND u.status = 1 AND u.deleted = 0")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
