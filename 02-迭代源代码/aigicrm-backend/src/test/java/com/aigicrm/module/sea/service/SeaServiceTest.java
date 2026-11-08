package com.aigicrm.module.sea.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.entity.BizSeaConfig;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaConfigMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

/**
 * 【修复 2026-08-09 复核 B1】all 数据范围用户也不得看到禁用池(status=0)的存量客户。
 * 回归保护: visiblePoolIds(2026-10-19 前名 visiblePoolIdsOrNull) 不对 all 返回 null(不过滤),而是返回全部启用池 ID。
 */
class SeaServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测环境无 Spring 容器：手动初始化 MyBatis-Plus lambda 缓存(LambdaQueryWrapper 依赖)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaPool.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaConfig.class);
        // completeClaim 用 LambdaUpdateWrapper<BizCustomer> 显式置空开票基准(2026-10-08 修复)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizCustomer.class);
    }

    private void setCurrentUser(SecurityUser u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    @Test
    void allDataScopeUser_seesOnlyEnabledPools() {
        // all 数据范围用户(通常管理员)
        SecurityUser u = new SecurityUser();
        u.setUserId(1L);
        u.setDataScope("all");
        setCurrentUser(u);

        // 两个池: 启用(1) + 禁用(0)
        BizSeaConfig enabled = new BizSeaConfig();
        enabled.setId(10L);
        enabled.setStatus(1);
        BizSeaConfig disabled = new BizSeaConfig();
        disabled.setId(20L);
        disabled.setStatus(0);

        BizSeaConfigMapper configMapper = mock(BizSeaConfigMapper.class);
        when(configMapper.selectList(any())).thenReturn(List.of(enabled, disabled));
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);

        // page() 的 selectPage 返回一条属于启用池的待捞取记录
        BizSeaPool pool = new BizSeaPool();
        pool.setId(100L);
        pool.setCustomerId(500L);
        pool.setPoolId(10L); // 启用池
        pool.setStatus("available");
        Page<BizSeaPool> pageResult = new Page<>(1, 15);
        pageResult.setRecords(List.of(pool));
        pageResult.setTotal(1);
        when(poolMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(poolMapper.selectCount(any())).thenReturn(1L);

        CustomerService customerService = mock(CustomerService.class);
        BizCustomerMapper custMapper = customerMapper();
        SeaConfigService configService = new SeaConfigService(configMapper, poolMapper, deptMapper, userMapper,
                mock(com.aigicrm.module.system.mapper.SysRoleMapper.class));
        // SeaService 字段顺序: seaPoolMapper, seaConfigMapper, customerMapper, userMapper, deptMapper, seaConfigService, customerService, configService
        SeaService seaService = new SeaService(poolMapper, configMapper, custMapper,
                userMapper, deptMapper, configService, customerService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        // page() 内部: visiblePoolIds() = [10](启用池), 禁用池 20 被排除
        IPage<BizSeaPool> result = seaService.page(1, 15, null, null, null, null, null, "available");
        assertEquals(1, result.getRecords().size());
        assertEquals(10L, result.getRecords().get(0).getPoolId());
        // 关键断言: 查询条件含 pool_id IN(启用池过滤生效), 且参数值只有启用池 id 10、无禁用池 20。
        // 修复前 all 用户旧名 visiblePoolIdsOrNull 返回 null → SQL 无 pool_id IN 条件(禁用池也可见)。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<BizSeaPool>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(poolMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getExpression().getSqlSegment();
        assertTrue(sql.contains("pool_id IN"), "all 用户也应受启用池过滤(含 pool_id IN): " + sql);
        // 参数值: IN 列表应只含启用池 id 10
        java.util.Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        String joined = String.valueOf(params.values());
        assertTrue(joined.contains("10"), "IN 参数应含启用池 id 10: " + joined);
        assertFalse(joined.contains("20"), "IN 参数不应含禁用池 id 20: " + joined);
    }

    private BizCustomerMapper customerMapper() {
        BizCustomerMapper m = mock(BizCustomerMapper.class);
        BizCustomer c = new BizCustomer();
        c.setId(500L);
        c.setCustomerName("测试客户");
        c.setOfficeId(4L);
        when(m.selectById(500L)).thenReturn(c);
        return m;
    }

    @Test
    void page_returnsPoolOperationFlagsFromPoolPermissions() {
        SecurityUser user = new SecurityUser();
        user.setUserId(7L);
        user.setDataScope("all");
        setCurrentUser(user);

        BizSeaConfig enabled = new BizSeaConfig();
        enabled.setId(10L);
        enabled.setStatus(1);
        BizSeaConfigMapper configMapper = mock(BizSeaConfigMapper.class);
        when(configMapper.selectList(any())).thenReturn(List.of(enabled));

        BizSeaPool first = new BizSeaPool();
        first.setId(100L);
        first.setPoolId(10L);
        first.setCustomerId(500L);
        first.setStatus("available");
        Page<BizSeaPool> result = new Page<>(1, 15);
        result.setRecords(List.of(first));
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        when(poolMapper.selectPage(any(), any())).thenReturn(result);

        SeaConfigService configService = mock(SeaConfigService.class);
        when(configService.listVisible()).thenReturn(List.of(enabled));
        when(configService.hasPoolPerm(any(), any(), any())).thenAnswer(invocation -> {
            String permission = invocation.getArgument(1);
            return SeaConfigService.PERM_EDIT.equals(permission)
                    || SeaConfigService.PERM_CLAIM.equals(permission);
        });
        SeaService service = new SeaService(poolMapper, configMapper, customerMapper(),
                mock(SysUserMapper.class), mock(SysDeptMapper.class), configService, mock(CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        IPage<BizSeaPool> page = service.page(1, 15, null, null, null, null, null, "available");

        assertTrue(page.getRecords().get(0).getCanEdit());
        assertFalse(page.getRecords().get(0).getCanDelete());
        assertTrue(page.getRecords().get(0).getCanClaim());
        verify(configService, times(3)).hasPoolPerm(eq(10L), any(), eq(user));
    }

    // ===== deletePool 消息统一（2026-08-14 防探测: 不存在/状态不符/无权限统一文案） =====

    private SeaService newSeaService(BizSeaPoolMapper poolMapper, SeaConfigService configService) {
        return new SeaService(poolMapper, mock(BizSeaConfigMapper.class), customerMapper(),
                mock(SysUserMapper.class), mock(SysDeptMapper.class), configService, mock(CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    @Test
    void deletePool_missingPool_unifiedMessage() {
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        when(poolMapper.selectById(1L)).thenReturn(null);
        SeaService svc = newSeaService(poolMapper, mock(SeaConfigService.class));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deletePool(1L));
        assertEquals("资源不存在或无权访问", ex.getMessage(), "池不存在必须与无权限统一文案");
    }

    @Test
    void deletePool_notAvailableStatus_unifiedMessage() {
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        BizSeaPool claimed = new BizSeaPool();
        claimed.setId(1L);
        claimed.setPoolId(10L);
        claimed.setStatus("claimed");
        when(poolMapper.selectById(1L)).thenReturn(claimed);
        SeaService svc = newSeaService(poolMapper, mock(SeaConfigService.class));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deletePool(1L));
        assertEquals("资源不存在或无权访问", ex.getMessage(), "状态不符(已捞取)必须与不存在统一文案");
    }

    @Test
    void deletePool_noPoolPerm_unifiedMessage403() {
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        BizSeaPool available = new BizSeaPool();
        available.setId(1L);
        available.setPoolId(10L);
        available.setStatus("available");
        when(poolMapper.selectById(1L)).thenReturn(available);
        SeaConfigService configService = mock(SeaConfigService.class);
        when(configService.hasPoolPerm(any(), any(), any())).thenReturn(false);
        SeaService svc = newSeaService(poolMapper, configService);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deletePool(1L));
        assertEquals(403, ex.getCode());
        assertEquals("资源不存在或无权访问", ex.getMessage());
    }

    // ===== 删除一致性(2026-10-18): 公海管理删除是池行+客户的唯一成对删除路径 =====

    /** 负向: 客户有票款/在途审批(customerService.assertDeletable 拒绝) → 池行与客户都不得删除 */
    @Test
    void deletePool_customerNotDeletable_keepsPoolAndCustomer() {
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        BizSeaPool available = new BizSeaPool();
        available.setId(1L);
        available.setPoolId(10L);
        available.setCustomerId(500L);
        available.setStatus("available");
        when(poolMapper.selectById(1L)).thenReturn(available);

        BizSeaConfig enabled = new BizSeaConfig();
        enabled.setId(10L);
        SeaConfigService configService = mock(SeaConfigService.class);
        when(configService.hasPoolPerm(any(), any(), any())).thenReturn(true);
        when(configService.listVisible()).thenReturn(List.of(enabled));

        CustomerService customerService = mock(CustomerService.class);
        doThrow(new BusinessException("该客户有 1 笔开票,不允许删除"))
                .when(customerService).assertDeletable(500L);
        BizCustomerMapper custMapper = customerMapper();
        SeaService svc = new SeaService(poolMapper, mock(BizSeaConfigMapper.class), custMapper,
                mock(SysUserMapper.class), mock(SysDeptMapper.class), configService, customerService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        assertThrows(BusinessException.class, () -> svc.deletePool(1L));
        verify(custMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
        verify(poolMapper, never()).delete(any());
    }

    /** 正向: available 且客户可删 → 先条件结束池行, 再走 CustomerService 统一删除(分享/提醒/快照) */
    @Test
    void deletePool_availableCustomer_endsPoolThenUnifiedCustomerDelete() {
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        BizSeaPool available = new BizSeaPool();
        available.setId(1L);
        available.setPoolId(10L);
        available.setCustomerId(500L);
        available.setStatus("available");
        when(poolMapper.selectById(1L)).thenReturn(available);
        when(poolMapper.delete(any())).thenReturn(1);

        BizSeaConfig enabled = new BizSeaConfig();
        enabled.setId(10L);
        SeaConfigService configService = mock(SeaConfigService.class);
        when(configService.hasPoolPerm(any(), any(), any())).thenReturn(true);
        when(configService.listVisible()).thenReturn(List.of(enabled));

        BizCustomerMapper custMapper = customerMapper();
        CustomerService customerService = mock(CustomerService.class);
        SeaService svc = new SeaService(poolMapper, mock(BizSeaConfigMapper.class), custMapper,
                mock(SysUserMapper.class), mock(SysDeptMapper.class), configService, customerService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        svc.deletePool(1L);

        // 池行条件删除(status=available) 先于统一客户删除; 客户删除的唯一写入口是 CustomerService
        ArgumentCaptor<LambdaQueryWrapper<BizSeaPool>> cap = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(poolMapper).delete(cap.capture());
        assertTrue(cap.getValue().getSqlSegment().contains("status"), "池行删除必须带状态条件: " + cap.getValue().getSqlSegment());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue("available"));
        verify(customerService).deleteFromSeaPool(500L);
        verify(custMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    // ===== completeClaim 捞取重置口径（2026-10-08 需求对齐 P1 修复） =====

    @Test
    void completeClaim_resetsProtectionFromConfigAndClearsExtension() {
        // 捞取=归新负责人重新起算:①天数走 sys_config(原硬编码360/180,管理员改配置不生效);
        // ②清零 approvedExtendDays(原残留延期在下次开票重算时叠加,公海回收被架空);
        // ③显式置空 lastInvoiceDate(updateById 的 NOT_NULL 策略不写 null,必须 wrapper set)
        SecurityUser admin = new SecurityUser();
        admin.setUserId(7L);
        setCurrentUser(admin);

        BizSeaPool pool = new BizSeaPool();
        pool.setId(100L);
        pool.setCustomerId(500L);
        pool.setStatus("pending_approval");
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        when(poolMapper.selectOne(any())).thenReturn(pool);

        BizCustomer customer = new BizCustomer();
        customer.setId(500L);
        customer.setApprovedExtendDays(360); // 原负责人任内审批的延期(残留源)
        customer.setLastInvoiceDate(java.time.LocalDate.now().minusDays(200));
        BizCustomerMapper custMapper = mock(BizCustomerMapper.class);
        when(custMapper.selectById(500L)).thenReturn(customer);

        com.aigicrm.module.system.service.SysConfigService sysConfig =
                mock(com.aigicrm.module.system.service.SysConfigService.class);
        when(sysConfig.getValue("customer.protection.days")).thenReturn("180"); // 管理员改过配置
        when(sysConfig.getValue("customer.adjust.days")).thenReturn("90");

        // 【2026-11-02 裁决B 收敛】completeClaim 经 CustomerService.applyClaimReset 真公式重置
        // (捞取线=捞取日+adjust.days), 注入 spy 包裹的真实例: 公式走真逻辑, removeAllShares/
        // cleanPendingReminders 仍可 verify(末尾两断言依赖)
        CustomerService customerService = org.mockito.Mockito.spy(new CustomerService(
                custMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizInvoiceMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                sysConfig,
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class)));
        SeaService svc = new SeaService(poolMapper, mock(BizSeaConfigMapper.class), custMapper,
                mock(SysUserMapper.class), mock(SysDeptMapper.class), mock(SeaConfigService.class),
                customerService,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        svc.completeClaim(500L, 7L);

        java.time.LocalDate today = java.time.LocalDate.now();
        ArgumentCaptor<BizCustomer> cap = ArgumentCaptor.forClass(BizCustomer.class);
        verify(custMapper).updateById(cap.capture());
        BizCustomer saved = cap.getValue();
        assertEquals(7L, saved.getOwnerId());
        assertEquals(today, saved.getClaimDate(), "捞取日期=当天(裁决B, 独立字段)");
        org.junit.jupiter.api.Assertions.assertNull(saved.getRegisterDate(), "注册日不再被捞取覆写(裁决B, 本用例未设注册日应保持空)");
        assertEquals(today.plusDays(90), saved.getExpireDate(), "到期日=捞取日+adjust.days(90, 裁决B短保护)");
        assertEquals(today.plusDays(90), saved.getAdjustDate(), "捞取到期日=捞取日+adjust.days(90, 裁决C)");
        assertEquals(java.lang.Integer.valueOf(0), saved.getApprovedExtendDays(), "捞取必须清零历史延期,防叠加架空公海回收");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizCustomer>> wcap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(custMapper).update(isNull(), wcap.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<BizCustomer, ?, ?> w =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<BizCustomer, ?, ?>) wcap.getValue();
        assertTrue(w.getSqlSet().contains("last_invoice_date"),
                "必须显式 set last_invoice_date(开票推导基准随新周期重置): " + w.getSqlSet());
        assertTrue(w.getParamNameValuePairs().containsValue(null), "last_invoice_date 应置 null");
        verify(customerService).removeAllShares(500L);
        // 【体检 P1 2026-10-12】INV-05 四路径全量口径: 捞取完成同样清全部类型 pending 提醒
        verify(customerService).cleanPendingReminders(500L);
    }

    @Test
    void completeClaim_rejectsWhenPoolClaimedByAnotherUser() {
        // 【B2 加固 2026-10-21·backend-customer 域移交】completeClaim 按 customerId+pending_approval
        // 定位池行后原样覆写 claimUserId——若行已被他人重新认领(锁超时释放后他人接管), 审批通过
        // 会把客户判给错误的人。守卫: 行上申请人非空且≠审批人 → 抛错回滚, 不写池不写客户。
        BizSeaPool pool = new BizSeaPool();
        pool.setId(100L);
        pool.setCustomerId(500L);
        pool.setStatus("pending_approval");
        pool.setClaimUserId(8L); // 池行现属申请人 8, 而本审批申请人是 7
        BizSeaPoolMapper poolMapper = mock(BizSeaPoolMapper.class);
        when(poolMapper.selectOne(any())).thenReturn(pool);
        BizCustomerMapper custMapper = mock(BizCustomerMapper.class);
        SeaService svc = new SeaService(poolMapper, mock(BizSeaConfigMapper.class), custMapper,
                mock(SysUserMapper.class), mock(SysDeptMapper.class), mock(SeaConfigService.class),
                mock(CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.completeClaim(500L, 7L));
        assertTrue(String.valueOf(ex.getMessage()).contains("已被他人申请"), "报错须指明认领冲突: " + ex.getMessage());
        verify(poolMapper, never()).updateById(any(BizSeaPool.class));
        verify(custMapper, never()).updateById(any(BizCustomer.class));
        verify(custMapper, never()).update(any(), any());
    }
}
