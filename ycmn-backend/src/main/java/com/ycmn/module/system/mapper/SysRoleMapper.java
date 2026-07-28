package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
