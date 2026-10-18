package com.aigicrm.module.customer.listener;

import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 【D3 2026-10-18】导入缺失注册日: 成功落库 + 行级提示(不阻断)。 */
class CustomerImportListenerTest {

    @Test
    void missingRegisterDate_importSucceedsWithWarning() {
        CustomerService service = mock(CustomerService.class);
        when(service.existsByName("无日期客户")).thenReturn(false);
        doNothing().when(service).create(any(BizCustomer.class));

        CustomerImportDTO dto = new CustomerImportDTO();
        dto.setCustomerName("无日期客户");
        dto.setRegisterDate(null);

        CustomerImportResult result = new CustomerImportResult();
        CustomerImportListener listener = new CustomerImportListener(service, 7L, 3L, result);
        listener.invoke(dto, null);

        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(service).create(captor.capture());
        assertEquals(null, captor.getValue().getRegisterDate(), "监听器不自行改日期，由 create 统一兜底");
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).getReason().contains(LocalDate.now().toString()));
        assertTrue(result.getWarnings().get(0).getReason().contains("兜底"));
    }

    @Test
    void providedRegisterDate_hasNoWarning() {
        CustomerService service = mock(CustomerService.class);
        when(service.existsByName("有日期客户")).thenReturn(false);
        doNothing().when(service).create(any(BizCustomer.class));

        CustomerImportDTO dto = new CustomerImportDTO();
        dto.setCustomerName("有日期客户");
        dto.setRegisterDate(LocalDate.of(2026, 1, 15));

        CustomerImportResult result = new CustomerImportResult();
        new CustomerImportListener(service, 7L, 3L, result).invoke(dto, null);

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getWarnings().isEmpty());
    }
}
