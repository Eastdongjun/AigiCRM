package com.aigicrm.module.system.service;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizCustomer.class);
    }

    @Test
    void seedDemo_insertsThreeWhenEmptyAndSetsFlag() {
        BizCustomerMapper mapper = mock(BizCustomerMapper.class);
        SysConfigService configService = mock(SysConfigService.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        OnboardingService service = new OnboardingService(mapper, configService);

        Map<String, Object> result = service.seedDemo();

        assertEquals(3, result.get("inserted"));
        assertEquals(3, result.get("totalDemo"));
        assertEquals(true, result.get("demoSeeded"));
        verify(mapper, times(3)).insert(any(BizCustomer.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(configService).batchUpdate(captor.capture());
        assertEquals("onboarding.demoSeeded", captor.getValue().get(0).getConfigKey());
        assertEquals("true", captor.getValue().get(0).getConfigValue());
    }

    @Test
    void seedDemo_skipsExistingCodes() {
        BizCustomerMapper mapper = mock(BizCustomerMapper.class);
        SysConfigService configService = mock(SysConfigService.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        OnboardingService service = new OnboardingService(mapper, configService);

        Map<String, Object> result = service.seedDemo();

        assertEquals(0, result.get("inserted"));
        verify(mapper, never()).insert(any(BizCustomer.class));
        verify(configService).batchUpdate(any());
    }
}
