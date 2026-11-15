package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.service.RecycleService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 【产品口径 2026-11-15】回收站恢复/清空仅系统管理员。 */
class RecycleControllerAdminOnlyTest {

    private final RecycleService service = mock(RecycleService.class);
    private final RecycleController controller = new RecycleController(service);

    @Test
    void nonAdminCannotRestoreFile() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> controller.restoreFile(1L));
        }
        verifyNoInteractions(service);
    }

    @Test
    void nonAdminCannotPurgeFile() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> controller.hardDeleteFile(1L));
        }
        verifyNoInteractions(service);
    }

    @Test
    void nonAdminCannotPurgeAllFiles() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> controller.purgeFiles());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminCanRestoreFile() {
        when(service.restoreFile(1L)).thenReturn("ok");
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            assertEquals(200, controller.restoreFile(1L).getCode());
        }
        verify(service).restoreFile(1L);
    }
}
