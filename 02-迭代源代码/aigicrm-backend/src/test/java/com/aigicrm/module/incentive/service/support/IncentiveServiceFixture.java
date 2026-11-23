package com.aigicrm.module.incentive.service.support;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.service.CustomerVisibilityService;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * IncentiveService 测试装配单一入口。S5.3 拆分期间只改本文件，测试调用点不动。
 */
public final class IncentiveServiceFixture {

    private IncentiveServiceFixture() {
    }

    public static IncentiveService create(
            BizIncentiveCategoryMapper categoryMapper,
            BizIncentiveRecordMapper recordMapper,
            SysUserMapper userMapper,
            BizFileMapper fileMapper,
            BizCustomerMapper customerMapper,
            SysDeptMapper deptMapper,
            OrgScopeResolver scopeResolver,
            CustomerVisibilityService customerVisibilityService,
            JdbcTemplate jdbc) {
        return new IncentiveService(categoryMapper, recordMapper, userMapper, fileMapper, customerMapper,
                deptMapper, scopeResolver, customerVisibilityService, jdbc);
    }
}
