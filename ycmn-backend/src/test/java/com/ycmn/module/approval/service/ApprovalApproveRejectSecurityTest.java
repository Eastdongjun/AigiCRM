package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.entity.BizApprovalNode;
import com.ycmn.module.approval.mapper.BizApprovalConfigMapper;
import com.ycmn.module.approval.mapper.BizApprovalFlowMapper;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import com.ycmn.module.system.service.SysUserService;
import com.ycmn.security.SecurityUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【安全回归 H4】审批 approve/reject 权限守卫：
 * 只有该审批节点的审批人(approverId=当前用户 且 节点 pending)才能通过/驳回，
 * 非审批人调用必须抛"无权审批"——防止越权审批他人事项。
 */
class ApprovalApproveRejectSecurityTest {

    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final ApprovalService svc = new ApprovalService(
            mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
            mock(BizApprovalFlowMapper.class), mock(BizCustomerMapper.class),
            mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
            mock(BizSeaPoolMapper.class), mock(SeaService.class),
            mock(com.ycmn.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
            mock(SysUserService.class));

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
        // nodeMapper.selectOne 返回 null（未打桩）→ 无权审批
        assertThrows(BusinessException.class, () -> svc.approve(1L, "同意"));
    }

    @Test
    void reject_nonAssignedApprover_rejects() {
        setCurrentUser(2L);
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        assertThrows(BusinessException.class, () -> svc.reject(1L, "驳回"));
    }

    @Test
    void approve_missingApproval_rejects() {
        setCurrentUser(1L);
        when(approvalMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.approve(1L, "同意"));
    }

    @Test
    void approve_assignedApprover_succeeds() {
        setCurrentUser(1L);
        when(approvalMapper.selectById(1L)).thenReturn(approval());
        BizApprovalNode node = new BizApprovalNode();
        node.setId(5L);
        node.setApprovalId(1L);
        node.setApproverId(1L);
        node.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(nodeMapper.selectCount(any())).thenReturn(1L); // 仍有其他节点 pending，不触发最终提交

        assertDoesNotThrow(() -> svc.approve(1L, "同意"));
        assertEquals("approved", node.getStatus());
        verify(nodeMapper).updateById(node);
    }
}
