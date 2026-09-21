package com.aigicrm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizCustomerMapper extends BaseMapper<BizCustomer> {

    // 【G2 2026-09-21】取号显式租户条件(防跨集团编码语义泄漏/冲突; tenantId null=无集团上下文不额外限制, 与原行为一致)
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, 2) AS UNSIGNED)), 0) FROM biz_customer WHERE (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCode(@Param("tenantId") Long tenantId);

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, #{prefixLen} + 1) AS UNSIGNED)), 0) FROM biz_customer WHERE customer_code LIKE CONCAT(#{prefix}, '%') AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCodeByPrefix(@Param("prefix") String prefix, @Param("prefixLen") int prefixLen, @Param("tenantId") Long tenantId);
}
