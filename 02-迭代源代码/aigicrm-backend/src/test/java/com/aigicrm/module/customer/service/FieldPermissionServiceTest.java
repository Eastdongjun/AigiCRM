package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

/** 字段码行为：有码放行，无码 400 + FIELD_PERM_REQUIRED。 */
class FieldPermissionServiceTest {

    @Test
    void registerDate_withCode_passes() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(true);
            assertDoesNotThrow(FieldPermissionService::requireRegisterDate);
        }
    }

    @Test
    void registerDate_withoutCode_400WithErrorCode() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, FieldPermissionService::requireRegisterDate);
            assertEquals(400, ex.getCode());
            assertEquals("FIELD_PERM_REQUIRED", ex.getErrorCode());
        }
    }

    @Test
    void invoiceDate_withoutCode_400WithErrorCode() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(() -> SecurityUtils.hasAuthority("customer:invoice-date:edit")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, FieldPermissionService::requireInvoiceDate);
            assertEquals(400, ex.getCode());
            assertEquals("FIELD_PERM_REQUIRED", ex.getErrorCode());
        }
    }
}
