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
import static org.mockito.ArgumentMatchers.anyString;
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
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        when(customerMapper.selectCount(any())).thenReturn(5L);
        when(reminderMapper.selectCount(any())).thenReturn(2L, 3L);

        assertEquals(6L, service.pendingCount());
    }

    @Test
    void process_rejectsReminderOutsideVisibleOwnerScope() {
        authenticate(1L, "self");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(1L));
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
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
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
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(1L));
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

    @Test
    void detail_returnsReminderWithCustomerBriefAndOwnerNames() {
        authenticate(1L, "self");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(1L));
        BizReminder reminder = new BizReminder();
        reminder.setId(21L);
        reminder.setCustomerId(77L);
        reminder.setOwnerId(1L);
        reminder.setOfficeId(5L);
        reminder.setReminderType("invoice_overdue");
        reminder.setStatus("pending");
        reminder.setOverdueDays(12);
        when(reminderMapper.selectById(21L)).thenReturn(reminder);
        BizCustomer customer = new BizCustomer();
        customer.setId(77L);
        customer.setCustomerCode("C0077");
        customer.setCustomerName("山西平阳重工机械有限责任公司");
        customer.setStatus("warning");
        customer.setProvince("山西省");
        customer.setCity("临汾市");
        customer.setExpireDate(LocalDate.now().plusDays(20));
        when(customerMapper.selectById(77L)).thenReturn(customer);
        com.aigicrm.module.system.entity.SysUser owner = new com.aigicrm.module.system.entity.SysUser();
        owner.setRealName("张销售");
        when(userMapper.selectById(1L)).thenReturn(owner);
        com.aigicrm.module.system.entity.SysDept dept = new com.aigicrm.module.system.entity.SysDept();
        dept.setDeptName("山西办事处");
        when(deptMapper.selectById(5L)).thenReturn(dept);

        var detail = service.detail(21L);

        assertEquals("invoice_overdue", detail.get("reminderType"));
        assertEquals(12, detail.get("overdueDays"));
        assertEquals("张销售", detail.get("ownerName"));
        assertEquals("山西办事处", detail.get("officeName"));
        @SuppressWarnings("unchecked")
        var brief = (java.util.Map<String, Object>) detail.get("customer");
        assertEquals("山西平阳重工机械有限责任公司", brief.get("customerName"));
        assertEquals("山西省临汾市", brief.get("region"));
    }

    @Test
    void detail_rejectsReminderOutsideVisibleOwnerScope() {
        authenticate(1L, "self");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(1L));
        BizReminder reminder = new BizReminder();
        reminder.setId(22L);
        reminder.setOwnerId(2L);
        when(reminderMapper.selectById(22L)).thenReturn(reminder);

        assertThrows(BusinessException.class, () -> service.detail(22L));
        verify(customerMapper, never()).selectById(any());
    }

    @Test
    void detail_reportsMissingReminder() {
        authenticate(1L, "self");
        when(reminderMapper.selectById(23L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.detail(23L));
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
