package com.aigicrm.task;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskControllerSecurityTest {

    private final CustomerExpireTask task = mock(CustomerExpireTask.class);
    private final TaskController controller = new TaskController(task);

    @Test
    void nonAdminCannotTriggerCustomerExpiryTask() {
        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException error = assertThrows(BusinessException.class, controller::checkExpire);

            assertEquals(403, error.getCode());
            assertEquals("仅系统管理员可触发该任务", error.getMessage());
            org.mockito.Mockito.verifyNoInteractions(task);
        }
    }

    @Test
    void adminCanTriggerCustomerExpiryTask() {
        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);

            controller.checkExpire();

            verify(task).checkCustomerExpire();
        }
    }
}
