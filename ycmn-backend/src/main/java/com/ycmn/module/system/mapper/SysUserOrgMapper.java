package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysUserOrg;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-组织关联 Mapper（用户级 custom 数据权限，2026-08-08）
 */
@Mapper
public interface SysUserOrgMapper extends BaseMapper<SysUserOrg> {

    /** 根据用户ID查询关联的组织列表 */
    @Select("SELECT * FROM sys_user_org WHERE user_id = #{userId} ORDER BY id")
    List<SysUserOrg> selectByUserId(@Param("userId") Long userId);

    /** 根据用户ID删除所有关联 */
    @Delete("DELETE FROM sys_user_org WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
