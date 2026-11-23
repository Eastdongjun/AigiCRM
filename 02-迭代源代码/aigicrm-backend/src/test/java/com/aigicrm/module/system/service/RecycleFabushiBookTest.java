package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【D15/回收站 2026-11-23】法布施书目回收闸门：恢复防同名重复；彻底删除有关联即阻断。
 * 只覆盖决策分支，不重复 RecycleServiceTest 客户/人员既有回归。
 */
class RecycleFabushiBookTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RecycleService service = new RecycleService(
            mock(BizCustomerMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
            mock(SysLogMapper.class), mock(BizFileMapper.class), mock(BizFileFolderMapper.class),
            mock(FileService.class), jdbc, mock(PermissionCacheService.class));

    private void softDeleted(long id) {
        when(jdbc.queryForObject(contains("SELECT deleted FROM biz_fabushi_book"), eq(Integer.class), eq(id)))
                .thenReturn(1);
    }

    @Test
    void restoreBook_sameNameActive_rejected() {
        softDeleted(3L);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM biz_fabushi_book WHERE title"),
                eq(Long.class), eq(3L), eq(3L))).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.restoreBook(3L));

        assertTrue(e.getMessage().contains("同名活跃书目"));
    }

    @Test
    void hardDeleteBook_withSectionRelation_blocked() {
        softDeleted(3L);
        when(jdbc.queryForObject(contains("biz_fabushi_section"), eq(Long.class), eq(3L))).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.hardDeleteBook(3L));

        assertTrue(e.getMessage().contains("章节 2"));
    }

    @Test
    void restoreBook_notInBin_rejected() {
        when(jdbc.queryForObject(contains("SELECT deleted FROM biz_fabushi_book"), eq(Integer.class), eq(99L)))
                .thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.restoreBook(99L));

        assertTrue(e.getMessage().contains("不在回收站"));
    }

    @Test
    void recycleBinLedgerIsBestEffort_wiredThroughReflection() {
        // 台账注入为可选字段：未注入时恢复/彻底删除不得因台账缺失而 NPE。
        ReflectionTestUtils.setField(service, "recycleBinService", null);
        softDeleted(7L);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM biz_fabushi_book WHERE title"),
                eq(Long.class), eq(7L), eq(7L))).thenReturn(0L);
        when(jdbc.update(contains("UPDATE biz_fabushi_book SET deleted = 0"), eq(7L))).thenReturn(1);

        String message = service.restoreBook(7L);

        assertTrue(message.contains("已恢复"));
    }
}
