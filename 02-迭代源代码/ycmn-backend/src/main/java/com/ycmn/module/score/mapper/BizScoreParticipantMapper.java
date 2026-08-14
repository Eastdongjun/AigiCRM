package com.ycmn.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.score.entity.BizScoreParticipant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizScoreParticipantMapper extends BaseMapper<BizScoreParticipant> {

    @Delete("DELETE FROM biz_score_participant WHERE period = #{period}")
    int physicalDeleteByPeriod(@Param("period") String period);
}
