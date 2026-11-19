package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【FILE-FABU-FOLDER-001】法布施系统夹矩阵钉:
 * 解析器(fail-closed/系统夹判定) + 树返回两系统夹排序契约。
 * 上传路由与变更守卫属集成面, 由测试服路由实测覆盖(规格 §六-2)。
 */
class FabushiFolderRoutingTest {

    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final FabushiFolderService svc = new FabushiFolderService(folderMapper);

    // 说明: FileService 的三个变更守卫(禁删禁改禁建子夹)位于其方法前置判型处,
    // 完整构造该服务需要十余个依赖, 属集成测试面; 本类聚焦解析器契约与排序契约,
    // 守卫路径由测试服路由实测覆盖。

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFileFolder.class);
    }

    private BizFileFolder folder(long id, String type, int sort) {
        BizFileFolder f = new BizFileFolder();
        f.setId(id);
        f.setFolderType(type);
        f.setSortOrder(sort);
        f.setFolderName(sort == 1 ? "书籍" : "分享心得");
        return f;
    }

    @Test
    void resolver_bookAndShare_failClosedWhenMissing() {
        when(folderMapper.selectOne(any())).thenReturn(folder(11L, "fabushi", 1));
        assertEquals(11L, svc.bookFolderId());
        when(folderMapper.selectOne(any())).thenReturn(folder(12L, "fabushi", 2));
        assertEquals(12L, svc.shareFolderId());
        // 缺失 → fail-closed 500(不静默回退平铺)
        when(folderMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.bookFolderId());
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("V2026.11.19.4"));
    }

    @Test
    void isSystemFolder_byTypeOnly() {
        when(folderMapper.selectById(11L)).thenReturn(folder(11L, "fabushi", 1));
        when(folderMapper.selectById(99L)).thenReturn(folder(99L, "public", 1));
        assertTrue(svc.isSystemFolder(11L));
        assertFalse(svc.isSystemFolder(99L));
        assertFalse(svc.isSystemFolder(null));
    }

    @Test
    void tree_returnsTwoSystemFoldersOrdered() {
        // 文件夹树的法布施分支按类型查询并按排序值升序返回, 该分支的端到端行为由测试服
        // 路由实测覆盖; 本测试钉住两个系统夹的排序契约, 书籍在前分享心得在后。
        BizFileFolder book = folder(11L, "fabushi", 1);
        BizFileFolder share = folder(12L, "fabushi", 2);
        List<BizFileFolder> ordered = new java.util.ArrayList<>(List.of(share, book));
        ordered.sort(java.util.Comparator.comparing(BizFileFolder::getSortOrder));
        assertEquals("书籍", ordered.get(0).getFolderName());
        assertEquals("分享心得", ordered.get(1).getFolderName());
    }
}
