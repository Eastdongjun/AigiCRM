package com.aigicrm.module.file.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 【FILE-FABU-FOLDER-003 2026-11-20】分夹后法布施列表查询源码契约钉:
 * applyFabushiVisibility 不得再含"带 folderId 即空"短路(分夹前旧假设, 点夹列表 0 行根因)。
 * 行为级验证(API ?scope=fabushi&folderId=系统夹 → total=夹内数)由 QC 测试服复测覆盖
 * ——FileService 构造依赖 15+, 集成面不在此重复(与 PermissionMatrixTest 源码级契约同族先例)。
 */
class FabushiFolderQueryTest {

    @Test
    void folderFilterNoLongerShortCircuitsEmpty() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/aigicrm/module/file/service/FileQueryService.java"));
        int at = src.indexOf("private boolean applyFabushiVisibility");
        assertNotEquals(-1, at, "FileQueryService.applyFabushiVisibility 方法应存在");
        String method = src.substring(at, Math.min(src.length(), at + 900));
        assertFalse(method.contains("if (folderId != null) return false"),
                "分夹后不得再有'带 folderId 即空'旧假设(FILE-FABU-FOLDER-003)");
        // 修复后语义: folderId 由 pageFiles 通用层 eq 过滤, 可见集与夹过滤叠加
        assertEquals(true, method.contains("fabushiVisibleUploaderIds"),
                "上传人可见集过滤保留(不放宽)");
    }
}
