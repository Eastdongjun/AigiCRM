package com.aigicrm.module.approval.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.entity.BizApprovalNode;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【审查 backend-3 2026-10-18】延期审批并发/重复守卫：
 * 1. 同一客户已有 reviewing/pending 延期单时再次提交 → 拒绝（并发提交先锁客户行）；
 * 2. 审批执行侧对客户行 SELECT ... FOR UPDATE 后再累加 approved_extend_days。
 */
class ExtendApprovalConcurrencyTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApproval.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApprovalNode.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizCustomer.class);
    }

    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
    private final BizApprovalFlowMapper flowMapper = mock(BizApprovalFlowMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CommerceStubs commerce = new CommerceStubs();

    /** 构造依赖较多, 抽出避免用例重复 */
    private ApprovalService service() {
        return new ApprovalService(
                configMapper, approvalMapper, nodeMapper, flowMapper, customerMapper,
                userMapper, mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class), mock(OrgScopeResolver.class),
                mock(SeaConfigService.class), commerce.accessService, mock(SysUserService.class),
                mock(ApprovalConfigService.class), mock(SysConfigService.class), jdbc,
                mock(SecurityAuditService.class));
    }

    private static final class CommerceStubs {
        final CustomerAccessService accessService = mock(CustomerAccessService.class);
    }

    private void login(long userId) {
        SecurityUser u = new SecurityUser();
        u.setUserId(userId);
        u.setRoleCodes(List.of("06"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null));
    }

    private static BizCustomer normalCustomer(long id) {
        BizCustomer c = new BizCustomer();
        c.setId(id);
        c.setStatus("normal");
        c.setOwnerId(7L);
        return c;
    }

    @Test
    void submitExtend_existingActiveExtend_rejectedAndNoInsert() {
        login(7L);
        when(customerMapper.selectById(501L)).thenReturn(normalCustomer(501L));
        when(commerce.accessService.canView(501L)).thenReturn(true);
        when(approvalMapper.selectCount(any())).thenReturn(1L);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service().submit("extend", 501L, null, null));
            assertTrue(ex.getMessage().contains("进行中的保护期延期审批"), "实际: " + ex.getMessage());
            verify(approvalMapper, never()).insert(any(BizApproval.class));
        }

        // 并发提交串行化: 先锁客户行, 再数在途单
        InOrder order = inOrder(jdbc, approvalMapper);
        order.verify(jdbc).queryForList(contains("FOR UPDATE"), eq(Long.class), eq(501L));
        order.verify(approvalMapper).selectCount(any());
    }

    @Test
    void approveExtend_locksCustomerBeforeAccumulating() {
        login(7L);
        BizApproval approval = new BizApproval();
        approval.setId(71L);
        approval.setApprovalType("extend");
        approval.setCustomerId(501L);
        approval.setApplicantId(8L);
        approval.setStatus("reviewing");
        approval.setExtraData("{\"extendDays\":360}");
        when(approvalMapper.selectByIdForUpdate(71L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);
        when(approvalMapper.selectById(71L)).thenReturn(approval);

        BizApprovalNode mine = new BizApprovalNode();
        mine.setId(801L);
        mine.setApprovalId(71L);
        mine.setApproverId(7L);
        mine.setNodeOrder(1);
        mine.setStatus("approved");
        when(nodeMapper.selectOne(any())).thenReturn(mine);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of(mine));

        BizCustomer customer = normalCustomer(501L);
        customer.setRegisterDate(LocalDate.of(2026, 1, 1));
        customer.setApprovedExtendDays(100); // 已有累计延期
        when(customerMapper.selectByIdForUpdate(501L)).thenReturn(customer);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            service().approve(71L, "同意");
        }

        InOrder order = inOrder(customerMapper);
        order.verify(customerMapper).selectByIdForUpdate(501L);
        ArgumentCaptor<BizCustomer> cap = ArgumentCaptor.forClass(BizCustomer.class);
        order.verify(customerMapper).updateById(cap.capture());
        assertEquals(460, cap.getValue().getApprovedExtendDays(),
                "锁内读改写: 100 + 本次 360, 不得丢失既有累计");
    }
}
