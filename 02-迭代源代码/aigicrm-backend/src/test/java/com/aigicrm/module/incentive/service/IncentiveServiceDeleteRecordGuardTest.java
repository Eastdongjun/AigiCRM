package com.aigicrm.module.incentive.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.incentive.service.support.IncentiveServiceFixture;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RecycleBinService;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 删除激励记录时的原生 SQL 守卫（2026-11-25，配合 check-native-sql-tenant-scope.sh 的归类）。
 *
 * deleteRecord 里的三条 SQL 走 JdbcTemplate，按主键直接改库，不经过 MyBatis-Plus 的租户拦截器。
 * 所以往下走之前必须先用 Mapper 取一次记录（Mapper 走拦截器链）：取不到就停，一条原生 SQL 都不发。
 * 这样无论调用方有没有做过归属校验，都不可能凭一个 id 改到当前租户之外的记录。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveServiceDeleteRecordGuardTest {

    @Mock
    OrgScopeResolver resolver;
    @Mock
    BizIncentiveRecordMapper recordMapper;
    @Mock
    JdbcTemplate jdbc;
    @Captor
    ArgumentCaptor<Map<String, Object>> snapshot;

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private IncentiveService service() {
        return IncentiveServiceFixture.create(
                mock(BizIncentiveCategoryMapper.class),
                recordMapper,
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysDeptMapper.class),
                resolver,
                null,
                jdbc);
    }

    private void loginAs(long userId) {
        SecurityUser user = new SecurityUser();
        user.setUserId(userId);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void idThatDoesNotResolveIsRejectedBeforeAnyRawSql() {
        // Mapper 取不到：记录不存在、已在回收站，或属于别的租户
        when(recordMapper.selectById(9L)).thenReturn(null);

        IncentiveService service = service();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteRecord(9L));

        assertEquals(404, ex.getCode());
        verifyNoInteractions(jdbc);
        verify(recordMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void resolvedIdIsSoftDeletedAndReadRowsCleared() {
        when(recordMapper.selectById(5L)).thenReturn(new BizIncentiveRecord());
        when(jdbc.queryForList(anyString(), eq(5L))).thenReturn(List.of());
        loginAs(7L);

        service().deleteRecord(5L);

        verify(jdbc).queryForList("SELECT user_id FROM biz_incentive_read WHERE record_id=?", 5L);
        verify(jdbc).update(startsWith("UPDATE biz_incentive_record SET deleted_at=NOW()"), eq(7L), eq("用户删除"), eq(5L));
        verify(jdbc).update("DELETE FROM biz_incentive_read WHERE record_id = ?", 5L);
        verify(recordMapper).deleteById(5L);
    }

    @Test
    void readersAreSnapshottedForRecycleBinRestore() {
        when(recordMapper.selectById(5L)).thenReturn(new BizIncentiveRecord());
        List<Map<String, Object>> readers = List.of(Map.of("user_id", 3L), Map.of("user_id", 4L));
        when(jdbc.queryForList(anyString(), eq(5L))).thenReturn(readers);
        RecycleBinService recycleBin = mock(RecycleBinService.class);
        IncentiveService svc = service();
        ReflectionTestUtils.setField(svc, "recycleBinService", recycleBin);

        svc.deleteRecord(5L);

        verify(recycleBin).record(eq("incentive"), eq(5L), snapshot.capture(), eq("用户删除"), eq("user"));
        assertEquals(5L, snapshot.getValue().get("id"));
        assertSame(readers, snapshot.getValue().get("readUsers"));
    }
}
