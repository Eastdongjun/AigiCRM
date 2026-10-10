package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【用户裁决 2026-10-10】编辑历史文件可移除单个文件（上传错了需要修改）：
 * removeFile 服务语义——行锁读(FOR UPDATE 串行化并发删除)、槽位数组剔除该文件ID、显式 SET 写回（删空槽位必须置 NULL，
 * updateById 默认策略会跳过 null 造成假成功）、五槽合计移除后须 ≥1、重算积分+盖修改人章。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveRemoveFileTest {

    @Mock
    BizIncentiveCategoryMapper categoryMapper;
    @Mock
    BizIncentiveRecordMapper recordMapper;
    @Mock
    SysUserMapper userMapper;
    @Mock
    BizFileMapper fileMapper;
    @Mock
    BizCustomerMapper customerMapper;
    @Mock
    SysDeptMapper deptMapper;
    @Mock
    OrgScopeResolver scopeResolver;
    @Mock
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 纯 mock 单测，手动注册 TableInfo 供 LambdaUpdateWrapper 渲染 SQL（自足 init，防蹭缓存单跑挂） */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizIncentiveRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizFile.class);
    }

    private IncentiveService newService() {
        return new IncentiveService(categoryMapper, recordMapper, userMapper,
                fileMapper, customerMapper, deptMapper, scopeResolver, jdbc);
    }

    private void stubUser(MockedStatic<SecurityUtils> m) {
        m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
    }

    /** 记录：安装前两份 + 安装中一份，visit 类（基础分 10） */
    private BizIncentiveRecord recordWithFiles() {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setId(5L);
        r.setUserId(9L);
        r.setCategoryId(1L);
        r.setFilePreId("[100,101]");
        r.setFileMidId("[200]");
        return r;
    }

    private void stubCategory() {
        BizIncentiveCategory cat = new BizIncentiveCategory();
        cat.setId(1L);
        cat.setCategoryCode("visit");
        cat.setScore(10);
        when(categoryMapper.selectById(1L)).thenReturn(cat);
    }

    /** 正常移除：pre 槽 [100,101] 移除 100 → 显式 SET file_pre_id='[101]' + score + updated_by */
    @Test
    void removeFile_ok_writesSlotAndAudit() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(recordWithFiles());
            stubCategory();

            newService().removeFile(5L, "pre", 100L);

            @SuppressWarnings("rawtypes")
            ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(recordMapper).update(any(), captor.capture());
            LambdaUpdateWrapper<?> uw = (LambdaUpdateWrapper<?>) captor.getValue();
            String sqlSet = uw.getSqlSet();
            assertTrue(sqlSet.contains("file_pre_id"), "必须显式写回被改的槽位列: " + sqlSet);
            assertTrue(sqlSet.contains("score"), "必须重算积分: " + sqlSet);
            assertTrue(sqlSet.contains("updated_by"), "必须盖修改人章: " + sqlSet);
            assertTrue(uw.getParamNameValuePairs().containsValue("[101]"),
                    "pre 槽应只剩 101: " + uw.getParamNameValuePairs());
        }
    }

    /** 删空槽位：pre=[100] 且 mid=[200]，移除 pre 的 100 → file_pre_id 必须显式置 NULL（假成功回归钉） */
    @Test
    void removeFile_emptySlot_setsNullExplicitly() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            BizIncentiveRecord r = recordWithFiles();
            r.setFilePreId("[100]");
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(r);
            stubCategory();

            newService().removeFile(5L, "pre", 100L);

            @SuppressWarnings("rawtypes")
            ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(recordMapper).update(any(), captor.capture());
            LambdaUpdateWrapper<?> uw = (LambdaUpdateWrapper<?>) captor.getValue();
            assertTrue(uw.getParamNameValuePairs().containsValue(null),
                    "空槽位必须显式 SET NULL，否则 updateById/默认策略跳过 null 造成假成功: "
                            + uw.getParamNameValuePairs());
        }
    }

    /** 最后一份文件守卫：全记录仅剩一份时拒绝移除（防空壳记录） */
    @Test
    void removeFile_lastFile_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            BizIncentiveRecord r = recordWithFiles();
            r.setFilePreId("[100]");
            r.setFileMidId(null);
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(r);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().removeFile(5L, "pre", 100L));
            assertTrue(ex.getMessage().contains("至少须保留一份文件"), ex.getMessage());
            verify(recordMapper, never()).update(any(), any());
        }
    }

    /** 无效槽位拒绝 */
    @Test
    void removeFile_invalidSlot_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(recordWithFiles());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().removeFile(5L, "main", 100L));
            assertTrue(ex.getMessage().contains("无效槽位"), ex.getMessage());
        }
    }

    /** 文件不在该槽位拒绝 */
    @Test
    void removeFile_fileNotInSlot_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(recordWithFiles());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().removeFile(5L, "pre", 999L));
            assertTrue(ex.getMessage().contains("不在此记录的该槽位"), ex.getMessage());
        }
    }

    /** 记录不存在 → 404 */
    @Test
    void removeFile_recordNotFound_404() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            when(recordMapper.selectByIdForUpdate(404L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().removeFile(404L, "pre", 100L));
            assertEquals(404, ex.getCode());
        }
    }

    /** 【工作流复检修复 2026-10-10】PUT 空串清空全部槽位须被拒：与 removeFile 同口径五槽≥1 */
    @Test
    void updateRecord_clearAllSlots_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            BizIncentiveRecord existing = recordWithFiles();
            when(recordMapper.selectByIdForUpdate(5L)).thenReturn(existing);

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setFilePreId("");
            update.setFileMidId("");
            update.setFilePostId("");
            update.setFileTrackId("");
            update.setFileId("");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().updateRecord(5L, update));
            assertTrue(ex.getMessage().contains("至少须保留一份文件"), ex.getMessage());
            verify(recordMapper, never()).updateById(any(BizIncentiveRecord.class));
        }
    }
}
