package com.aigicrm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.customer.entity.BizInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BizInvoiceMapper extends BaseMapper<BizInvoice> {

    /** 【PR-18 统计口径】聚合排除已作废(voided)发票——作废记录仅保留台账痕迹, 不计入金额/笔数 */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM biz_invoice WHERE customer_id = #{customerId} AND deleted = 0 AND status <> 'voided'")
    BigDecimal sumAmountByCustomerId(@Param("customerId") Long customerId);

    @Select("SELECT COUNT(*) FROM biz_invoice WHERE customer_id = #{customerId} AND deleted = 0 AND status <> 'voided'")
    int countByCustomerId(@Param("customerId") Long customerId);
}
