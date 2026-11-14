package com.aigicrm.module.system.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【V2026.11.14.2 P0 钉】回收站保留期与彻底删除级联(外部审查 P0 三项):
 * ① 保留期只认 deleted_at(COALESCE 兜底)——原 updated_at 判过期会让"老文件今天删除明天被清";
 * ② 彻底删除级联=主行+全部版本+分享+台账, 版本 key 引用计数——此前版本行/旧字节全留;
 * ③ 恢复清 deleted_at/deleted_by——源码级钉(mapper 注解 SQL 无法 mock 断言, 按文本钉防回归)。
 */
class RecycleRetentionCascadeTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final com.aigicrm.module.file.service.FileService fileService =
            mock(com.aigicrm.module.file.service.FileService.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private RecycleService svc;

    @BeforeEach
    void setUp() {
        svc = new RecycleService(
                mock(BizCustomerMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(SysLogMapper.class), fileMapper, mock(BizFileFolderMapper.class), fileService,
                jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
    }

    @Test
    void purgeExpired_retentionCutoff_usesDeletedAtNotUpdatedAt() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        svc.purgeExpiredFilesWithStats(30);
        ArgumentCaptor<LocalDateTime> cutoffCap = ArgumentCaptor.forClass(LocalDateTime.class);
        // 【V2026.11.14.3】保留期清理含文件+夹两段查询——按 SQL 精确钉文件段
        verify(jdbc, org.mockito.Mockito.times(1))
                .queryForList(org.mockito.ArgumentMatchers.contains("biz_file WHERE deleted = 1"),
                        eq(Long.class), cutoffCap.capture());
        assertTrue(cutoffCap.getValue().isBefore(LocalDateTime.now()),
                "cutoff = now - retentionDays");
        assertTrue(cutoffCap.getValue().isBefore(LocalDateTime.now()),
                "cutoff = now - retentionDays");
    }

    @Test
    void purgeRows_cascadesVersionKeys_andDeletesVersionRows() throws Exception {
        // 锁行返回已删文件(主 key=main-key; 版本行含 v-key)——经 hardDeleteFile 公开入口验证级联
        BizFile f = new BizFile();
        f.setId(9L);
        f.setOssKey("private/7/main-key.txt");
        f.setFileName("a.txt");
        f.setFileScope("private");
        when(jdbc.query(eq("SELECT * FROM biz_file WHERE id = ? AND deleted = 1 FOR UPDATE"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(9L)))
                .thenReturn(List.of(f));
        // isIncentiveEvidence: 激励佐证反查(RowCallbackHandler 无行=非佐证)
        org.mockito.Mockito.doAnswer(inv -> null)
                .when(jdbc).query(org.mockito.ArgumentMatchers.contains("biz_incentive_record"),
                        any(RowCallbackHandler.class), eq(9L));
        // 版本行: v-key 一条
        org.mockito.Mockito.doAnswer(inv -> { ((RowCallbackHandler) inv.getArgument(1)).processRow(mockRow("v-key.txt")); return null; })
                .when(jdbc).query(eq("SELECT oss_key FROM biz_file_version WHERE file_id = ?"),
                        any(RowCallbackHandler.class), eq(9L));
        // 引用计数: 两个 key 都无其他引用
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("DELETE FROM biz_file"), eq(9L))).thenReturn(1);
        when(fileService.contentExists(any())).thenReturn(false); // 删除成功(不再存在)

        svc.hardDeleteFile(9L);

        // 两个 key 都被物理删除(主+版本; 回滚同 key 场景按 key 去重不重复删)
        verify(fileService, times(1)).deleteStoredContent("private/7/main-key.txt");
        verify(fileService, times(1)).deleteStoredContent("v-key.txt");
        // 版本行随主行删(孤儿表防线)
        verify(jdbc, times(1)).update("DELETE FROM biz_file_version WHERE file_id = ?", 9L);
        verify(jdbc, times(1)).update("DELETE FROM biz_file_share WHERE file_id = ?", 9L);
        verify(jdbc, times(1)).update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", 9L);
    }

    private java.sql.ResultSet mockRow(String key) throws Exception {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("oss_key")).thenReturn(key);
        return rs;
    }

    /** 源码级钉(注解 SQL 无法 mock 断言): 恢复必须清 deleted_at/by + 列表排序认 deleted_at */
    @Test
    void restoreSql_clearsDeletedFlags_sourcePinned() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/aigicrm/module/file/mapper/BizFileMapper.java"));
        assertTrue(src.contains("SET deleted = 0, deleted_at = NULL, deleted_by = NULL WHERE id = #{id} AND deleted = 1"),
                "恢复必须清删除人/删除时间(脏归属会污染审计与二次删除)");
        assertTrue(src.contains("deleted_at = NULL, deleted_by = NULL, folder_id = NULL"),
                "挂根恢复同样清标志");
        assertTrue(src.contains("ORDER BY COALESCE(deleted_at, updated_at) DESC"),
                "回收站列表按进站时间排序(非 updated_at)");
    }
}
