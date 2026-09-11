package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 按角色查启用用户 id（权限缓存 evictByRole 反查用——**不过滤角色状态**，停用角色的用户也要 evict）。
     *  ⚠️ 与 {@link #selectActiveUserIdsByRoleId} 语义刻意不同(本方法含停用角色用户), 勿合并为同一参数化查询——
     *  合并会破坏 evict 语义。 */
    @Select("SELECT u.id FROM sys_user u INNER JOIN sys_user_role ur ON ur.user_id = u.id " +
            "WHERE ur.role_id = #{roleId} AND u.status = 1 AND u.deleted = 0")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /** 【安全 2026-08-11 P1 关联修复】按角色查启用用户 id（审批 ROLE 审批人解析用），
     *  过滤 sys_role.status=1——停用角色不再产生审批人，避免"停用角色即失效"语义在审批链未闭环。
     *  ⚠️ 与 {@link #selectUserIdsByRoleId} 语义刻意不同(本方法仅启用角色用户), 勿合并。 */
    @Select("SELECT u.id FROM sys_user u INNER JOIN sys_user_role ur ON ur.user_id = u.id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id AND r.status = 1 " +
            "WHERE ur.role_id = #{roleId} AND u.status = 1 AND u.deleted = 0")
    List<Long> selectActiveUserIdsByRoleId(@Param("roleId") Long roleId);
}
