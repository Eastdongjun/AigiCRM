package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, d.dept_name, " +
            "(SELECT d2.dept_name FROM sys_dept d2 WHERE d2.id = u.office_id) as office_name, " +
            "(SELECT r.role_name FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = u.id LIMIT 1) as role_name, " +
            "(SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = u.id LIMIT 1) as role_id " +
            "FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id " +
            "WHERE u.deleted = 0 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%')) " +
            "AND (#{deptIds} IS NULL OR #{deptIds} = '' OR FIND_IN_SET(u.dept_id, #{deptIds})) " +
            "AND (#{status} IS NULL OR u.status = #{status}) " +
            "ORDER BY u.created_at DESC")
    IPage<SysUser> selectUserPage(Page<SysUser> page, @Param("keyword") String keyword, @Param("deptIds") String deptIds, @Param("status") Integer status);
}
