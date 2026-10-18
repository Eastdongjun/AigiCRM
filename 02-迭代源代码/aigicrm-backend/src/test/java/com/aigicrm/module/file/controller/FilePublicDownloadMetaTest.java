package com.aigicrm.module.file.controller;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【P2 2026-09-30 复审修复】public 下载元数据校验回归钉。
 * 旧实现只校验物理路径在 public/ 段——库行已删(逻辑删)或范围已改而物理文件残留时,
 * 匿名请求仍可拉取。修复口径(评审): 元数据不存在默认 404;范围非 public 拒。
 * 匿名语义不引入 canDownloadFile(按登录用户判定), 仅按行公开性收口。
 */
class FilePublicDownloadMetaTest {

    @TempDir
    Path root;

    private final FileService fileService = mock(FileService.class);
    private FileController controller;
    private Path publicFile;

    @BeforeEach
    void setUp() throws Exception {
        controller = new FileController(fileService, mock(JwtTokenProvider.class),
                mock(com.aigicrm.module.system.service.SysUserService.class),
                mock(com.aigicrm.module.approval.service.ApprovalService.class));
        Path publicDir = Files.createDirectories(root.resolve("public/1"));
        publicFile = Files.write(publicDir.resolve("logo.jpg"), new byte[]{1, 2, 3});
        when(fileService.getLocalFilePath("")).thenReturn(root);
    }

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        return r;
    }

    private BizFile meta(String scope) {
        BizFile f = new BizFile();
        f.setId(9L);
        f.setFileName("品牌logo.jpg");
        f.setOssKey("public/1/logo.jpg");
        f.setFileScope(scope);
        return f;
    }

    @Test
    void publicFile_withPublicMeta_served() {
        when(fileService.getPublicFileByKey("public/1/logo.jpg")).thenReturn(meta("public"));
        var resp = controller.downloadPublic(req("/api/file/download/public/1/logo.jpg"));
        assertEquals(200, resp.getStatusCode().value(), "元数据在且 scope=public → 匿名可读");
    }

    @Test
    void publicFile_withoutMeta_404() {
        // 行被逻辑删(@TableLogic)或从未入库 → getPublicFileByKey=null → 404(评审默认口径)
        when(fileService.getPublicFileByKey("public/1/logo.jpg")).thenReturn(null);
        var resp = controller.downloadPublic(req("/api/file/download/public/1/logo.jpg"));
        assertEquals(404, resp.getStatusCode().value(), "物理文件在而元数据不存在 → 404");
    }

    @Test
    void publicPath_withNonPublicMeta_403() {
        // 行被移出 public(如改挂私人/激励范围)而物理文件残留 public/ → 拒
        when(fileService.getPublicFileByKey("public/1/logo.jpg")).thenReturn(meta("private"));
        var resp = controller.downloadPublic(req("/api/file/download/public/1/logo.jpg"));
        assertEquals(403, resp.getStatusCode().value(), "元数据范围非 public → 403");
    }

    @Test
    void traversalAndCrossSegment_stillBlocked() throws java.io.IOException {
        // H3 既有防线保留钉: 越段(非 public 首段)403;穿越(../)400
        Path privateFile = Files.write(Files.createDirectories(root.resolve("private")).resolve("x.txt"), new byte[]{1});
        assertEquals(403, controller.downloadPublic(req("/api/file/download/private/x.txt")).getStatusCode().value());
        assertEquals(403, controller.downloadPublic(req("/api/file/download/public/../private/x.txt")).getStatusCode().value(),
                "归一化后首段非 public → 403(与 H3 证据口径一致)");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        publicFile = null;
    }
}
