package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【A2 §2.5】双写服务单测（QC 复审结论 §二③ 清单）：
 * ①改配置后 legacy_ref 不被改成新行（更新原行、保留 source）②动作集变化删旧插新
 * ③删除旧行后 ftd:&lt;id&gt;: 全清 ④create 保留、rename/replace 归并 edit
 * ⑤映射与迁移草案逐口径一致（SPACE/blank 归一/scope_mode/scope_org_id=NULL/inherit）。
 */
class LegacyFileGrantSyncServiceTest {

    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFileGrant.class);
    }

    private final BizFileGrantMapper grantMapper = mock(BizFileGrantMapper.class);
    private final LegacyFileGrantSyncService svc = new LegacyFileGrantSyncService(grantMapper);

    private BizFolderTypeDefault old(long id, String folderType, String objectType, Long folderId,
                                     String scopeLevel, String perms, LocalDateTime expires) {
        BizFolderTypeDefault g = new BizFolderTypeDefault();
        g.setId(id);
        g.setFolderType(folderType);
        g.setObjectType(objectType);
        g.setFolderId(folderId);
        g.setScopeLevel(scopeLevel);
        g.setSubjectType("ROLE");
        g.setSubjectId(9L);
        g.setPerms(perms);
        g.setExpiresAt(expires);
        g.setCreatedBy(1L);
        return g;
    }

    @Test
    void insertMapsRowPerActionAndMergesEdit() {
        // 旧行 perms=view,upload,rename,replace,create → 4 个动作行（rename+replace 归并 edit 一行，create 保留）
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        svc.syncUpsert(old(160L, "public", "FILE", null, null,
                "view,upload,rename,replace,create", null));
        ArgumentCaptor<BizFileGrant> cap = ArgumentCaptor.forClass(BizFileGrant.class);
        verify(grantMapper, times(4)).insert(cap.capture());
        List<BizFileGrant> rows = cap.getAllValues();
        assertEquals(4, rows.size());
        // SPACE 行映射口径（与 V2026.11.18.2 逐项对齐）
        for (BizFileGrant g : rows) {
            assertEquals("SPACE", g.getObjectType());
            assertNull(g.getObjectId());
            assertEquals("public", g.getSpaceType());
            assertEquals("ROLE", g.getSubjectType());
            assertEquals(9L, g.getSubjectId());
            assertEquals("ALLOW", g.getEffect());
            assertEquals(0, g.getInherit(), "SPACE 行 inherit=0（B-2）");
            assertNull(g.getScopeOrgId(), "旧 scope_org_id 被旧引擎忽略，迁移置 NULL");
            assertEquals(FileGrantService.SCOPE_ANY, g.getScopeMode());
            assertNull(g.getScopeLevel());
            assertEquals("dual_write", g.getSource());
            assertTrue(g.getLegacyRef().startsWith("ftd:160:"));
        }
        // 动作集合 = view/upload/edit/create（rename/replace 归并）
        assertEquals(List.of("view", "upload", "edit", "create"),
                rows.stream().map(BizFileGrant::getAction).toList());
        assertEquals("ftd:160:view", rows.get(0).getLegacyRef());
        assertEquals("ftd:160:edit", rows.get(2).getLegacyRef());
    }

    @Test
    void relativeLevelKeptAndBlankFolderTypeNormalized() {
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        // scope_level=region → RELATIVE 保留；folder_type 空白 → space_type=NULL（B-3 归一）
        svc.syncUpsert(old(161L, " ", "FILE", null, "region", "view", null));
        ArgumentCaptor<BizFileGrant> cap = ArgumentCaptor.forClass(BizFileGrant.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals(FileGrantService.SCOPE_RELATIVE, cap.getValue().getScopeMode());
        assertEquals("region", cap.getValue().getScopeLevel());
        assertNull(cap.getValue().getSpaceType(), "blank folder_type 归一 NULL=任意空间");
    }

    @Test
    void folderTargetedRowMapsToFolderInherit1() {
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        svc.syncUpsert(old(162L, "public", "FILE", 7L, null, "view", LocalDateTime.now().plusDays(3)));
        ArgumentCaptor<BizFileGrant> cap = ArgumentCaptor.forClass(BizFileGrant.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals("FOLDER", cap.getValue().getObjectType());
        assertEquals(7L, cap.getValue().getObjectId());
        assertEquals(1, cap.getValue().getInherit());
        assertNull(cap.getValue().getSpaceType());
        assertNotNull(cap.getValue().getExpireAt());
    }

    @Test
    void existingRowIsUpdatedInPlaceKeepingSourceAndRef() {
        // 已有迁移行（source=legacy）：改配置后按行更新，legacy_ref/source 不漂移、不新插
        BizFileGrant legacy = new BizFileGrant();
        legacy.setId(900L);
        legacy.setLegacyRef("ftd:163:view");
        legacy.setSource("legacy");
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(legacy);
        svc.syncUpsert(old(163L, "public", "FILE", null, null, "view",
                LocalDateTime.now().plusDays(9)));
        verify(grantMapper, never()).insert(any(BizFileGrant.class));
        verify(grantMapper).update(any(), any(Wrapper.class));
        // legacy_ref 恒定（幂等键），source 保留：断言无 delete/insert、仅 update
        verify(grantMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void actionSetChangeDeletesStaleAndUpsertsNew() {
        // 上次双写有 view+edit 两行；本次 perms 只剩 download → view/edit 行删除，插 download 行
        BizFileGrant view = new BizFileGrant();
        view.setId(901L);
        view.setLegacyRef("ftd:164:view");
        view.setSource("dual_write");
        BizFileGrant edit = new BizFileGrant();
        edit.setId(902L);
        edit.setLegacyRef("ftd:164:edit");
        edit.setSource("legacy");
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(view, edit));
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        svc.syncUpsert(old(164L, "public", "FILE", null, null, "download", null));
        verify(grantMapper).deleteById(901L);
        verify(grantMapper).deleteById(902L);
        ArgumentCaptor<BizFileGrant> cap = ArgumentCaptor.forClass(BizFileGrant.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals("download", cap.getValue().getAction());
        assertEquals("ftd:164:download", cap.getValue().getLegacyRef());
    }

    @Test
    void syncDeleteClearsAllPrefixRows() {
        BizFileGrant a = new BizFileGrant();
        a.setId(910L);
        a.setLegacyRef("ftd:165:view");
        a.setSource("legacy");
        BizFileGrant b = new BizFileGrant();
        b.setId(911L);
        b.setLegacyRef("ftd:165:edit");
        b.setSource("dual_write");
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        svc.syncDelete(165L);
        verify(grantMapper).deleteById(910L);
        verify(grantMapper).deleteById(911L);
        verify(grantMapper, never()).insert(any(BizFileGrant.class));
    }

    @Test
    void editUmbrellaInPermsMergesToSingleEditRow() {
        // 'edit' 伞词（旧 ACL 转换行遗留）与 rename/replace 同归并：只有一行 edit
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(grantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        svc.syncUpsert(old(166L, "private", "FILE", null, null, "edit,rename", null));
        ArgumentCaptor<BizFileGrant> cap = ArgumentCaptor.forClass(BizFileGrant.class);
        verify(grantMapper, times(1)).insert(cap.capture());
        assertEquals("edit", cap.getValue().getAction());
    }
}
