package com.aigicrm.module.customer.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CustomerControllerTest {

    @Test
    void release_rejectsNonOwnerBeforeSeaMutation() {
        CustomerService customerService = mock(CustomerService.class);
        SeaService seaService = mock(SeaService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        BizCustomer customer = customer(11L, 200L);
        when(customerService.getById(11L)).thenReturn(customer);
        CustomerController controller = new CustomerController(customerService, seaService, mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService, mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            security.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class, () -> controller.release(11L, Map.of("reason", "放弃客户")));
        }
        verifyNoInteractions(seaService);
        verify(customerService, never()).removeAllShares(11L);
    }

    @Test
    void release_rejectsWhenOfficeHasNoEnabledPool() {
        CustomerService customerService = mock(CustomerService.class);
        SeaService seaService = mock(SeaService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        BizCustomer customer = customer(11L, 100L);
        customer.setOfficeId(7L);
        when(customerService.getById(11L)).thenReturn(customer);
        when(seaService.matchSeaConfig(7L)).thenReturn(null);
        CustomerController controller = new CustomerController(customerService, seaService, mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService, mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            security.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class, () -> controller.release(11L, Map.of()));
        }
        verify(seaService, never()).addToSea(anyLong(), anyString());
        verify(customerService, never()).removeAllShares(11L);
    }

    @Test
    void release_movesCustomerAndRemovesSharesAfterGuardsPass() {
        CustomerService customerService = mock(CustomerService.class);
        SeaService seaService = mock(SeaService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        BizCustomer customer = customer(11L, 100L);
        customer.setOfficeId(7L);
        when(customerService.getById(11L)).thenReturn(customer);
        when(seaService.matchSeaConfig(7L)).thenReturn(1L);
        CustomerController controller = new CustomerController(customerService, seaService, mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService, mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            security.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            controller.release(11L, Map.of("reason", "客户主动放弃"));
        }
        verify(seaService).addToSea(11L, "客户主动放弃");
        verify(customerService).removeAllShares(11L);
    }

    @Test
    void financeEndpoints_redactDataWithoutFinancePermission() {
        CustomerService customerService = mock(CustomerService.class);
        SeaService seaService = mock(SeaService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(customerService.getInvoices(11L)).thenReturn(List.of());
        when(customerService.getInvoiceSummary(11L)).thenReturn(Map.of("totalCount", 4, "totalAmount", 120));
        CustomerController controller = new CustomerController(customerService, seaService, mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService, mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("finance:view")).thenReturn(false);
            controller.getInvoices(11L);
            controller.getInvoiceSummary(11L);
        }
        verify(customerService, never()).getInvoices(11L);
        verify(customerService, never()).getInvoiceSummary(11L);
    }

    @Test
    void contactsEndpoint_doesNotQueryWithoutContactPermission() {
        CustomerService customerService = mock(CustomerService.class);
        CustomerController controller = new CustomerController(customerService, mock(SeaService.class),
                mock(com.aigicrm.common.idempotency.IdempotencyService.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("contact:view")).thenReturn(false);
            controller.getContacts(11L);
        }
        verify(customerService, never()).getContacts(11L);
    }

    @Test
    void groupNamesEndpoint_delegatesToService() {
        CustomerService customerService = mock(CustomerService.class);
        when(customerService.listGroupNames()).thenReturn(List.of("陕煤集团"));
        CustomerController controller = new CustomerController(customerService, mock(SeaService.class),
                mock(com.aigicrm.common.idempotency.IdempotencyService.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        assertEquals(List.of("陕煤集团"), controller.groupNames().getData());
        verify(customerService).listGroupNames();
    }

    private BizCustomer customer(Long id, Long ownerId) {
        BizCustomer customer = new BizCustomer();
        customer.setId(id);
        customer.setOwnerId(ownerId);
        return customer;
    }

    /**
     * 【L3 2026-10-08】userIds/officeIds CSV 解析:去重(officeIds 下游 in 条件此前不去重)
     * + 上限 200 截断(防构造巨型 IN 放大查询);脏值跳过/空串返回 null 的既有容错语义不变。
     */
    @Test
    void parseLongCsv_dedupesAndCapsAtTwoHundred() {
        assertEquals(List.of(1L, 2L), CustomerController.parseLongCsv("1,2,1"), "重复 id 必须去重");
        assertEquals(List.of(3L), CustomerController.parseLongCsv("3,x,"), "脏值跳过(既有容错)");
        assertEquals(null, CustomerController.parseLongCsv(null));
        assertEquals(null, CustomerController.parseLongCsv("x"));

        StringBuilder csv = new StringBuilder();
        for (int i = 1; i <= 250; i++) {
            if (i > 1) csv.append(',');
            csv.append(i);
        }
        List<Long> capped = CustomerController.parseLongCsv(csv.toString());
        assertEquals(200, capped.size(), "250 个 id 必须截断到 200");
        assertEquals(200L, capped.get(199), "截断保序取前 200");
    }

    // ===== 【E1 2026-10-21】阶段总额保存与发票端点统一 assertCanManageFinance =====
    // @PreAuthorize 本层不参与纯单测(无 Spring Security 过滤链), 断言落在曾实际拒绝
    // "仅持 finance:manage"用户的服务层对象校验上: gate 必须从 assertCanEdit 换成
    // assertCanManageFinance(CustomerAccessService: finance:manage 放行, 否则回退 assertCanEdit)。

    @Test
    void saveStageAmount_usesFinanceManageGate_notPlainEditGate() {
        CustomerService customerService = mock(CustomerService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        CustomerController controller = new CustomerController(customerService, mock(SeaService.class),
                mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        controller.saveStageAmount(11L, stageAmountRequest());

        // gate 换成 assertCanManageFinance: "仅持 finance:manage"用户(非负责人)由此放行保存
        verify(accessService).assertCanManageFinance(11L);
        verify(accessService, never()).assertCanEdit(anyLong());
        // gate 通过即落服务(200 路径)
        verify(customerService).saveStageAmount(eq(11L), any(com.aigicrm.module.customer.dto.StageAmountRequest.class));
    }

    @Test
    void saveStageAmount_rejectedBeforeServiceWhenFinanceGateFails() {
        CustomerService customerService = mock(CustomerService.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        doThrow(new BusinessException("无权编辑该客户"))
                .when(accessService).assertCanManageFinance(11L);
        CustomerController controller = new CustomerController(customerService, mock(SeaService.class),
                mock(com.aigicrm.common.idempotency.IdempotencyService.class), accessService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        assertThrows(BusinessException.class, () -> controller.saveStageAmount(11L, stageAmountRequest()));
        verify(customerService, never())
                .saveStageAmount(anyLong(), any(com.aigicrm.module.customer.dto.StageAmountRequest.class));
    }

    private com.aigicrm.module.customer.dto.StageAmountRequest stageAmountRequest() {
        return new com.aigicrm.module.customer.dto.StageAmountRequest(
                java.time.LocalDate.now().getYear(), "H1",
                new java.math.BigDecimal("12.5"), null);
    }
}
