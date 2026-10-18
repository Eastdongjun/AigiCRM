package com.aigicrm.module.approval.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.entity.BizApprovalConfig;
import com.aigicrm.module.approval.entity.BizApprovalNode;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
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
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【D1=A 2026-10-18】storage 审批契约:
 * - submit 类型白名单(未知类型 400), storage 忽略 customerId/客户端 oldQuota,
 *   从库取当前配额并规范化 extraData {quota,new oldQuota,reason} 落单;
 * - 同一申请人仅允许一张在途单;
 * - approve 执行 applyStorageQuota(锁内只升不降), 业务异常透传随事务回滚。
 */
class StorageApprovalServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApproval.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApprovalNode.class);
    }

    private final BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SysUserService userService = mock(SysUserService.class);

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(
                configMapper,
                approvalMapper,
                nodeMapper,
                mock(BizApprovalFlowMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class),
                mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                userService,
                mock(ApprovalConfigService.class),
                mock(SysConfigService.class),
                jdbc,
                mock(com.aigicrm.security.SecurityAuditService.class));
    }

    private SysUser currentUser(long quota) {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setStorageQuota(quota);
        return u;
    }

    private void stubSubmitFlow() {
        when(userService.validateStorageApplyReason("扩容")).thenReturn("扩容");
        when(userService.validateStorageQuotaRequest(eq(7L), any())).thenReturn(currentUser(100L));
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);
        when(configMapper.selectCount(any())).thenReturn(1L);
        BizApprovalConfig cfg = new BizApprovalConfig();
        cfg.setId(10L);
        cfg.setFlowName("存储空间申请");
        when(configMapper.selectOne(any())).thenReturn(cfg);
        ApprovalConfigService configService = mock(ApprovalConfigService.class);
        when(configService.createNodes(any(), anyString())).thenReturn(List.of(7L));
        // 重新构造以注入返回审批人的配置服务
        service = new ApprovalService(
                configMapper,
                approvalMapper,
                nodeMapper,
                mock(BizApprovalFlowMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class),
                mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                userService,
                configService,
                mock(SysConfigService.class),
                jdbc,
                mock(com.aigicrm.security.SecurityAuditService.class));
    }

    @Test
    void submit_storage_normalizesExtraData_ignoresClientOldQuotaAndCustomerId() {
        stubSubmitFlow();
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            service.submit("storage", 42L, null,
                    "{\"quota\":200,\"oldQuota\":1,\"reason\":\"扩容\"}");
        }

        ArgumentCaptor<BizApproval> captor = ArgumentCaptor.forClass(BizApproval.class);
        verify(approvalMapper).insert(captor.capture());
        BizApproval inserted = captor.getValue();
        assertEquals("storage", inserted.getApprovalType());
        assertEquals("{\"quota\":200,\"oldQuota\":100,\"reason\":\"扩容\"}", inserted.getExtraData(),
                "客户端 oldQuota 必须被忽略, 以库内当前配额快照");
        assertNull(inserted.getCustomerId(), "storage 单不关联客户(客户端传值忽略)");
        verify(jdbc).queryForList(anyString(), eq(Long.class), eq(7L));
    }

    @Test
    void submit_unknownType_rejectedWith400() {
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit("unknown-type", null, null, "{}"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("不支持的审批类型"));
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void submit_nullType_rejectedWith400NotNpe() {
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(null, null, null, "{}"));
            assertEquals(400, ex.getCode());
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void submit_storage_existingActiveApplication_rejected() {
        when(userService.validateStorageApplyReason("扩容")).thenReturn("扩容");
        when(userService.validateStorageQuotaRequest(eq(7L), any())).thenReturn(currentUser(100L));
        when(approvalMapper.selectCount(any())).thenReturn(1L);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit("storage", null, null, "{\"quota\":200,\"reason\":\"扩容\"}"));
            assertTrue(ex.getMessage().contains("进行中的存储空间申请"));
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void submit_storage_missingQuota_rejected() {
        when(userService.validateStorageApplyReason("扩容")).thenReturn("扩容");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit("storage", null, null, "{\"reason\":\"扩容\"}"));
            assertTrue(ex.getMessage().contains("缺少配额"));
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void submit_storage_decimalQuota_rejected() {
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit("storage", null, null, "{\"quota\":1.5,\"reason\":\"扩容\"}"));
            assertTrue(ex.getMessage().contains("整数"));
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void approve_storage_executesLockedQuotaUpdate_andDoesNotUseLegacyUpdate() {
        BizApproval approval = new BizApproval();
        approval.setId(20L);
        approval.setApprovalType("storage");
        approval.setApplicantId(8L);
        approval.setStatus("reviewing");
        approval.setExtraData("{\"quota\":200,\"oldQuota\":100,\"reason\":\"扩容\"}");
        when(approvalMapper.selectByIdForUpdate(20L)).thenReturn(approval);

        BizApprovalNode pending = new BizApprovalNode();
        pending.setId(700L);
        pending.setApprovalId(20L);
        pending.setApproverId(7L);
        pending.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(pending);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        BizApprovalNode approved = new BizApprovalNode();
        approved.setId(700L);
        approved.setApprovalId(20L);
        approved.setApproverId(7L);
        approved.setStatus("approved");
        when(nodeMapper.selectList(any())).thenReturn(List.of(approved));
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            service.approve(20L, "同意");
        }

        verify(userService).applyStorageQuota(8L, 200L, 20L);
        verify(userService, never()).updateStorageQuota(any(), any());
    }

    @Test
    void approve_storage_concurrencyRejection_propagates_andRollsBackApproval() {
        BizApproval approval = new BizApproval();
        approval.setId(21L);
        approval.setApprovalType("storage");
        approval.setApplicantId(8L);
        approval.setStatus("reviewing");
        approval.setExtraData("{\"quota\":200,\"oldQuota\":100,\"reason\":\"扩容\"}");
        when(approvalMapper.selectByIdForUpdate(21L)).thenReturn(approval);

        BizApprovalNode pending = new BizApprovalNode();
        pending.setId(701L);
        pending.setApprovalId(21L);
        pending.setApproverId(7L);
        pending.setStatus("pending");
        when(nodeMapper.selectOne(any())).thenReturn(pending);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        BizApprovalNode approved = new BizApprovalNode();
        approved.setId(701L);
        approved.setApprovalId(21L);
        approved.setApproverId(7L);
        approved.setStatus("approved");
        when(nodeMapper.selectList(any())).thenReturn(List.of(approved));
        when(approvalMapper.update(isNull(), any())).thenReturn(1);
        doThrow(new BusinessException("存储配额调整失败：申请额度已不高于当前配额"))
                .when(userService).applyStorageQuota(8L, 200L, 21L);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.approve(21L, "同意"));
            assertTrue(ex.getMessage().contains("不高于当前配额"), "业务异常必须原样透传: " + ex.getMessage());
        }
    }
}
