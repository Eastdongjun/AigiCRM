package com.aigicrm.module.customer.service.support;

import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.customer.mapper.BizContactMapper;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerLogMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.customer.service.CustomerContactShareService;
import com.aigicrm.module.customer.service.CustomerEventService;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.customer.service.CustomerVisibilityService;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.module.system.service.SysConfigService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CustomerService 测试装配单一入口。
 *
 * <p>目的：S5.3 拆分期间 CustomerService 的构造器会不断增加子服务参数；
 * 所有测试只依赖本工厂的稳定签名，拆分时只改本文件一处，避免 40+ 处
 * {@code new CustomerService(...)} 跟着改。</p>
 *
 * <p>本类只做装配，不含断言/业务逻辑；保持与拆分前构造器参数顺序一致。</p>
 */
public final class CustomerServiceFixture {

    private CustomerServiceFixture() {
    }

    public static CustomerService create(
            BizCustomerMapper customerMapper,
            CustomerVisibilityService customerVisibilityService,
            BizCustomerLogMapper customerLogMapper,
            BizInvoiceMapper invoiceMapper,
            BizCustomerShareMapper shareMapper,
            BizCustomerIndicatorMapper indicatorMapper,
            BizContactMapper contactMapper,
            BizPaymentMapper paymentMapper,
            BizCustomerStageAmountMapper stageAmountMapper,
            BizSeaPoolMapper seaPoolMapper,
            SysUserMapper userMapper,
            SysDeptMapper deptMapper,
            BizReminderMapper reminderMapper,
            JdbcTemplate jdbc,
            SysConfigService configService,
            OrgScopeResolver scopeResolver,
            CustomerAccessService customerAccessService,
            RolePermScopeService rolePermScopeService,
            BizApprovalMapper approvalMapper,
            SysLogMapper financeAuditLogMapper,
            CustomerEventService customerEventService) {
        CustomerContactShareService contactShareService = new CustomerContactShareService(customerMapper,
                shareMapper, reminderMapper, contactMapper, userMapper, jdbc);
        return new CustomerService(customerMapper, customerVisibilityService, customerLogMapper, invoiceMapper,
                shareMapper, indicatorMapper, paymentMapper, stageAmountMapper, seaPoolMapper,
                userMapper, deptMapper, jdbc, configService, scopeResolver,
                customerAccessService, rolePermScopeService, approvalMapper, financeAuditLogMapper,
                customerEventService, contactShareService);
    }
}
