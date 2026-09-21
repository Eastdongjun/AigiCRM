package com.aigicrm.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aigicrm.module.approval.entity.BizApproval;
import org.apache.ibatis.annotations.Select;

public interface BizApprovalMapper extends BaseMapper<BizApproval> {

    // 【G2 2026-09-21】取号显式租户条件(同 BizCustomerMapper; tenantId null=无集团上下文不额外限制)
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(approval_code, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) FROM biz_approval WHERE approval_code LIKE CONCAT(#{prefix}, '%') AND (#{tenantId} IS NULL OR tenant_id = #{tenantId})")
    int selectMaxCodeByPrefix(@org.apache.ibatis.annotations.Param("prefix") String prefix, @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

    /**
     * 【PR-22 活体发现 2026-09-19】审批主行悲观锁:approve/reject 入口先锁主行,
     * 串行化同一审批单上的并发操作——否则双人各自先锁自己的节点行、再互相取消对方节点行,
     * 构成 AB-BA 死锁(MySQL 杀一回滚,败者收到 500 而非优雅拒绝)。
     */
    @Select("SELECT * FROM biz_approval WHERE id = #{id} FOR UPDATE")
    BizApproval selectByIdForUpdate(Long id);
}
