package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 【回收站 2026-10-18】已删除客户/人员列表与恢复闸门 */
class RecycleServiceTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysLogMapper logMapper = mock(SysLogMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PermissionCacheService permissionCacheService = mock(PermissionCacheService.class);
    private final RecycleService service = new RecycleService(
            customerMapper, userMapper, deptMapper, logMapper, jdbc, permissionCacheService);

    private static BizCustomer deletedCustomer(Long ownerId) {
        BizCustomer c = new BizCustomer();
        c.setId(42L);
        c.setCustomerCode("SX-202607-999");
        c.setCustomerName("测试回收客户有限公司");
        c.setOwnerId(ownerId);
        c.setDeleted(1);
        return c;
    }

    private static SysUser deletedUser(Long id, String username, Long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setRealName("已删人员");
        u.setDeptId(deptId);
        u.setDeleted(1);
        u.setStatus(1);
        return u;
    }

    // ==================== 列表 ====================

    @Test
    void deletedCustomers_ownerNameFilledEvenIfOwnerDeleted() {
        when(customerMapper.selectDeletedPage(anyString(), eq(0), eq(20))).thenReturn(List.of(deletedCustomer(9L)));
        when(customerMapper.countDeleted(anyString())).thenReturn(1L);
        // selectNamesByIds 不过滤 deleted, 已删负责人也能回填姓名
        SysUser deletedOwner = deletedUser(9L, "s9", null);
        when(userMapper.selectNamesByIds(anyCollection())).thenReturn(List.of(deletedOwner));

        RecycleService.PageResult<RecycleService.DeletedCustomerRow> page =
                service.deletedCustomers(1, 20, "");

        assertEquals(1, page.total());
        assertEquals(1, page.records().size());
        assertEquals("已删人员", page.records().get(0).ownerName(), "已删负责人姓名仍应回填");
    }

    @Test
    void deletedUsers_deptNameFilled() {
        when(userMapper.selectDeletedPage(anyString(), eq(0), eq(20))).thenReturn(List.of(deletedUser(5L, "old5", 30L)));
        when(userMapper.countDeleted(anyString())).thenReturn(1L);
        SysDept dept = new SysDept();
        dept.setId(30L);
        dept.setDeptName("销售一部");
        when(deptMapper.selectBatchIds(anyCollection())).thenReturn(List.of(dept));

        RecycleService.PageResult<RecycleService.DeletedUserRow> page = service.deletedUsers(1, 20, "");

        assertEquals("销售一部", page.records().get(0).deptName());
    }

    // ==================== 客户恢复闸门 ====================

    @Test
    void restoreCustomer_success_writesAudit() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        SysUser owner = deletedUser(9L, "s9", null);
        owner.setDeleted(0);
        when(userMapper.selectByIdIncludingDeleted(9L)).thenReturn(owner);
        when(customerMapper.countActivePoolRows(42L)).thenReturn(0L);
        when(customerMapper.restoreDeleted(42L)).thenReturn(1);

        String message = service.restoreCustomer(42L);

        assertTrue(message.contains("已恢复"));
        verify(customerMapper).restoreDeleted(42L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void restoreCustomer_duplicateActiveName_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("同名活跃客户"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_ownerDeleted_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        when(userMapper.selectByIdIncludingDeleted(9L)).thenReturn(deletedUser(9L, "s9", null)); // deleted=1

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("原负责人已被删除"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_poolResidual_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(null));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        when(customerMapper.countActivePoolRows(42L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("公海池"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_notInRecycle_rejected() {
        BizCustomer alive = deletedCustomer(null);
        alive.setDeleted(0);
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(alive);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("不在回收站"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    // ==================== 人员恢复闸门 ====================

    @Test
    void restoreUser_success_bumpsPermVersionAndEvictsCache() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(0L);
        SysDept dept = new SysDept();
        dept.setId(30L);
        when(deptMapper.selectById(30L)).thenReturn(dept);
        when(userMapper.restoreDeleted(5L)).thenReturn(1);

        String message = service.restoreUser(5L);

        assertTrue(message.contains("重新分配角色"), "实际: " + message);
        verify(jdbc).update(anyString(), eq(5L));
        verify(permissionCacheService).evict(5L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void restoreUser_usernameTaken_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreUser(5L));

        assertTrue(ex.getMessage().contains("已被活跃用户占用"), "实际: " + ex.getMessage());
        verify(userMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreUser_deptMissing_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(0L);
        when(deptMapper.selectById(30L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreUser(5L));

        assertTrue(ex.getMessage().contains("原部门已不存在"), "实际: " + ex.getMessage());
        verify(userMapper, never()).restoreDeleted(anyLong());
    }
}
