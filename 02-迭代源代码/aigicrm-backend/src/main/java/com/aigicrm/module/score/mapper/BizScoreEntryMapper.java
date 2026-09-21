package com.aigicrm.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.score.entity.BizScoreEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizScoreEntryMapper extends BaseMapper<BizScoreEntry> {

    // 【G2 2026-09-21】显式租户条件: 修复按 period 物理删全集团数据的越权(插件关闭时拦截器不兜底)
    @Delete("DELETE FROM biz_score_entry WHERE period = #{period} AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int physicalDeleteByPeriod(@Param("period") String period, @Param("tenantId") Long tenantId);
}
