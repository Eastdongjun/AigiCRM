package com.aigicrm.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.score.entity.BizScoreParticipant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizScoreParticipantMapper extends BaseMapper<BizScoreParticipant> {

    // 【G2 2026-09-21】显式租户条件, 同 BizScoreEntryMapper
    @Delete("DELETE FROM biz_score_participant WHERE period = #{period} AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int physicalDeleteByPeriod(@Param("period") String period, @Param("tenantId") Long tenantId);
}
