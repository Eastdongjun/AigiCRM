package com.aigicrm.module.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.common.entity.BizFormDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** 【议题四】表单草稿 mapper。 */
@Mapper
public interface BizFormDraftMapper extends BaseMapper<BizFormDraft> {

    @Delete("DELETE FROM biz_form_draft WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM biz_form_draft WHERE updated_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int purgeExpired(@Param("days") int days);
}
