package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysPermissionRule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionRuleMapper extends BaseMapper<SysPermissionRule> {

    /** 查岗位关联的规则 */
    @Select("SELECT r.* FROM sys_permission_rule r " +
            "INNER JOIN sys_position_rule pr ON r.id = pr.rule_id " +
            "WHERE pr.position_id = #{positionId} AND r.status = 1 " +
            "ORDER BY r.priority")
    List<SysPermissionRule> selectByPositionId(@Param("positionId") Long positionId);

    /** 查角色关联的规则 */
    @Select("SELECT r.* FROM sys_permission_rule r " +
            "INNER JOIN sys_role_rule rr ON r.id = rr.rule_id " +
            "WHERE rr.role_id = #{roleId} AND r.status = 1 " +
            "ORDER BY r.priority")
    List<SysPermissionRule> selectByRoleId(@Param("roleId") Long roleId);

    /** 查用户所有岗位关联的规则（去重） */
    @Select("SELECT DISTINCT r.* FROM sys_permission_rule r " +
            "INNER JOIN sys_position_rule pr ON r.id = pr.rule_id " +
            "INNER JOIN sys_user_position up ON pr.position_id = up.position_id " +
            "WHERE up.user_id = #{userId} AND r.status = 1 " +
            "ORDER BY r.priority")
    List<SysPermissionRule> selectByUserId(@Param("userId") Long userId);
}
