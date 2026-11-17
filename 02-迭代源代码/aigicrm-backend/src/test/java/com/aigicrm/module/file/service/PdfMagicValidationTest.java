package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static com.aigicrm.module.file.service.FileService.assertPdfMagic;
import static org.junit.jupiter.api.Assertions.*;

/**
 * U1 魔数校验钉(2026-11-17): .pdf 扩展名上传须含 %PDF- 魔数(前1024字节内, PDF 规范 7.5.1)。
 * T5f 实测缺口: 纯文本改名 fake.pdf 可上传挂载致阅读器打不开——内容伪装在上传层拦截。
 */
class PdfMagicValidationTest {

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    /** 正例: 标准头起始于第0字节。 */
    @Test
    void pdfMagic_standardHeader_passes() {
        byte[] pdf = "%PDF-1.7\n%âãÏÓ\n1 0 obj\nend\n%%EOF".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertDoesNotThrow(() -> assertPdfMagic(file("a.pdf", pdf)));
    }

    /** 正例: 头前有少量前置字节(部分工具产物)——1024 字节窗口内仍通过。 */
    @Test
    void pdfMagic_headerWithin1024Bytes_passes() {
        byte[] pad = new byte[500];
        byte[] tail = "%PDF-1.4\nrest".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] all = new byte[pad.length + tail.length];
        System.arraycopy(pad, 0, all, 0, pad.length);
        System.arraycopy(tail, 0, all, pad.length, tail.length);
        assertDoesNotThrow(() -> assertPdfMagic(file("b.pdf", all)));
    }

    /** 正例: 头恰在 1024 字节窗口边界(1019..1023)——仍通过。 */
    @Test
    void pdfMagic_headerAtWindowEdge_passes() {
        byte[] all = new byte[1100];
        byte[] magic = "%PDF-".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, all, 1019, magic.length);
        assertDoesNotThrow(() -> assertPdfMagic(file("c.pdf", all)));
    }

    /** U1 负例: 纯文本伪装(T5f case1 复现物)——400 拒绝。 */
    @Test
    void pdfMagic_plainTextDisguise_throws400() {
        byte[] fake = "this is NOT a pdf, just plain text disguise\n".repeat(40)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BusinessException e = assertThrows(BusinessException.class, () -> assertPdfMagic(file("fake.pdf", fake)));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("%PDF-"));
    }

    /** 边界负例: 头在 1024 字节窗口之外(第 1024 字节起)——拒绝。 */
    @Test
    void pdfMagic_headerBeyondWindow_throws400() {
        byte[] all = new byte[1100];
        byte[] magic = "%PDF-".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, all, 1024, magic.length);
        assertThrows(BusinessException.class, () -> assertPdfMagic(file("d.pdf", all)));
    }

    /** 边界负例: 空文件——拒绝。 */
    @Test
    void pdfMagic_emptyFile_throws400() {
        assertThrows(BusinessException.class, () -> assertPdfMagic(file("e.pdf", new byte[0])));
    }
}
