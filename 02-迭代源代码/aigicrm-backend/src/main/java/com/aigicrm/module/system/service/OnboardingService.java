package com.aigicrm.module.system.service;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.service.CustomerNameNormalizer;
import com.aigicrm.module.system.entity.SysConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * B1 首启演示数据：仅 admin（controller 权限）显式调用，空库/无同名演示客户时插入。
 * 不触碰真实客户；tenant 由 MyBatis 租户插件补，group 固定 1（单集团安装）。
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final List<DemoCustomer> DEMO_CUSTOMERS = List.of(
            new DemoCustomer("DEMO001", "演示客户-制造业A", "制造业", "成熟"),
            new DemoCustomer("DEMO002", "演示客户-互联网B", "互联网", "成熟创新"),
            new DemoCustomer("DEMO003", "演示客户-新能源C", "新能源", "突破")
    );

    private final BizCustomerMapper customerMapper;
    private final SysConfigService configService;

    public record DemoCustomer(String code, String name, String industry, String devType) {}

    @Transactional
    public Map<String, Object> seedDemo() {
        int inserted = 0;
        for (DemoCustomer demo : DEMO_CUSTOMERS) {
            Long exists = customerMapper.selectCount(new LambdaQueryWrapper<BizCustomer>()
                    .eq(BizCustomer::getCustomerCode, demo.code()));
            if (exists != null && exists > 0) continue;
            BizCustomer c = new BizCustomer();
            c.setCustomerCode(demo.code());
            c.setCustomerName(demo.name());
            c.setNameNorm(CustomerNameNormalizer.normalizeOrThrow(demo.name()));
            c.setIndustry(demo.industry());
            c.setDevType(demo.devType());
            c.setOwnerId(1L);
            c.setDeptId(1L);
            c.setGroupId(1L);
            c.setGroupName("默认集团");
            c.setStatus("normal");
            c.setProtectionStatus("active");
            customerMapper.insert(c);
            inserted++;
        }
        SysConfig flag = new SysConfig();
        flag.setConfigKey("onboarding.demoSeeded");
        flag.setConfigValue("true");
        flag.setDescription("B1 演示数据标记");
        configService.batchUpdate(List.of(flag));
        return Map.of("inserted", inserted, "totalDemo", DEMO_CUSTOMERS.size(), "demoSeeded", true);
    }
}
