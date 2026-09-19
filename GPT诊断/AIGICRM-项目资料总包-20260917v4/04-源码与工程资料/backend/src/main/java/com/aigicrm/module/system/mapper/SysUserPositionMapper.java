package com.aigicrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.system.entity.SysUserPosition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserPositionMapper extends BaseMapper<SysUserPosition> {

    /** 按岗位查关联用户 id（权限缓存岗位级失效反查用） */
    @Select("SELECT user_id FROM sys_user_position WHERE position_id = #{positionId}")
    List<Long> selectUserIdsByPositionId(@Param("positionId") Long positionId);
}
