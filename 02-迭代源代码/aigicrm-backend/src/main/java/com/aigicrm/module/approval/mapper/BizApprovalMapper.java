package com.aigicrm.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.approval.entity.BizApproval;
import org.apache.ibatis.annotations.Select;

public interface BizApprovalMapper extends BaseMapper<BizApproval> {

    // 【G2 2026-09-21】取号显式租户条件(同 BizCustomerMapper; tenantId null=无集团上下文不额外限制)
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(approval_code, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) FROM biz_approval WHERE approval_code LIKE CONCAT(#{prefix}, '%') AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCodeByPrefix(@org.apache.ibatis.annotations.Param("prefix") String prefix, @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

    /**
     * 【A3 2026-10-21 取号行锁】同前缀取号的锁定读(FOR UPDATE): 锁定读读最新已提交值,
     * 不受本事务 REPEATABLE READ 旧快照影响(普通 SELECT 在同事务内重读仍是旧快照, 取号会
     * 复用已被并发事务提交的号码)。仅在 tenantId 非 null 时使用(调用方保证);
     * 与 {@link #selectMaxCodeByPrefix} 同数字序语义(CAST+ORDER BY+LIMIT 1, 纯字符串 DESC
     * 在序号跨位数时如 999→1000 会倒序取错), 无匹配行返回 null。
     * 调用方须在事务内使用, 且先持有同集团取号串行化锁(ApprovalService 取号路径)。
     */
    @Select("SELECT CAST(SUBSTRING(approval_code, LENGTH(#{prefix}) + 1) AS UNSIGNED) FROM biz_approval "
            + "WHERE tenant_id = #{tenantId} AND approval_code LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY CAST(SUBSTRING(approval_code, LENGTH(#{prefix}) + 1) AS UNSIGNED) DESC LIMIT 1 FOR UPDATE")
    Integer selectMaxCodeByPrefixForUpdate(@org.apache.ibatis.annotations.Param("prefix") String prefix,
                                           @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

    /**
     * 【PR-22 活体发现 2026-09-19】审批主行悲观锁:approve/reject 入口先锁主行,
     * 串行化同一审批单上的并发操作——否则双人各自先锁自己的节点行、再互相取消对方节点行,
     * 构成 AB-BA 死锁(MySQL 杀一回滚,败者收到 500 而非优雅拒绝)。
     */
    @Select("SELECT * FROM biz_approval WHERE id = #{id} FOR UPDATE")
    BizApproval selectByIdForUpdate(Long id);
}
