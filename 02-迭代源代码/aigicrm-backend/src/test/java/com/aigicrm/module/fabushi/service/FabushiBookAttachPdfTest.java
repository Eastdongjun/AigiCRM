package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.aigicrm.security.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 挂载 PDF 类型/大小校验钉(2026-11-17 U1/U2 缺口修复, T5f 实测两缺口后收口):
 * U1 类型: file_type 须=PDF(file_type=扩展名大写, FileService.java 上传口径), 非 PDF/空类型=400;
 * U2 大小: file_size ≤100MB(MAX_PDF_BYTES, 与 book.vue「≤100MB」文案一致), 超限=400; 恰好=通过。
 * 校验收口在挂载点 attachPdf——不影响 feed 附件上传(图片/音视频大文件合法)。
 */
class FabushiBookAttachPdfTest {

    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDictService dictService = mock(SysDictService.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final FabushiBookService svc = new FabushiBookService(
            bookMapper, postMapper, fileMapper, userMapper, dictService, auditService);

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiPost.class);
    }

    private BizFabushiBook book(long id) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(id);
        b.setTitle("验收书");
        b.setStatus("approved");
        return b;
    }

    private BizFile file(String fileType, Long fileSize) {
        BizFile f = new BizFile();
        f.setId(2696L);
        f.setFileName("x.pdf");
        f.setFileType(fileType);
        f.setFileSize(fileSize);
        f.setFileScope("fabushi");
        f.setDeleted(0);
        return f;
    }

    /** 正例: 真PDF(类型PDF+任意小文件)挂载成功。 */
    @Test
    void attachPdf_validPdf_succeeds() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        when(fileMapper.selectById(2696L)).thenReturn(file("PDF", 527_199L));

        svc.attachPdf(6L, 2696L);

        verify(bookMapper).updateById(any(BizFabushiBook.class));
    }

    /** 边界正例: 恰好 100MB 通过(≤MAX_PDF_BYTES)。 */
    @Test
    void attachPdf_exactly100MB_passes() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        when(fileMapper.selectById(2696L)).thenReturn(file("PDF", FabushiBookService.MAX_PDF_BYTES));

        svc.attachPdf(6L, 2696L);

        verify(bookMapper).updateById(any(BizFabushiBook.class));
    }

    /** U1 负例: 非 PDF 扩展名(TXT) = 400, 不落库。 */
    @Test
    void attachPdf_nonPdfType_throws400() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        when(fileMapper.selectById(2696L)).thenReturn(file("TXT", 1_760L));

        BusinessException e = assertThrows(BusinessException.class, () -> svc.attachPdf(6L, 2696L));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("仅支持 PDF"));
        verify(bookMapper, never()).updateById(any(BizFabushiBook.class));
    }

    /** U1 负例: 无扩展名(file_type=空串, 上传口径) = 400。 */
    @Test
    void attachPdf_emptyType_throws400() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        when(fileMapper.selectById(2696L)).thenReturn(file("", 1_760L));

        BusinessException e = assertThrows(BusinessException.class, () -> svc.attachPdf(6L, 2696L));
        assertEquals(400, e.getCode());
        verify(bookMapper, never()).updateById(any(BizFabushiBook.class));
    }

    /** U2 负例: 110MB(>100MB) = 400, 报错含实际大小, 不落库。 */
    @Test
    void attachPdf_over100MB_throws400() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        long size = 110L * 1024 * 1024;
        when(fileMapper.selectById(2696L)).thenReturn(file("PDF", size));

        BusinessException e = assertThrows(BusinessException.class, () -> svc.attachPdf(6L, 2696L));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("100MB"));
        verify(bookMapper, never()).updateById(any(BizFabushiBook.class));
    }

    /** 既有行为钉: scope 非 fabushi 仍 400(校验链不被新代码截断)。 */
    @Test
    void attachPdf_wrongScope_stillThrows400() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));
        BizFile f = file("PDF", 1_000L);
        f.setFileScope("private");
        when(fileMapper.selectById(2696L)).thenReturn(f);

        BusinessException e = assertThrows(BusinessException.class, () -> svc.attachPdf(6L, 2696L));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("fabushi"));
        verify(bookMapper, never()).updateById(any(BizFabushiBook.class));
    }

    /** fileId=null 仍 400(既有行为钉)。 */
    @Test
    void attachPdf_nullFileId_throws400() {
        when(bookMapper.selectById(6L)).thenReturn(book(6L));

        BusinessException e = assertThrows(BusinessException.class, () -> svc.attachPdf(6L, null));
        assertEquals(400, e.getCode());
        verify(bookMapper, never()).updateById(any(BizFabushiBook.class));
    }
}
