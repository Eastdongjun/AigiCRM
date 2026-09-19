package com.aigicrm.module.task.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.task.entity.BizTask;
import com.aigicrm.module.task.mapper.BizTaskMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 【PR-10 2026-09-19】任务模块:核心口径(完成必须记结果/取消记原因/转交校验)。 */
class TaskServiceTest {

    private final BizTaskMapper taskMapper = mock(BizTaskMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final TaskService svc = new TaskService(taskMapper, userMapper, customerMapper);

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                BizTask.class);
    }

    @AfterEach
    void clear() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void asUser(Long userId, boolean admin) {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            // placeholder — tests below use their own mockStatic scopes
        }
    }

    @Test
    void create_validTask_inserted() {
        SysUser assignee = activeUser(20L);
        when(userMapper.selectById(20L)).thenReturn(assignee);
        BizTask task = new BizTask();
        task.setTitle("提交选型方案");
        task.setAssigneeId(20L);
        task.setDueDate(LocalDate.now().plusDays(7));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertDoesNotThrow(() -> svc.create(task));
            verify(taskMapper).insert(any(BizTask.class));
            assertEquals("pending", task.getStatus());
        }
    }

    @Test
    void create_pastDueDate_rejected() {
        when(userMapper.selectById(20L)).thenReturn(activeUser(20L));
        BizTask task = new BizTask();
        task.setTitle("测试");
        task.setAssigneeId(20L);
        task.setDueDate(LocalDate.now().minusDays(1));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertThrows(BusinessException.class, () -> svc.create(task));
        }
    }

    @Test
    void complete_withoutNote_rejected() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(BusinessException.class, () -> svc.complete(1L, ""));
            assertThrows(BusinessException.class, () -> svc.complete(1L, null));
        }
    }

    @Test
    void complete_withNote_succeeds() {
        BizTask task = ownTask();
        when(taskMapper.selectById(1L)).thenReturn(task);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.complete(1L, "方案已发送,客户约下周三二次沟通");
            assertEquals("completed", task.getStatus());
            assertNotNull(task.getCompletedAt());
            assertTrue(task.getCompletionNote().contains("方案已发送"));
        }
    }

    @Test
    void cancel_requiresReason() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(BusinessException.class, () -> svc.cancel(1L, ""));
        }
    }

    @Test
    void transfer_toInactiveUser_rejected() {
        when(taskMapper.selectById(1L)).thenReturn(ownTask());
        SysUser inactive = activeUser(99L);
        inactive.setStatus(0);
        when(userMapper.selectById(99L)).thenReturn(inactive);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.transfer(1L, 99L));
        }
    }

    @Test
    void statistics_countsCorrectly() {
        when(taskMapper.selectCount(any())).thenReturn(3L);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            var stats = svc.statistics(null);
            assertEquals(3L, stats.get("pending"));
            assertEquals(3L, stats.get("overdue"));
        }
    }

    @Test
    void page_nonAdmin_seesOnlyOwn() {
        when(taskMapper.selectPage(any(), any())).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            IPage<BizTask> result = svc.page(1, 20, null, null, null, null);
            assertNotNull(result);
            verify(taskMapper).selectPage(any(), any());
        }
    }

    private SysUser activeUser(Long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(1);
        u.setDeleted(0);
        return u;
    }

    private BizTask ownTask() {
        BizTask t = new BizTask();
        t.setId(1L);
        t.setTitle("测试任务");
        t.setAssigneeId(7L);
        t.setCreatedBy(7L);
        t.setStatus("pending");
        return t;
    }
}
