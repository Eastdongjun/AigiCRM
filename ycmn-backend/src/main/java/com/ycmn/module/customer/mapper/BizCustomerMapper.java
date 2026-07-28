package com.ycmn.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.customer.entity.BizCustomer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizCustomerMapper extends BaseMapper<BizCustomer> {

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, 2) AS UNSIGNED)), 0) FROM biz_customer")
    int selectMaxCode();

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, #{prefixLen} + 1) AS UNSIGNED)), 0) FROM biz_customer WHERE customer_code LIKE CONCAT(#{prefix}, '%')")
    int selectMaxCodeByPrefix(@Param("prefix") String prefix, @Param("prefixLen") int prefixLen);
}
