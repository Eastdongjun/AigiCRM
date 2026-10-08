package com.aigicrm.module.common.service;

import com.aigicrm.module.common.entity.BizFormDraft;
import com.aigicrm.module.common.mapper.BizFormDraftMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P2 2026-10-08 复审修复】草稿乐观锁原子化回归钉。
 * 旧实现先查版本再 updateById——两请求同读同版本都过判, 后写覆盖先写且不返冲突。
 * 修复: WHERE version=? 条件更新(参数化), 影响行数 0 = 并发写者已胜出 → 409 冲突。
 */
class FormDraftOptimisticLockTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizFormDraft.class);
    }

    private final BizFormDraftMapper draftMapper = mock(BizFormDraftMapper.class);
    private final FormDraftService svc = new FormDraftService(draftMapper);

    private static BizFormDraft serverDraft(int version) {
        BizFormDraft d = new BizFormDraft();
        d.setId(11L);
        d.setUserId(7L);
        d.setGroupId(1L);
        d.setDraftType("customer_form");
        d.setDraftData("{\"v\":old}");
        d.setVersion(version);
        return d;
    }

    @Test
    void concurrentWriterWon_conditionalUpdateReturnsZero_conflictThrown() {
        // 两请求同读 version=5: 甲先条件更新成功(→6); 乙的 WHERE version=5 命中 0 行 = 已被甲覆盖
        when(draftMapper.selectOne(any())).thenReturn(serverDraft(5));
        when(draftMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        // 冲突时回读服务器当前草稿(供客户端"使用服务器版本"选择)
        when(draftMapper.selectById(11L)).thenReturn(serverDraft(6));

        FormDraftService.ConflictException ex = assertThrows(FormDraftService.ConflictException.class,
                () -> svc.saveDraft("customer_form", 7L, 1L, "{\"v\":mine}", 5));
        assertEquals(6, ex.getServerDraft().getVersion(), "冲突异常须携带服务器最新草稿");
    }

    @Test
    void conditionalUpdateUsesVersionPredicate() {
        // 钉住 WHERE 语义: 条件更新必须带 version = 客户端版本(参数化), 不能退回裸 updateById
        when(draftMapper.selectOne(any())).thenReturn(serverDraft(5));
        when(draftMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        BizFormDraft saved = svc.saveDraft("customer_form", 7L, 1L, "{\"v\":mine}", 5);
        assertEquals(6, saved.getVersion());
        assertEquals("{\"v\":mine}", saved.getDraftData());
        verify(draftMapper, never()).updateById(any(BizFormDraft.class));
    }

    @Test
    void firstSave_insertPath_noConditionalUpdate() {
        when(draftMapper.selectOne(any())).thenReturn(null);
        when(draftMapper.insert(any(BizFormDraft.class))).thenReturn(1);
        BizFormDraft saved = svc.saveDraft("customer_form", 7L, 1L, "{\"a\":1}", 0);
        assertNotNull(saved);
        assertEquals(1, saved.getVersion());
        verify(draftMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
