package com.aigicrm.module.reminder.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReminderServiceTest {
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizReminder.class);
        TableInfoHelper.initTableInfo(assistant, BizCustomer.class);
    }
    private final BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysConfigService configService = mock(SysConfigService.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final ReminderService service = new ReminderService(
            reminderMapper, customerMapper, userMapper, deptMapper, configService, scopeResolver);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingCount_combinesUnfollowedExpiringAndSharedReminders() {
        authenticate(1L, "all");
        when(scopeResolver.resolve(any())).thenReturn(null);
        when(customerMapper.selectCount(any())).thenReturn(5L);
        when(reminderMapper.selectCount(any())).thenReturn(2L, 3L);

        assertEquals(6L, service.pendingCount());
    }

    @Test
    void process_rejectsReminderOutsideVisibleOwnerScope() {
        authenticate(1L, "self");
        when(scopeResolver.resolve(any())).thenReturn(List.of(1L));
        BizReminder reminder = new BizReminder();
        reminder.setId(9L);
        reminder.setOwnerId(2L);
        when(reminderMapper.selectById(9L)).thenReturn(reminder);

        assertThrows(BusinessException.class, () -> service.process(9L));
        verify(reminderMapper, never()).updateById(any(BizReminder.class));
    }

    @Test
    void expiringPage_returnsRemainingDaysWithoutMaterializingReminder() {
        authenticate(1L, "all");
        when(scopeResolver.resolve(any())).thenReturn(null);
        BizCustomer customer = new BizCustomer();
        customer.setId(12L);
        customer.setCustomerName("临期客户");
        customer.setExpireDate(LocalDate.now().plusDays(5));
        customer.setOwnerId(1L);
        Page<BizCustomer> customers = new Page<>(1, 10);
        customers.setTotal(1);
        customers.setRecords(List.of(customer));
        when(customerMapper.selectPage(any(), any())).thenReturn(customers);
        when(reminderMapper.selectOne(any())).thenReturn(null);

        var result = service.expiringPage(1, 10, null, null, null, null);

        assertEquals(1L, result.getTotal());
        assertEquals(5L, result.getRecords().get(0).get("remainingDays"));
        verify(reminderMapper, never()).insert(any(BizReminder.class));
    }

    @Test
    void process_marksVisibleReminderProcessed() {
        authenticate(1L, "self");
        when(scopeResolver.resolve(any())).thenReturn(List.of(1L));
        BizReminder reminder = new BizReminder();
        reminder.setId(10L);
        reminder.setOwnerId(1L);
        reminder.setStatus("pending");
        when(reminderMapper.selectById(10L)).thenReturn(reminder);

        service.process(10L);

        assertEquals("processed", reminder.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(reminder.getProcessedAt());
        verify(reminderMapper).updateById(reminder);
    }

    private void authenticate(Long userId, String dataScope) {
        SecurityUser user = new SecurityUser();
        user.setUserId(userId);
        user.setUsername("tester");
        user.setDataScope(dataScope);
        user.setRoleCodes(List.of("salesperson"));
        user.setAuthorities(List.of(() -> "reminder:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
