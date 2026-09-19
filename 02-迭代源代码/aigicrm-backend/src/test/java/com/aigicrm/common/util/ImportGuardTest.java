package com.aigicrm.common.util;

import com.aigicrm.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 【BS-D 2026-09-19】导入资源上限:大小/扩展名/行数(拒绝服务防护)。 */
class ImportGuardTest {

    private static MockMultipartFile xlsxOfSize(long size) {
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[(int) size]);
    }

    @Test
    void checkFile_normalXlsx_passes() {
        assertDoesNotThrow(() -> ImportGuard.checkFile(xlsxOfSize(1024)));
    }

    @Test
    void checkFile_empty_rejected() {
        assertThrows(BusinessException.class, () -> ImportGuard.checkFile(xlsxOfSize(0)),
                "空文件拒绝(与原口径一致)");
    }

    @Test
    void checkFile_wrongExtension_rejected() {
        MockMultipartFile csv = new MockMultipartFile("file", "import.csv", "text/csv", new byte[10]);
        assertThrows(BusinessException.class, () -> ImportGuard.checkFile(csv));
    }

    @Test
    void checkFile_overSize_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ImportGuard.checkFile(xlsxOfSize(ImportGuard.MAX_IMPORT_FILE_BYTES + 1)));
        assertTrue(ex.getMessage().contains("5MB"), "超限提示须含上限说明: " + ex.getMessage());
    }

    @Test
    void checkRow_atLimit_passes_overLimit_throws() {
        assertDoesNotThrow(() -> ImportGuard.checkRow(ImportGuard.MAX_IMPORT_ROWS));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ImportGuard.checkRow(ImportGuard.MAX_IMPORT_ROWS + 1));
        assertTrue(ex.getMessage().contains("5000"));
    }
}
