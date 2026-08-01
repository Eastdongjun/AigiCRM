package com.ycmn.module.customer.service;

import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizInvoice;
import com.ycmn.module.customer.entity.BizPayment;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerIndicatorMapper;
import com.ycmn.module.customer.mapper.BizCustomerLogMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.customer.mapper.BizContactMapper;
import com.ycmn.module.customer.mapper.BizInvoiceMapper;
import com.ycmn.module.customer.mapper.BizPaymentMapper;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private CustomerService newService(BizInvoiceMapper invoiceMapper, BizPaymentMapper paymentMapper,
                                       BizCustomerMapper customerMapper) {
        return new CustomerService(
                customerMapper,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                paymentMapper,
                mock(SysUserMapper.class),
                mock(SysOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class));
    }

    @Test
    void deleteInvoice_rejectsInvoiceOfAnotherCustomer() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizInvoice invoice = new BizInvoice();
        invoice.setId(1L);
        invoice.setCustomerId(100L);
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);

        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class), mock(BizCustomerMapper.class));
        // 路径客户 200 ≠ 记录客户 100 → 拒绝，防跨客户删除（IDOR）
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L));
    }

    @Test
    void deleteInvoice_missingInvoice_rejects() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectById(1L)).thenReturn(null);
        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class), mock(BizCustomerMapper.class));
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L));
    }

    @Test
    void deletePayment_rejectsPaymentOfAnotherCustomer() {
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizPayment payment = new BizPayment();
        payment.setId(2L);
        payment.setCustomerId(100L);
        when(paymentMapper.selectById(2L)).thenReturn(payment);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), paymentMapper, mock(BizCustomerMapper.class));
        assertThrows(BusinessException.class, () -> svc.deletePayment(200L, 2L));
    }
}
