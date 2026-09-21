package com.aigicrm.module.customer.fixture;

import com.aigicrm.module.customer.controller.CustomerController;
import com.aigicrm.module.customer.entity.BizCustomer;

/**
 * 【PR-05 残余 20260921】组合提交测试夹具。
 * 架构守卫禁止 controller 包(含测试)依赖实体——实体构造统一收敛到本夹具包。
 */
public final class CustomerFullFixtures {

    private CustomerFullFixtures() {
    }

    public static CustomerController.CustomerWithIndicatorRequest request(String customerName) {
        CustomerController.CustomerWithIndicatorRequest req = new CustomerController.CustomerWithIndicatorRequest();
        BizCustomer c = new BizCustomer();
        c.setCustomerName(customerName);
        req.setCustomer(c);
        return req;
    }
}
