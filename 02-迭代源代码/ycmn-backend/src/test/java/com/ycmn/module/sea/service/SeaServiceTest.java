package com.ycmn.module.sea.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaConfigMapper;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

/**
 * 【修复 2026-08-09 复核 B1】all 数据范围用户也不得看到禁用池(status=0)的存量客户。
 * 回归保护: visiblePoolIdsOrNull 不再对 all 返回 null(不过滤),而是返回全部启用池 ID。
 */
class SeaServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测环境无 Spring 容器：手动初始化 MyBatis-Plus lambda 缓存(LambdaQueryWrapper 依赖)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaPool.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaConfig.class);
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
        BizSeaPool record = new BizSeaPool();
        record.setId(100L);
        record.setCustomerId(500L);
        record.setPoolId(10L); // 启用池
        record.setStatus("available");
        Page<BizSeaPool> pageResult = new Page<>(1, 15);
        pageResult.setRecords(List.of(record));
        pageResult.setTotal(1);
        when(poolMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(poolMapper.selectCount(any())).thenReturn(1L);

        CustomerService customerService = mock(CustomerService.class);
        BizCustomerMapper custMapper = customerMapper();
        SeaConfigService configService = new SeaConfigService(configMapper, poolMapper, deptMapper, userMapper);
        // SeaService 字段顺序: seaPoolMapper, seaConfigMapper, customerMapper, userMapper, deptMapper, seaConfigService, customerService
        SeaService seaService = new SeaService(poolMapper, configMapper, custMapper,
                userMapper, deptMapper, configService, customerService);

        // page() 内部: visiblePoolIdsOrNull() = [10](启用池), 禁用池 20 被排除
        IPage<BizSeaPool> result = seaService.page(1, 15, null, null, null, null, null, null, "available");
        assertEquals(1, result.getRecords().size());
        assertEquals(10L, result.getRecords().get(0).getPoolId());
        // 关键断言: 查询条件含 pool_id IN(启用池过滤生效), 且参数值只有启用池 id 10、无禁用池 20。
        // 修复前 all 用户 visiblePoolIdsOrNull 返回 null → SQL 无 pool_id IN 条件(禁用池也可见)。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<BizSeaPool>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(poolMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getExpression().getSqlSegment();
        assertEquals(true, sql.contains("pool_id IN"), "all 用户也应受启用池过滤(含 pool_id IN): " + sql);
        // 参数值: IN 列表应只含启用池 id 10
        java.util.Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        String joined = String.valueOf(params.values());
        assertEquals(true, joined.contains("10"), "IN 参数应含启用池 id 10: " + joined);
        assertEquals(false, joined.contains("20"), "IN 参数不应含禁用池 id 20: " + joined);
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

    // ===== deletePool 消息统一（2026-08-14 防探测: 不存在/状态不符/无权限统一文案） =====

    private SeaService newSeaService(BizSeaPoolMapper poolMapper, SeaConfigService configService) {
        return new SeaService(poolMapper, mock(BizSeaConfigMapper.class), customerMapper(),
                mock(SysUserMapper.class), mock(SysDeptMapper.class), configService, mock(CustomerService.class));
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
}
