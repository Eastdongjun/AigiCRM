package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.entity.SysConfig;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P0 2026-10-17】Logo 匿名读取路径穿越 + system.logo 保存校验回归钉。
 *
 * 旧实现先判 "public/" 前缀再 normalize，`public/../private/x` 可通过校验并匿名读取私密文件；
 * 保存侧也允许任意 URL 落库。修复：normalize 后相对路径首段必须仍为 public；保存仅允许
 * /api/file/download/public/ 开头且不含 ..，并限制平台 admin 修改。
 */
class SysConfigControllerLogoTest {

    @TempDir
    Path root;

    private final SysConfigService configService = mock(SysConfigService.class);
    private final FileService fileService = mock(FileService.class);
    private SysConfigController controller;

    @BeforeEach
    void setUp() {
        reset(configService, fileService);
        controller = new SysConfigController(configService, fileService);
        ReflectionTestUtils.setField(controller, "uploadDir", root.toString());
    }

    @Test
    void logo_publicPath_served() throws Exception {
        Path dir = Files.createDirectories(root.resolve("public/1"));
        Files.write(dir.resolve("logo.png"), new byte[]{1, 2, 3});
        when(configService.getValue("system.logo"))
                .thenReturn("/api/file/download/public/1/logo.png");

        ResponseEntity<?> resp = controller.logo();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("image/png", resp.getHeaders().getContentType().toString());
    }

    @Test
    void logo_traversalToPrivateFile_blocked() throws Exception {
        Path privateDir = Files.createDirectories(root.resolve("private"));
        Files.write(privateDir.resolve("secret.png"), new byte[]{9, 9, 9});
        when(configService.getValue("system.logo"))
                .thenReturn("/api/file/download/public/../private/secret.png");

        ResponseEntity<?> resp = controller.logo();

        // 命中穿越 → 返回透明 SVG 兜底，绝不返回 private 文件
        assertTrue(resp.getHeaders().getContentType().toString().startsWith("image/svg+xml"));
        assertTrue(new String((byte[]) resp.getBody()).contains("<svg"));
        verify(fileService, never()).getOssUrlByKey("public/../private/secret.png");
    }

    @Test
    void batchUpdate_logoByAdminWithValidPublicPath_saved() {
        SysConfig logo = logo("/api/file/download/public/1/logo.png");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            controller.batchUpdate(List.of(logo));
        }
        verify(configService).batchUpdate(List.of(logo));
    }

    @Test
    void batchUpdate_logoByNonAdmin_rejected() {
        SysConfig logo = logo("/api/file/download/public/1/logo.png");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.batchUpdate(List.of(logo)));
            assertEquals(403, ex.getCode());
        }
        verify(configService, never()).batchUpdate(anyList());
    }

    @Test
    void batchUpdate_logoWithTraversal_rejected() {
        SysConfig logo = logo("/api/file/download/public/../private/secret.png");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(BusinessException.class, () -> controller.batchUpdate(List.of(logo)));
        }
        verify(configService, never()).batchUpdate(anyList());
    }

    @Test
    void batchUpdate_unchangedLogoDoesNotBlockNonAdminOtherConfigs() {
        SysConfig logo = logo("/api/file/download/public/1/logo.png");
        when(configService.getValue("system.logo"))
                .thenReturn("/api/file/download/public/1/logo.png");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            controller.batchUpdate(List.of(logo));
        }
        verify(configService).batchUpdate(List.of(logo));
    }

    @Test
    void batchUpdate_logoWithExternalOrNonPublicUrl_rejected() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(BusinessException.class,
                    () -> controller.batchUpdate(List.of(logo("https://evil.example/logo.png"))));
            assertThrows(BusinessException.class,
                    () -> controller.batchUpdate(List.of(
                            logo("/api/file/download/private/1/logo.png"))));
        }
        verify(configService, never()).batchUpdate(anyList());
    }

    private SysConfig logo(String value) {
        SysConfig config = new SysConfig();
        config.setConfigKey("system.logo");
        config.setConfigValue(value);
        return config;
    }
}
