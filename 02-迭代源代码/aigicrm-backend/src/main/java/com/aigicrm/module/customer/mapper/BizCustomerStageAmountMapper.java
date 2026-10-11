package com.aigicrm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.customer.entity.BizCustomerStageAmount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BizCustomerStageAmountMapper extends BaseMapper<BizCustomerStageAmount> {

    /** 唯一键(customer_id, period_year, period_type)定位 + 行锁: saveStageAmount 的 upsert 前置读,
     *  并发下两个请求同时"不存在→insert"会被唯一键拦截其一, 行锁保证读到-写入串行化(沿用激励 remove-file 范式) */
    @Select("SELECT * FROM biz_customer_stage_amount WHERE customer_id = #{customerId} "
            + "AND period_year = #{year} AND period_type = #{type} AND deleted = 0 FOR UPDATE")
    BizCustomerStageAmount selectByPeriodForUpdate(@Param("customerId") Long customerId,
                                                   @Param("year") int year, @Param("type") String type);
}
