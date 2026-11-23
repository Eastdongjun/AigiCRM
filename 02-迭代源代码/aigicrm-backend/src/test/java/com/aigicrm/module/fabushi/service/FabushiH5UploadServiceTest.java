package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【F3a 2026-11-19】法布施 H5 上传票矩阵钉(方案验收: TTL 过期 401 / scope 不符 403 / scene 不符 403 /
 * 黑名单与 pjti 联检 401 / 发票限速 429 / 分型大小闸 400 / 消费复用 FileService 同路径)。
 * provider 用 mock(校验矩阵可控); 发票 happy path 断言透传参数与 expiresIn。
 */
class FabushiH5UploadServiceTest {

    private final JwtTokenProvider provider = mock(JwtTokenProvider.class);
    private final FileService fileService = mock(FileService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final com.aigicrm.module.file.service.FabushiFolderService folderService =
            mock(com.aigicrm.module.file.service.FabushiFolderService.class);
    private final FabushiH5UploadService svc = new FabushiH5UploadService(
            provider, fileService, userDetailsService, redisTemplate, folderService);

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
    }

    private void allowIssue(long count) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(count);
    }

    private SecurityUser user() {
        SecurityUser su = new SecurityUser();
        su.setUserId(5L);
        su.setUsername("NO005");
        su.setGroupIds(List.of(2L, 1L));
        return su;
    }

    private void validTicketStubs(String ticket) {
        when(provider.validateToken(ticket)).thenReturn(true);
        when(provider.getScopeFromToken(ticket)).thenReturn(FabushiH5UploadService.SCOPE_UPLOAD);
        when(provider.getSceneFromToken(ticket)).thenReturn(FabushiH5UploadService.SCENE_FABUSHI);
        when(provider.getJtiFromToken(ticket)).thenReturn("jti-1");
        when(provider.getParentJtiFromToken(ticket)).thenReturn("pjti-1");
        when(provider.isBlacklisted("jti-1")).thenReturn(false);
        when(provider.isBlacklisted("pjti-1")).thenReturn(false);
        when(provider.getUserIdFromToken(ticket)).thenReturn(5L);
    }

    private MultipartFile file(String name, long size) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.getOriginalFilename()).thenReturn(name);
        when(f.getSize()).thenReturn(size);
        return f;
    }

    private void filesTicketStubs(String ticket) {
        when(provider.validateToken(ticket)).thenReturn(true);
        when(provider.getScopeFromToken(ticket)).thenReturn(FabushiH5UploadService.SCOPE_UPLOAD);
        when(provider.getSceneFromToken(ticket)).thenReturn(FabushiH5UploadService.SCENE_FILES);
        when(provider.getJtiFromToken(ticket)).thenReturn("jti-f");
        when(provider.getParentJtiFromToken(ticket)).thenReturn(null);
        when(provider.getUserIdFromToken(ticket)).thenReturn(5L);
    }

    // ===== 发票 =====

    @Test
    void issueTicket_happyPath_andRateLimit() {
        allowIssue(1L);
        when(provider.generateTransferToken(5L, "pjti-0", FabushiH5UploadService.SCOPE_UPLOAD,
                FabushiH5UploadService.SCENE_FABUSHI, 5L)).thenReturn("T");
        Map<String, Object> out = svc.issueTicket(5L, "pjti-0");
        assertEquals("T", out.get("ticket"));
        assertEquals(300L, out.get("expiresIn"));

        allowIssue(11L);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.issueTicket(5L, null));
        assertEquals(429, ex.getCode());
        assertEquals("发票过于频繁，请稍后再试", ex.getMessage());
    }

    // ===== 消费: 票校验矩阵 =====

    @Test
    void consume_validTicket_delegatesToUploadPath() {
        validTicketStubs("T");
        when(userDetailsService.loadUserByUsername("5")).thenReturn(user());
        MultipartFile f = file("a.jpg", 1024);
        BizFile out = new BizFile();
        out.setId(2696L);
        out.setFileName("a.jpg");
        out.setFileSize(1024L);
        out.setFileType("JPG");
        when(folderService.shareFolderId()).thenReturn(18L);
        when(fileService.upload(f, 18L, "fabushi")).thenReturn(out);
        Map<String, Object> result = svc.consumeUpload("T", f);
        assertEquals(2696L, result.get("id"));
        assertEquals("a.jpg", result.get("fileName"));
        verify(fileService).upload(f, 18L, "fabushi");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "消费完毕清会话");
    }

    @Test
    void consume_invalidOrExpired_401() {
        when(provider.validateToken("bad")).thenReturn(false);
        assertEquals(401, consumeRejected("bad", file("a.jpg", 1L)).getCode());
    }

    @Test
    void consume_wrongScope_403_neverUploads() {
        when(provider.validateToken("dl")).thenReturn(true);
        when(provider.getScopeFromToken("dl")).thenReturn("download");
        BusinessException ex = consumeRejected("dl", file("a.jpg", 1L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("scope"));
        verify(fileService, never()).upload(any(), any(), anyString());
    }

    @Test
    void consume_wrongScene_403() {
        when(provider.validateToken("s")).thenReturn(true);
        when(provider.getScopeFromToken("s")).thenReturn("upload");
        when(provider.getSceneFromToken("s")).thenReturn("other");
        assertEquals(403, consumeRejected("s", file("a.jpg", 1L)).getCode());
    }

    @Test
    void consume_blacklistedSelfOrParent_401() {
        when(provider.validateToken("t1")).thenReturn(true);
        when(provider.getScopeFromToken("t1")).thenReturn("upload");
        when(provider.getSceneFromToken("t1")).thenReturn("fabushi");
        when(provider.getJtiFromToken("t1")).thenReturn("jti-x");
        when(provider.isBlacklisted("jti-x")).thenReturn(true);
        assertEquals(401, consumeRejected("t1", file("a.jpg", 1L)).getCode());

        when(provider.isBlacklisted("jti-x")).thenReturn(false);
        when(provider.getParentJtiFromToken("t1")).thenReturn("pjti-x");
        when(provider.isBlacklisted("pjti-x")).thenReturn(true);
        assertEquals(401, consumeRejected("t1", file("a.jpg", 1L)).getCode());
    }

    // ===== 消费: 分型大小闸 =====

    @Test
    void consume_typeSizeGates() {
        validTicketStubs("T");
        when(userDetailsService.loadUserByUsername("5")).thenReturn(user());
        assertEquals("图片不能超过10MB",
                consumeRejected("T", file("a.jpg", 10L * 1024 * 1024 + 1)).getMessage());
        assertEquals("语音不能超过20MB",
                consumeRejected("T", file("a.mp3", 20L * 1024 * 1024 + 1)).getMessage());
        assertEquals("视频不能超过100MB",
                consumeRejected("T", file("a.mp4", 100L * 1024 * 1024 + 1)).getMessage());
        assertEquals("PDF不能超过100MB",
                consumeRejected("T", file("a.pdf", 100L * 1024 * 1024 + 1)).getMessage());
        assertEquals("不支持的文件类型(仅图片/语音/视频/PDF)",
                consumeRejected("T", file("a.exe", 1024)).getMessage());
        verify(fileService, never()).upload(any(), any(), anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    // ===== 【F3b】files 通道 =====

    @Test
    void fileTicket_issue_and_consume() {
        // 发票: scene=files, 独立限速键
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(provider.generateTransferToken(5L, null, FabushiH5UploadService.SCOPE_UPLOAD,
                FabushiH5UploadService.SCENE_FILES, 5L)).thenReturn("FT");
        assertEquals("FT", svc.issueFileTicket(5L, null).get("ticket"));

        // 消费: 委托 upload(file, folderId, scope 缺省 private); 文件通道无分型闸(exe 同原生口径直过校验)
        filesTicketStubs("FT");
        when(userDetailsService.loadUserByUsername("5")).thenReturn(user());
        MultipartFile f = file("a.exe", 999L);
        BizFile out = new BizFile();
        out.setId(1L);
        out.setFileName("a.exe");
        out.setFileSize(999L);
        out.setFileType("EXE");
        when(fileService.upload(f, 9L, "private")).thenReturn(out);
        assertEquals(1L, svc.consumeFileUpload("FT", f, 9L, null).get("id"));
        verify(fileService).upload(f, 9L, "private");
    }

    @Test
    void fileTicket_crossScene_rejected() {
        // fabushi 票不能走 files 通道(scene 校验双向)
        validTicketStubs("T");
        MultipartFile f = file("a.jpg", 1L);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> svc.consumeFileUpload("T", f, null, "private")).getCode());
        verify(fileService, never()).upload(any(), any(), anyString());
    }

    // ===== 会话包裹 =====

    @Test
    void consume_groupFallback_minGroup() {
        validTicketStubs("T");
        SecurityUser su = user();
        when(userDetailsService.loadUserByUsername("5")).thenReturn(su);
        BizFile out = new BizFile();
        out.setId(1L);
        out.setFileName("a.png");
        out.setFileSize(10L);
        out.setFileType("PNG");
        when(fileService.upload(any(), any(), eq("fabushi"))).thenReturn(out);
        svc.consumeUpload("T", file("a.png", 10));
        assertEquals(1L, su.getCurrentGroupId(), "无会话集团 claim → 最小集团兜底(同 JwtAuthFilter 口径)");
    }

    private BusinessException consumeRejected(String ticket, MultipartFile file) {
        return assertThrows(BusinessException.class, () -> svc.consumeUpload(ticket, file));
    }
}
