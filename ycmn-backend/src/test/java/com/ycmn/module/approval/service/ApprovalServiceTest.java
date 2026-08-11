package com.ycmn.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.approval.entity.BizApproval;
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
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测环境无 Spring 容器：手动初始化 MyBatis-Plus lambda 缓存（LambdaUpdateWrapper 依赖）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaPool.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApproval.class);
    }

    private void setCurrentUser(SecurityUser u) {
        // 【修复 2026-08-09】9c6e72a 给捞取加了 sea:claim 方法内校验,测试用户需持该权限
        // (否则 submit("claim",...) 在到达业务分支前就抛"没有公海捞取权限")
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                u, null,
                java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("sea:claim"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("approval:view")));
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

    /** 回归：参与审批列表为空时不得生成 IN ()（原代码无空守卫导致 SQL 语法错误，生产已出故障） */
    @Test
    void statistics_emptyInvolved_noEmptyInClause() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(Collections.singletonList("sales_engineer"));
        u.setDataScope("self");
        setCurrentUser(u);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(Collections.emptyList()); // 无参与审批
        when(nodeMapper.selectCount(any())).thenReturn(1L);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(3L);

        ApprovalService svc = newService(mock(BizSeaPoolMapper.class), approvalMapper, nodeMapper);
        svc.statistics(7L);
        @SuppressWarnings("unchecked")

        ArgumentCaptor<LambdaQueryWrapper<BizApproval>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(approvalMapper, times(5)).selectCount(cap.capture());
        for (LambdaQueryWrapper<BizApproval> w : cap.getAllValues()) {
            String sql = w.getSqlSegment();
            assertTrue(sql == null || !sql.contains("IN ()"), "空参与列表不得生成 IN ()，实际: " + sql);
        }
    }
}
