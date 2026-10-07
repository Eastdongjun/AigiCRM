package com.aigicrm.module.file.controller;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【P2 2026-10-07 复审修复】登录后 key 下载路由 disposition 收敛回归钉。
 * 旧实现 /download/{key} 硬编码 inline——历史/旁路落库的 SVG/HTML 等活性内容
 * 在登录态直接打开即执行(存储型 XSS)。修复:与 /download/{id}、/download/public
 * 同口径走 FileService.dispositionFor(纯图/视频 inline,其余 attachment)。
 */
class FileKeyDownloadDispositionTest {

    @TempDir
    Path root;

    private final FileService fileService = mock(FileService.class);
    private FileController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new FileController(fileService, mock(JwtTokenProvider.class));
        when(fileService.getLocalFilePath("")).thenReturn(root);
    }

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        return r;
    }

    private BizFile meta(String key, String scope) {
        BizFile f = new BizFile();
        f.setId(9L);
        f.setFileName(key.substring(key.lastIndexOf('/') + 1));
        f.setOssKey(key);
        f.setFileScope(scope);
        f.setUploaderId(7L);
        return f;
    }

    private String dispositionOf(String filename) throws java.io.IOException {
        Path p = Files.write(root.resolve(filename), new byte[]{1});
        String key = filename;
        when(fileService.getFileByKey(key)).thenReturn(meta(key, "private"));
        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            // canDownloadFile 走夹判定依赖多个协作者, 直接放行(权限面由专属测试族覆盖)
            when(fileService.canDownloadFile(org.mockito.ArgumentMatchers.any(BizFile.class),
                    org.mockito.ArgumentMatchers.eq(7L))).thenReturn(true);
            var resp = controller.download(req("/api/file/download/" + key));
            assertEquals(200, resp.getStatusCode().value(), filename + " 应可下载(归属+权限已放行)");
            var h = resp.getHeaders().get(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
            return h == null ? null : h.get(0);
        }
    }

    @Test
    void image_inline() throws Exception {
        String d = dispositionOf("photo.jpg");
        assertTrue(d != null && d.startsWith("inline"), "纯图片保持 inline: " + d);
    }

    @Test
    void svg_forcedAttachment() throws Exception {
        String d = dispositionOf("badge.svg");
        assertTrue(d != null && d.startsWith("attachment"), "SVG 活性内容必须 attachment: " + d);
    }

    @Test
    void html_forcedAttachment() throws Exception {
        String d = dispositionOf("page.html");
        assertTrue(d != null && d.startsWith("attachment"), "HTML 必须 attachment: " + d);
    }
}
