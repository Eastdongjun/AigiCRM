package com.ycmn.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.system.entity.SysLegalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysLegalEntityMapper extends BaseMapper<SysLegalEntity> {

    /** 查集团下的所有法人公司 */
    @Select("SELECT * FROM sys_legal_entity WHERE group_id = #{groupId} AND status = 1 ORDER BY sort_order")
    List<SysLegalEntity> selectByGroupId(Long groupId);
}
