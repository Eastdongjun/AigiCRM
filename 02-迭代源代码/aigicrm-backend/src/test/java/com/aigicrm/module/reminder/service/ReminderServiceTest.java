package com.aigicrm.module.reminder.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** 【容量评估修复§1】page() 的行回填必须走批量化: 每维一次 selectBatchIds,
     *  禁止逐行 selectById 点查;且回填字段值/行序与逐行版等价 */
    @Test
    void page_backfillsRowsWithBatchedLookupsInsteadOfPerRowPointQueries() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        // 两行同客户/负责人/部门 → 验证 id 去重后每维仍只发 1 次批量查询
        BizReminder r1 = new BizReminder();
        r1.setId(101L);
        r1.setCustomerId(11L);
        r1.setOwnerId(1L);
        r1.setOfficeId(5L);
        BizReminder r2 = new BizReminder();
        r2.setId(102L);
        r2.setCustomerId(11L);
        r2.setOwnerId(1L);
        r2.setOfficeId(5L);
        Page<BizReminder> reminders = new Page<>(1, 15);
        reminders.setTotal(2);
        reminders.setRecords(List.of(r1, r2));
        when(reminderMapper.selectPage(any(), any())).thenReturn(reminders);
        BizCustomer customer = new BizCustomer();
        customer.setId(11L);
        customer.setCustomerName("客户甲");
        customer.setLastInvoiceDate(LocalDate.now().minusDays(3));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(customer));
        com.aigicrm.module.system.entity.SysUser owner = new com.aigicrm.module.system.entity.SysUser();
        owner.setId(1L);
        owner.setRealName("张销售");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(owner));
        com.aigicrm.module.system.entity.SysDept dept = new com.aigicrm.module.system.entity.SysDept();
        dept.setId(5L);
        dept.setDeptName("山西办事处");
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept));

        var result = service.page(1, 15, null, null, null, null, null, null);

        // 行序与回填字段值等价(同逐行版)
        assertEquals(List.of(101L, 102L), result.getRecords().stream().map(BizReminder::getId).toList());
        assertEquals("客户甲", result.getRecords().get(0).getCustomerName());
        assertEquals(LocalDate.now().minusDays(3), result.getRecords().get(1).getLastInvoiceDate());
        assertEquals("张销售", result.getRecords().get(0).getOwnerName());
        assertEquals("山西办事处", result.getRecords().get(1).getOfficeName());
        // 批量化断言: 每维恰好 1 次 selectBatchIds,逐行 selectById 点查为 0(原 3×size 次)
        verify(customerMapper, times(1)).selectBatchIds(any());
        verify(userMapper, times(1)).selectBatchIds(any());
        verify(deptMapper, times(1)).selectBatchIds(any());
        verify(customerMapper, never()).selectById(any());
        verify(userMapper, never()).selectById(any());
        verify(deptMapper, never()).selectById(any());
    }

    /** 【容量评估修复§1】expiringPage() 的逐行 4 查(dept/user 点查 + pending/processed selectOne×2)
     *  必须改为整页批量: reminder 侧全页仅 2 次 selectList、无 selectOne;且 reminderId/
     *  todayFollowedUp 的判定与逐行版等价(pending 优先、今日处理过=有 processed 今日行) */
    @Test
    void expiringPage_batchesPerRowReminderLookupsAndKeepsRowSemantics() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        BizCustomer withFollowUp = new BizCustomer();
        withFollowUp.setId(12L);
        withFollowUp.setCustomerName("临期客户甲");
        withFollowUp.setExpireDate(LocalDate.now().plusDays(5));
        BizCustomer withoutFollowUp = new BizCustomer();
        withoutFollowUp.setId(13L);
        withoutFollowUp.setCustomerName("临期客户乙");
        withoutFollowUp.setExpireDate(LocalDate.now().plusDays(9));
        Page<BizCustomer> customers = new Page<>(1, 10);
        customers.setTotal(2);
        customers.setRecords(List.of(withFollowUp, withoutFollowUp));
        when(customerMapper.selectPage(any(), any())).thenReturn(customers);
        // pending 查询与 processed 查询同走 selectList,按 wrapper 绑定参数区分应答(模拟 DB 行为)。
        // 注: MP wrapper 惰性求值,参数在 getSqlSegment() 渲染 SQL 时才写入 paramNameValuePairs
        // (真实执行亦先渲染后绑定),故此处先渲染再判参
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        when(reminderMapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<BizReminder> w = inv.getArgument(0);
            w.getSqlSegment();
            boolean pendingQuery = w.getParamNameValuePairs().containsValue("pending");
            BizReminder reminder = new BizReminder();
            if (pendingQuery) {
                reminder.setId(30L);
                reminder.setCustomerId(12L);
                reminder.setReminderType("expiring");
                reminder.setStatus("pending");
            } else {
                reminder.setId(40L);
                reminder.setCustomerId(12L);
                reminder.setReminderType("expiring");
                reminder.setStatus("processed");
                reminder.setProcessedAt(todayStart.plusHours(1));
            }
            return List.of(reminder);
        });

        var result = service.expiringPage(1, 10, null, null, null, null);

        // 行序不变;每客户判定与逐行版等价
        assertEquals(List.of(12L, 13L), result.getRecords().stream().map(r -> r.get("customerId")).toList());
        assertEquals(30L, result.getRecords().get(0).get("reminderId")); // 有 pending → reminderId 指向 pending(优先于今日已处理行)
        assertEquals(true, result.getRecords().get(0).get("todayFollowedUp"));
        assertEquals(5L, result.getRecords().get(0).get("remainingDays"));
        assertNull(result.getRecords().get(1).get("reminderId")); // 无任何提醒行
        assertEquals(false, result.getRecords().get(1).get("todayFollowedUp"));
        // 批量化断言: 全页 reminder 查询共 2 次(原 2×size 次 selectOne),点查为 0
        verify(reminderMapper, times(2)).selectList(any());
        verify(reminderMapper, never()).selectOne(any());
        verify(userMapper, never()).selectById(any());
        verify(deptMapper, never()).selectById(any());
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

    /** 【F15① 2026-10-18】软删客户的孤儿提醒(直删绕过 INV-05 清理的残留)不进列表,total 同步扣减 */
    @Test
    void page_filtersOrphanRemindersOfSoftDeletedCustomers() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);

        BizReminder alive = new BizReminder();
        alive.setId(1L);
        alive.setCustomerId(100L); // 客户存活: selectBatchIds 能查到
        BizReminder orphan = new BizReminder();
        orphan.setId(2L);
        orphan.setCustomerId(200L); // 客户已软删: @TableLogic 下 selectBatchIds 查不到 → 孤儿
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BizReminder> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 15);
        p.setRecords(new java.util.ArrayList<>(List.of(alive, orphan)));
        p.setTotal(2);
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);

        // 回填: 客户批查只回 100(200 被软删滤除); user/dept 批查空
        BizCustomer living = new BizCustomer();
        living.setId(100L);
        living.setCustomerName("存活客户");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(living));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of());

        com.baomidou.mybatisplus.core.metadata.IPage<BizReminder> result = service.page(1, 15, null, null, null, null, null, null);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getRecords().size(), "孤儿提醒应被滤除");
        org.junit.jupiter.api.Assertions.assertEquals(100L, result.getRecords().get(0).getCustomerId());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotal(), "total 应同步扣减孤儿数");
    }

    // ====== 【C1 2026-10-21】page() keyword 服务端消费: 客户名/编码 → customerId, 类型标签 → reminderType =====

    /** keyword 命中客户名/编码: wrapper 按 customerId 集合过滤, total 与行集同口径(单一 wrapper 分页) */
    @Test
    void page_keywordMatchesCustomerNameOrCode_filtersByCustomerId() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        BizCustomer byName = new BizCustomer();
        byName.setId(11L);
        byName.setCustomerName("山西平阳重工");
        BizCustomer byCode = new BizCustomer();
        byCode.setId(12L);
        byCode.setCustomerCode("NM-2026-012");
        when(customerMapper.selectList(any())).thenReturn(List.of(byName, byCode));
        // 模拟 DB 按 wrapper 过滤后的分页结果: 1 行, total=1(与筛选同口径)
        BizReminder hit = new BizReminder();
        hit.setId(101L);
        hit.setCustomerId(11L);
        Page<BizReminder> p = new Page<>(1, 15);
        p.setTotal(1);
        p.setRecords(List.of(hit));
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);
        // 回填: 客户 11 存活(避免被 F15① 孤儿过滤误伤)
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(byName));

        var result = service.page(1, 15, null, "平阳", null, null, null, null);

        assertEquals(1L, result.getTotal(), "total 应为筛选后总数");
        assertEquals(101L, result.getRecords().get(0).getId());
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReminder>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reminderMapper).selectPage(any(), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(segment.contains("customer_id IN"),
                "应按命中客户集合过滤: " + segment);
        var params = captor.getValue().getParamNameValuePairs();
        org.junit.jupiter.api.Assertions.assertTrue(params.containsValue(11L) && params.containsValue(12L),
                "客户名与编码命中的客户都应在集合内: " + params.values());
    }

    /** keyword 命中类型标签/类型码: 映射为 reminderType 集合("到期"→expiring/expire/protect_expiring) */
    @Test
    void page_keywordMatchesTypeLabel_mapsToReminderTypes() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        when(customerMapper.selectList(any())).thenReturn(List.of()); // 客户维无命中
        Page<BizReminder> p = new Page<>(1, 15);
        p.setTotal(0);
        p.setRecords(List.of());
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);

        service.page(1, 15, null, "到期", null, null, null, null);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReminder>> labelCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reminderMapper).selectPage(any(), labelCaptor.capture());
        String labelSegment = labelCaptor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(labelSegment.contains("reminder_type IN"),
                "类型标签应映射为 reminder_type 集合: " + labelSegment);
        var labelParams = labelCaptor.getValue().getParamNameValuePairs();
        org.junit.jupiter.api.Assertions.assertTrue(
                labelParams.containsValue("expiring") && labelParams.containsValue("expire")
                        && labelParams.containsValue("protect_expiring"),
                "\"到期\" 应命中三个到期类类型: " + labelParams.values());

        // 原始类型码同样命中(不区分大小写)
        service.page(1, 15, null, "SHARE_TO", null, null, null, null);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReminder>> codeCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reminderMapper, times(2)).selectPage(any(), codeCaptor.capture());
        LambdaQueryWrapper<BizReminder> second = codeCaptor.getAllValues().get(1);
        second.getSqlSegment(); // MP wrapper 惰性求值: 先渲染 SQL 才会写入 paramNameValuePairs
        org.junit.jupiter.api.Assertions.assertTrue(second.getSqlSegment().contains("reminder_type IN"),
                "类型码命中也应走 reminder_type 集合: " + second.getSqlSegment());
        org.junit.jupiter.api.Assertions.assertTrue(second.getParamNameValuePairs().containsValue("share_to"),
                "类型码 share_to 应命中(大小写不敏感): " + second.getParamNameValuePairs().values());
    }

    /** keyword 双空集(客户与类型均无命中): eq(customerId, -1L) 恒空守卫, 不生成 IN () */
    @Test
    void page_keywordNoMatch_usesMinusOneSentinelInsteadOfEmptyIn() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        when(customerMapper.selectList(any())).thenReturn(List.of());
        Page<BizReminder> p = new Page<>(1, 15);
        p.setTotal(0);
        p.setRecords(List.of());
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);

        var result = service.page(1, 15, null, "不存在的关键词XYZ", null, null, null, null);

        assertEquals(0L, result.getTotal(), "无命中 → 空页");
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReminder>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reminderMapper).selectPage(any(), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(segment.contains("customer_id ="),
                "空集应退化为 eq 哨兵(与 applyOwnerScope 的 -1L 同口径): " + segment);
        org.junit.jupiter.api.Assertions.assertFalse(segment.contains("IN ()"),
                "不得生成 IN () 空集合语法: " + segment);
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getParamNameValuePairs().containsValue(-1L),
                "哨兵值应为 -1L");
    }

    /** keyword 与 owner 数据范围叠加(AND 相交), 非替代: 范围外的命中客户行不可见 */
    @Test
    void page_keywordStacksWithOwnerScope() {
        authenticate(1L, "self");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(1L));
        BizCustomer matched = new BizCustomer();
        matched.setId(11L);
        matched.setCustomerName("平阳重工");
        when(customerMapper.selectList(any())).thenReturn(List.of(matched));
        Page<BizReminder> p = new Page<>(1, 15);
        p.setTotal(0);
        p.setRecords(List.of());
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);

        service.page(1, 15, null, "平阳", null, null, null, null);

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReminder>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reminderMapper).selectPage(any(), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(segment.contains("owner_id"),
                "keyword 不得绕过 owner 数据范围: " + segment);
        org.junit.jupiter.api.Assertions.assertTrue(segment.contains("customer_id IN"),
                "keyword 客户过滤应同时存在(AND 叠加): " + segment);
    }

    /** 无 keyword 时行为不变: 不发起客户维查询(既有口径回归) */
    @Test
    void page_withoutKeyword_skipsCustomerLookup() {
        authenticate(1L, "all");
        when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(null);
        Page<BizReminder> p = new Page<>(1, 15);
        p.setTotal(0);
        p.setRecords(List.of());
        when(reminderMapper.selectPage(any(), any())).thenReturn(p);

        service.page(1, 15, null, null, null, null, null, null);

        verify(customerMapper, never()).selectList(any());
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
