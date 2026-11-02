package com.aigicrm.module.approval.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.entity.BizApprovalNode;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.security.SecurityUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 【安全回归 H4】审批 approve/reject 权限守卫：
 * 只有该审批节点的审批人(approverId=当前用户 且 节点 pending)才能通过/驳回，
 * 非审批人调用必须被拒——防止越权审批他人事项。
 * 【2026-08-14 404 防探测】不存在/已结束/无审批节点统一抛"资源不存在或无权访问", 消除存在性 oracle。
 */
class ApprovalApproveRejectSecurityTest {

    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final ApprovalService svc = new ApprovalService(
            mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
            mock(BizApprovalFlowMapper.class), mock(BizCustomerMapper.class),
            mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
            mock(BizSeaPoolMapper.class), mock(SeaService.class),
            mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
            mock(SysUserService.class),
            mock(com.aigicrm.module.customer.service.CustomerService.class),
            mock(com.aigicrm.module.customer.service.CustomerEventService.class),
            mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApproval.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApprovalNode.class);
    }

    private void setCurrentUser(Long userId) {
        SecurityUser u = new SecurityUser();
        u.setUserId(userId);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(u);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private BizApproval approval() {
        BizApproval a = new BizApproval();
        a.setId(1L);
        a.setStatus("pending");
        return a;
    }

    @Test
    void approve_nonAssignedApprover_rejects() {
        setCurrentUser(2L); // 审批人应为 1，当前是 2
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(approval());
        // nodeMapper.selectOne 返回 null（未打桩）→ 拒绝(统一文案)
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(1L, "同意"));
        assertEquals("资源不存在或无权访问", ex.getMessage(),
                "非审批人/无节点必须与「不存在」统一文案, 防审批存在性探测");
    }

    @Test
    void reject_nonAssignedApprover_rejects() {
        setCurrentUser(2L);
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(approval());
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.reject(1L, "驳回"));
        assertEquals("资源不存在或无权访问", ex.getMessage());
    }

    @Test
    void approve_missingApproval_rejects() {
        setCurrentUser(1L);
        when(approvalMapper.selectById(1L)).thenReturn(null);
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(1L, "同意"));
        assertEquals("资源不存在或无权访问", ex.getMessage());
    }

    @Test
    void approve_assignedApprover_succeeds() {
        setCurrentUser(1L);
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(approval());
        BizApprovalNode node = new BizApprovalNode();
        node.setId(5L);
        node.setApprovalId(1L);
        node.setApproverId(1L);
        node.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(node);
        // 【PR-02 2026-09-19】条件流转契约:pending→approved 条件更新 affected=1
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        // 库内流转后的节点快照(无 configId=存量口径):本单仅此节点,流转后无 pending
        BizApprovalNode postDb = new BizApprovalNode();
        postDb.setId(5L);
        postDb.setApprovalId(1L);
        postDb.setApproverId(1L);
        postDb.setStatus("approved");
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(postDb));

        assertDoesNotThrow(() -> svc.approve(1L, "同意"));
        // 审批动作经条件更新落库(pending→approved),恰好一次
        verify(nodeMapper, times(1)).update(isNull(), any());
    }

    /**
     * 【P0 审批状态机】审批已被驳回后，其他审批人不得再通过（驳回后复活漏洞）。
     * 守卫应拒绝操作且不改动任何节点状态。
     */
    @Test
    void approve_afterRejected_rejects_notRevive() {
        setCurrentUser(2L);
        BizApproval a = approval();
        a.setStatus("rejected"); // 已被另一审批人驳回
        when(approvalMapper.selectById(1L)).thenReturn(a);
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(a);
        BizApprovalNode node = new BizApprovalNode();
        node.setId(6L);
        node.setApprovalId(1L);
        node.setApproverId(2L);
        node.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(nodeMapper.selectCount(any())).thenReturn(1L); // 避免走"全部通过"分支，验证的是终态守卫本身

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(1L, "同意"));
        assertEquals("资源不存在或无权访问", ex.getMessage(), "终态审批(已驳回)必须与「不存在」统一文案");
        assertEquals("pending", node.getStatus());
    }

    /**
     * 【P0 审批状态机】审批已通过后不得再驳回（反向状态翻转）。
     */
    @Test
    void reject_afterApproved_rejects() {
        setCurrentUser(1L);
        BizApproval a = approval();
        a.setStatus("approved"); // 已全部通过
        when(approvalMapper.selectById(1L)).thenReturn(a);
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(a);
        BizApprovalNode node = new BizApprovalNode();
        node.setId(7L);
        node.setApprovalId(1L);
        node.setApproverId(1L);
        node.setStatus("pending"); // 迟到节点（终态守卫应拦截，不依赖节点状态）
        when(nodeMapper.selectOne(any())).thenReturn(node);

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.reject(1L, "驳回"));
        assertEquals("资源不存在或无权访问", ex.getMessage(), "终态审批(已通过)必须与「不存在」统一文案");
        assertEquals("pending", node.getStatus());
        assertEquals("approved", a.getStatus()); // 审批状态不得被翻回 rejected
    }

    /**
     * 【P0 审批状态机】驳回后级联作废其他待审批节点，防终态后残留 pending 脏数据。
     */
    @Test
    void reject_cascadesSiblingPendingNodesToCancelled() {
        setCurrentUser(1L);
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(approval());
        BizApprovalNode actingNode = new BizApprovalNode();
        actingNode.setId(7L);
        actingNode.setApprovalId(1L);
        actingNode.setApproverId(1L);
        actingNode.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(actingNode);
        // 【PR-02】驳回同样走条件流转(pending→rejected),affected=1
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        BizApprovalNode sibling = new BizApprovalNode();
        sibling.setId(8L);
        sibling.setApprovalId(1L);
        sibling.setApproverId(2L);
        sibling.setStatus("pending");
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(sibling));

        svc.reject(1L, "驳回");

        assertEquals("rejected", sibling.getStatus());
        assertEquals("cancelled", sibling.getAction());
        verify(nodeMapper).updateById(sibling);
    }
}
