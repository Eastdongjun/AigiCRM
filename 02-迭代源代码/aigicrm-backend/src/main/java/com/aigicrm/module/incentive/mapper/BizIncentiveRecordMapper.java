package com.aigicrm.module.incentive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import org.apache.ibatis.annotations.Select;

public interface BizIncentiveRecordMapper extends BaseMapper<BizIncentiveRecord> {

    /**
     * 行锁读（SELECT ... FOR UPDATE）：removeFile 的"五槽合计≥1"守卫必须是锁定读，
     * 否则并发两事务各删不同槽的最后一份文件可双双通过守卫（工作流复检 2026-10-10 证实）。
     * 租户条件由 TenantLineInnerInterceptor 对 @Select 同样生效，保持租户隔离。
     */
    @Select("SELECT * FROM biz_incentive_record WHERE id = #{id} FOR UPDATE")
    BizIncentiveRecord selectByIdForUpdate(Long id);
}
