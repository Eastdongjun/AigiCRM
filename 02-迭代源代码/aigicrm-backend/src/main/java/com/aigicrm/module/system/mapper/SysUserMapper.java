package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 【P2 2026-10-09 配额并发修复】条件扣减配额(原子):
     * WHERE storage_used + size <= storage_quota 命中才加, 影响行数 0 = 配额不足。
     * 并发上传在数据库端串行判定, 消除"两请求同过内存检查"的超配额竞态。参数全 #{} 绑定。
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE sys_user SET storage_used = storage_used + #{size} "
                    + "WHERE id = #{userId} AND storage_used + #{size} <= storage_quota")
    int tryConsumeQuota(@org.apache.ibatis.annotations.Param("userId") Long userId,
                        @org.apache.ibatis.annotations.Param("size") long size);

    /**
     * 【P2 2026-10-09 配额并发修复】数据库端原子增减(删除归还/替换差额/上传失败补偿):
     * GREATEST(0, ...) 守护不越负数界, 消除读改写丢失更新。参数全 #{} 绑定。
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE sys_user SET storage_used = GREATEST(0, storage_used + #{delta}) WHERE id = #{userId}")
    int adjustStorageUsed(@org.apache.ibatis.annotations.Param("userId") Long userId,
                          @org.apache.ibatis.annotations.Param("delta") long delta);


    @Select("SELECT u.*, d.dept_name, " +
            "(SELECT d2.dept_name FROM sys_dept d2 WHERE d2.id = u.office_id) as office_name, " +
            "(SELECT su.real_name FROM sys_user su WHERE su.id = u.manager_id) as manager_name, " +
            "(SELECT r.role_name FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = u.id LIMIT 1) as role_name, " +
            "(SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = u.id LIMIT 1) as role_id " +
            "FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id " +
            "WHERE u.deleted = 0 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%')) " +
            "AND (#{deptIds} IS NULL OR #{deptIds} = '' OR FIND_IN_SET(u.dept_id, #{deptIds})) " +
            "AND (#{status} IS NULL OR u.status = #{status}) " +
            // 【2026-08-05】人员编号从总负责人开始：销售总监(0)最前，其次系统管理员(admin)/行政(05)，再按账号升序
            "ORDER BY CASE (SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = u.id LIMIT 1) " +
            "WHEN '0' THEN 0 WHEN 'admin' THEN 1 WHEN '05' THEN 2 ELSE 3 END, u.username ASC")
    IPage<SysUser> selectUserPage(Page<SysUser> page, @Param("keyword") String keyword, @Param("deptIds") String deptIds, @Param("status") Integer status);
}
