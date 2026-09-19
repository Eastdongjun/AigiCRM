package com.aigicrm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.customer.entity.BizPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BizPaymentMapper extends BaseMapper<BizPayment> {

    @Select("SELECT IFNULL(SUM(payment_amount), 0) FROM biz_payment WHERE customer_id = #{customerId} AND deleted = 0")
    BigDecimal sumAmountByCustomerId(@Param("customerId") Long customerId);

    @Select("SELECT COUNT(*) FROM biz_payment WHERE customer_id = #{customerId} AND deleted = 0")
    int countByCustomerId(@Param("customerId") Long customerId);
}
