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

    /**
     * 【A3 2026-10-21 取号行锁】同前缀取号的锁定读(FOR UPDATE): 锁定读读最新已提交值,
     * 不受本事务 REPEATABLE READ 旧快照影响(普通 SELECT 在同事务内重读仍是旧快照, 取号会
     * 复用已被并发事务提交的号码)。仅在 tenantId 非 null 时使用(调用方保证);
     * CAST+ORDER BY+LIMIT 1 保持与 {@link #selectMaxCodeByPrefix} 一致的数字序语义(纯字符串
     * DESC 在序号跨位数时如 999→1000 会倒序取错)。无匹配行返回 null(=该前缀首个号码)。
     * 调用方须在事务内使用, 且先持有同集团取号串行化锁(CustomerService 取号路径)。
     */
    @Select("SELECT CAST(SUBSTRING(customer_code, #{prefixLen} + 1) AS UNSIGNED) FROM biz_customer "
            + "WHERE tenant_id = #{tenantId} AND customer_code LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY CAST(SUBSTRING(customer_code, #{prefixLen} + 1) AS UNSIGNED) DESC LIMIT 1 FOR UPDATE")
    Integer selectMaxCodeByPrefixForUpdate(@Param("prefix") String prefix, @Param("prefixLen") int prefixLen, @Param("tenantId") Long tenantId);

    /**
     * 【审查 backend-3 2026-10-18】客户行悲观锁: 延期审批执行侧读改写 approved_extend_days
     * 必须在锁内完成, 否则两张在途延期单并发通过时互相覆盖(少加天数)。
     * 仅取未删除行; 调用方须在事务内使用。
     */
    @Select("SELECT * FROM biz_customer WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    BizCustomer selectByIdForUpdate(@Param("id") Long id);

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

    /**
     * 【审查 backend-4 2026-10-18】客户所属集团(租户)ID, 显式绕过租户拦截器:
     * 用于非 admin 财务代录的跨集团拒绝(被拦截器改写会恒空, 看不出"属于其他集团"只剩"不存在")。
     * 只返回 tenant_id, 不泄露客户资料; deleted=1 视为不存在。
     */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id FROM biz_customer WHERE id = #{id} AND deleted = 0")
    Long selectTenantIdById(@Param("id") Long id);

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
