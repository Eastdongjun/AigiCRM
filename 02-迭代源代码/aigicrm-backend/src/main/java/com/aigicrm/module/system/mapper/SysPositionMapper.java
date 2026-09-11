package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysPosition;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPositionMapper extends BaseMapper<SysPosition> {

    /** 查询用户的所有岗位 */
    @Select("SELECT p.* FROM sys_position p " +
            "INNER JOIN sys_user_position up ON p.id = up.position_id " +
            "WHERE up.user_id = #{userId} AND p.status = 1")
    List<SysPosition> selectByUserId(Long userId);
}
