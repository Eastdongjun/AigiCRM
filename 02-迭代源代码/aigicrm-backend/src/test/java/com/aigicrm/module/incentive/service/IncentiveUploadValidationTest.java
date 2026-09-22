package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizCustomer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激励上传入参校验与归属限制（2026-08-16 审查修复）：
 * addRecord 必须带 存在且有效的分类/客户、至少一个文件槽、引用的文件属于当前用户；
 * updateRecord 的归属客户 customerId 仅 admin/incentive:manage 可变更，本人保持原值。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveUploadValidationTest {

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

    /** 纯 mock 单测，手动注册 TableInfo 供 LambdaQueryWrapper 渲染 SQL */
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

    /** 合法入参：分类+客户+一个安装前文件，visit 类积分 10 */
    private BizIncentiveRecord validRecord() {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setCategoryId(1L);
        r.setCustomerId(10L);
        r.setFilePreId("[100]");
        return r;
    }

    private void stubValidDeps() {
        BizIncentiveCategory cat = new BizIncentiveCategory();
        cat.setId(1L);
        cat.setCategoryCode("visit");
        cat.setScore(10);
        when(categoryMapper.selectById(1L)).thenReturn(cat);
        when(customerMapper.selectById(10L)).thenReturn(new BizCustomer());
        when(fileMapper.selectCount(any())).thenReturn(1L);
    }

    private void stubNotManager(MockedStatic<SecurityUtils> m) {
        m.when(SecurityUtils::isAdmin).thenReturn(false);
        m.when(() -> SecurityUtils.hasAuthority(anyString())).thenReturn(false);
        m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
    }

    // ==================== addRecord 入参校验 ====================

    /** 缺分类 → 拒绝 */
    @Test
    void addRecord_missingCategory_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            BizIncentiveRecord r = validRecord();
            r.setCategoryId(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(r));
            assertEquals("请选择激励分类", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 分类不存在 → 拒绝 */
    @Test
    void addRecord_unknownCategory_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            when(categoryMapper.selectById(1L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(validRecord()));
            assertEquals("激励分类不存在或已删除", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 缺客户 → 拒绝 */
    @Test
    void addRecord_missingCustomer_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());
            BizIncentiveRecord r = validRecord();
            r.setCustomerId(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(r));
            assertEquals("请选择关联客户", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 客户不存在 → 拒绝 */
    @Test
    void addRecord_unknownCustomer_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());
            when(customerMapper.selectById(10L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(validRecord()));
            assertEquals("关联客户不存在或已删除", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 无任何文件 → 拒绝 */
    @Test
    void addRecord_noFile_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());
            when(customerMapper.selectById(10L)).thenReturn(new BizCustomer());
            BizIncentiveRecord r = validRecord();
            r.setFilePreId(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(r));
            assertEquals("请至少上传一个文件", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 引用文件不属于当前用户（selectCount=0）→ 拒绝 */
    @Test
    void addRecord_fileNotOwned_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());
            when(customerMapper.selectById(10L)).thenReturn(new BizCustomer());
            when(fileMapper.selectCount(any())).thenReturn(0L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().addRecord(validRecord()));
            assertEquals("包含无效文件引用，请重新上传", ex.getMessage());
            verify(recordMapper, never()).insert(any(BizIncentiveRecord.class));
        }
    }

    /** 合法入参 → 落库：userId 强制当前用户、积分按分类计 */
    @Test
    void addRecord_valid_insertsWithUserIdAndScore() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            stubValidDeps();
            newService().addRecord(validRecord());

            ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
            verify(recordMapper).insert(cap.capture());
            assertEquals(2L, cap.getValue().getUserId(), "userId 必须强制为当前登录人");
            assertEquals(10, cap.getValue().getScore(), "visit 任一槽有文件应得 base 分");
        }
    }

    // ==================== updateRecord 归属客户限制 ====================

    /** 本人(无 manage 权限)改 customerId 与现有不同 → 403 */
    @Test
    void updateRecord_ownerChangeCustomer_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            // 授权表无记录（默认 can_edit 关）——canEdit 判定走表
            when(jdbc.queryForMap(anyString(), org.mockito.ArgumentMatchers.eq(2L)))
                    .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
            BizIncentiveRecord existing = new BizIncentiveRecord();
            existing.setId(1L);
            existing.setUserId(2L);
            existing.setCategoryId(1L);
            existing.setCustomerId(10L);
            when(recordMapper.selectById(1L)).thenReturn(existing);

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setCustomerId(99L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().updateRecord(1L, update));
            assertEquals(403, ex.getCode());
            assertEquals("无权修改记录归属客户", ex.getMessage());
            verify(recordMapper, never()).updateById(any(BizIncentiveRecord.class));
        }
    }

    /** 本人改 customerId 传原值(前端编辑回传场景) → 正常合并落库 */
    @Test
    void updateRecord_ownerSameCustomer_allowed() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubNotManager(m);
            BizIncentiveRecord existing = new BizIncentiveRecord();
            existing.setId(1L);
            existing.setUserId(2L);
            existing.setCategoryId(1L);
            existing.setCustomerId(10L);
            when(recordMapper.selectById(1L)).thenReturn(existing);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setCustomerId(10L);
            newService().updateRecord(1L, update);

            ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
            verify(recordMapper).updateById(cap.capture());
            assertEquals(10L, cap.getValue().getCustomerId(), "同值客户应保持原值");
        }
    }

    /** admin 改他人记录归属客户 → 允许 */
    @Test
    void updateRecord_adminChangeCustomer_allowed() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BizIncentiveRecord existing = new BizIncentiveRecord();
            existing.setId(1L);
            existing.setUserId(2L);
            existing.setCategoryId(1L);
            existing.setCustomerId(10L);
            when(recordMapper.selectById(1L)).thenReturn(existing);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setCustomerId(99L);
            newService().updateRecord(1L, update);

            ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
            verify(recordMapper).updateById(cap.capture());
            assertEquals(99L, cap.getValue().getCustomerId(), "admin 可变更归属客户");
        }
    }

    // ==================== updateRecord 文件引用归属（2026-09-22 真机UAT修复回归钉） ====================

    /** 编辑他人记录、文件槽原样回传原作者存量文件（无新增引用）→ 放行。
     *  此前对合并后全量校验 uploader==当前编辑人，admin/can_edit 编辑任何他人记录都会被
     *  "包含无效文件引用"整体误拒（实测 record 266：原作者文件 869，admin 编辑被拒）。 */
    @Test
    void updateRecord_keepOriginalAuthorFiles_allowed() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BizIncentiveRecord existing = new BizIncentiveRecord();
            existing.setId(1L);
            existing.setUserId(52L);
            existing.setCategoryId(1L);
            existing.setCustomerId(10L);
            existing.setFilePreId("[869]");
            when(recordMapper.selectById(1L)).thenReturn(existing);
            when(categoryMapper.selectById(1L)).thenReturn(new BizIncentiveCategory());

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setFilePreId("[869]");
            newService().updateRecord(1L, update);

            verify(fileMapper, never()).selectCount(any());
            ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
            verify(recordMapper).updateById(cap.capture());
            assertEquals("[869]", cap.getValue().getFilePreId(), "存量引用应原样保留");
        }
    }

    /** 编辑时本次新引入他人/不存在文件 → 仍然拒绝（防"填他人文件ID保分"不放松） */
    @Test
    void updateRecord_newForeignFile_rejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BizIncentiveRecord existing = new BizIncentiveRecord();
            existing.setId(1L);
            existing.setUserId(52L);
            existing.setCategoryId(1L);
            existing.setCustomerId(10L);
            existing.setFilePreId("[869]");
            when(recordMapper.selectById(1L)).thenReturn(existing);
            when(fileMapper.selectCount(any())).thenReturn(0L);

            BizIncentiveRecord update = new BizIncentiveRecord();
            update.setFilePreId("[869,999]");
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> newService().updateRecord(1L, update));
            assertEquals("包含无效文件引用，请重新上传", ex.getMessage());
            verify(recordMapper, never()).updateById(any(BizIncentiveRecord.class));
        }
    }
}
