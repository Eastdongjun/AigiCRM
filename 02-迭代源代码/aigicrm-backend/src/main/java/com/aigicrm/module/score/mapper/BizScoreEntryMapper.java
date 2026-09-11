package com.aigicrm.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.score.entity.BizScoreEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizScoreEntryMapper extends BaseMapper<BizScoreEntry> {

    @Delete("DELETE FROM biz_score_entry WHERE period = #{period}")
    int physicalDeleteByPeriod(@Param("period") String period);
}
