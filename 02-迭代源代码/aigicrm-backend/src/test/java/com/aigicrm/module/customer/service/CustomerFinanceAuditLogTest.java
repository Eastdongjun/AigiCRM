package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizInvoice;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P2-3 2026-10-18】财务删除审计落库失败的可见性：
 * logRecordDeletion 的 catch 原为完全空吞(Exception ignored)——审计 insert 失败时
 * 财务删除留痕静默丢失且不可察觉。语义须保持"尽力而为不阻断删除"，
 * 但失败必须以 WARN 落日志（与类内部门同步失败的同款处置）。
 */
class CustomerFinanceAuditLogTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
    private final BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);
    private final SysLogMapper financeAuditLogMapper = mock(SysLogMapper.class);

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 依赖实体列缓存(单跑必挂的老坑, 自足 init)
        for (Class<?> t : List.of(BizCustomer.class, BizInvoice.class,
                com.aigicrm.module.customer.entity.BizCustomerIndicator.class)) {
            if (TableInfoHelper.getTableInfo(t) == null) {
                TableInfoHelper.initTableInfo(
                        new com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), t);
            }
        }
    }

    private CustomerService newService() {
        return new CustomerService(
                customerMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper,
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                financeAuditLogMapper,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    @Test
    void auditInsertFailureKeepsDeleteAndWarns() {
        BizInvoice invoice = new BizInvoice();
        invoice.setId(55L);
        invoice.setCustomerId(9L);
        invoice.setInvoiceNumber("INV-55");
        when(invoiceMapper.selectById(55L)).thenReturn(invoice);
        when(customerMapper.selectById(9L)).thenReturn(null); // refreshLastInvoiceDate 空守卫跳过
        doThrow(new RuntimeException("sys_log down")).when(financeAuditLogMapper).insert(any(
                com.aigicrm.module.system.entity.SysLog.class));
        CustomerService service = newService();

        Logger logger = (Logger) LoggerFactory.getLogger(CustomerService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUser).thenReturn(null);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

            // 尽力而为语义保持: 审计失败不得阻断删除主流程
            assertDoesNotThrow(() -> service.deleteInvoice(9L, 55L, "财务对冲冲销"),
                    "审计落库失败应被吞掉不阻断主流程");
            verify(invoiceMapper).deleteById(55L);
        } finally {
            logger.detachAppender(appender);
        }

        // 可见性: 失败必须落 WARN(空吞=审计丢失不可察觉)
        boolean warned = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("财务删除审计"));
        assertTrue(warned, "审计 insert 失败应产生含\"财务删除审计\"的 WARN 日志, 实际: "
                + appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void auditInsertSuccessStaysSilent() {
        BizInvoice invoice = new BizInvoice();
        invoice.setId(56L);
        invoice.setCustomerId(9L);
        when(invoiceMapper.selectById(56L)).thenReturn(invoice);
        when(customerMapper.selectById(9L)).thenReturn(null);
        CustomerService service = newService();

        Logger logger = (Logger) LoggerFactory.getLogger(CustomerService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUser).thenReturn(null);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            service.deleteInvoice(9L, 56L, "正常删除");
        } finally {
            logger.detachAppender(appender);
        }

        boolean warned = appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN);
        assertTrue(!warned, "审计落库成功不应产生 WARN 噪音");
        verify(financeAuditLogMapper).insert(any(com.aigicrm.module.system.entity.SysLog.class));
    }

    @Test
    void missingReasonStillRejected() {
        // 口径回归: 删除原因必填(财务留痕裁决)不受本改动影响
        when(invoiceMapper.selectById(57L)).thenReturn(null);
        CustomerService service = newService();
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.deleteInvoice(9L, 57L, " "));
        verify(invoiceMapper, never()).deleteById(57L);
    }
}
