package com.aigicrm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface BizCustomerMapper extends BaseMapper<BizCustomer> {

    // 【G2 2026-09-21】取号显式租户条件(防跨集团编码语义泄漏/冲突; tenantId null=无集团上下文不额外限制, 与原行为一致)
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, 2) AS UNSIGNED)), 0) FROM biz_customer WHERE (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCode(@Param("tenantId") Long tenantId);

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(customer_code, #{prefixLen} + 1) AS UNSIGNED)), 0) FROM biz_customer WHERE customer_code LIKE CONCAT(#{prefix}, '%') AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCodeByPrefix(@Param("prefix") String prefix, @Param("prefixLen") int prefixLen, @Param("tenantId") Long tenantId);

    // ===== 【回收站 2026-10-18】已删除客户(显式 deleted=1 绕过 @TableLogic; 租户插件仍按会话集团过滤, admin 忽略) =====

    /** 分页查已删除客户(pattern 为已拼好 %kw%，空搜索传 '%%') */
    @Select("SELECT * FROM biz_customer WHERE deleted = 1 "
            + "AND (customer_name LIKE #{pattern} OR customer_code LIKE #{pattern}) "
            + "ORDER BY updated_at DESC LIMIT #{offset}, #{size}")
    List<BizCustomer> selectDeletedPage(@Param("pattern") String pattern,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    @Select("SELECT COUNT(*) FROM biz_customer WHERE deleted = 1 "
            + "AND (customer_name LIKE #{pattern} OR customer_code LIKE #{pattern})")
    long countDeleted(@Param("pattern") String pattern);

    /** 含已删除行按 id 取(selectById 被 @TableLogic 过滤, 恢复流程需读原行) */
    @Select("SELECT * FROM biz_customer WHERE id = #{id}")
    BizCustomer selectByIdIncludingDeleted(@Param("id") Long id);

    /** 同名活跃客户数: >0 时恢复会造成重复卡(290 条里 158 条重复即此形态) */
    @Select("SELECT COUNT(*) FROM biz_customer WHERE deleted = 0 AND customer_name = #{name}")
    long countActiveByName(@Param("name") String name);

    /** 未结束的公海池残留: >0 时恢复会形成"私海+池内"双归属 */
    @Select("SELECT COUNT(*) FROM biz_sea_pool WHERE customer_id = #{customerId} "
            + "AND status IN ('available', 'pending_approval')")
    long countActivePoolRows(@Param("customerId") Long customerId);

    /** 恢复为名下客户(status 回 normal; 只更新 deleted=1 的行, 并发下重复恢复返回 0) */
    @Update("UPDATE biz_customer SET deleted = 0, status = 'normal' WHERE id = #{id} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id);
}
