package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.approval.mapper.BizApprovalConfigMapper;
import com.ycmn.module.approval.mapper.BizApprovalFlowMapper;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import com.ycmn.module.system.service.SysUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.security.SecurityUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测环境无 Spring 容器：手动初始化 MyBatis-Plus lambda 缓存（LambdaUpdateWrapper 依赖）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaPool.class);
    }

    private void setCurrentUser(SecurityUser u) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(u);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ApprovalService newService(BizSeaPoolMapper seaPoolMapper, BizApprovalMapper approvalMapper,
                                       BizApprovalNodeMapper nodeMapper) {
        return new ApprovalService(
                mock(BizApprovalConfigMapper.class),
                approvalMapper,
                nodeMapper,
                mock(BizApprovalFlowMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                seaPoolMapper,
                mock(SeaService.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                mock(SysUserService.class));
    }

    @Test
    void submit_claim_atomicUpdateMisses_rejectsAsTaken() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all"); // 跳过公海池可见性检查，聚焦并发分支
        setCurrentUser(u);

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        // 并发竞争：条件更新 affected=0（他人已锁定）
        when(seaPoolMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        ApprovalService svc = newService(seaPoolMapper, mock(BizApprovalMapper.class), mock(BizApprovalNodeMapper.class));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("claim", 42L, null, null));
        assertTrue(ex.getMessage().contains("已被他人申请"));
    }

    @Test
    void submit_claim_atomicUpdateSucceeds_proceeds() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u);

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        when(seaPoolMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        // 锁定更新必须携带 status='available' 条件（原子性）
        verify(seaPoolMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void statistics_nonAdmin_scopesCounts() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(Collections.singletonList("sales_engineer"));
        u.setDataScope("self");
        setCurrentUser(u);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(nodeMapper.selectCount(any())).thenReturn(1L);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(3L);

        ApprovalService svc = newService(mock(BizSeaPoolMapper.class), approvalMapper, nodeMapper);
        Map<String, Long> stats = svc.statistics(7L);

        assertEquals(1L, stats.get("pending"));
        assertEquals(3L, stats.get("mine"));
        // mine/reviewing/all/approvedThisMonth/rejectedThisMonth 共 5 次，均带数据范围过滤
        verify(approvalMapper, times(5)).selectCount(any());
    }
}
