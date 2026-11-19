package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiH5UploadService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 【F3b 2026-11-19】文件中心 H5 上传通道端点对(/mp-file-picker 换票——MP-TOKEN-001 最后一处)。
 * 与 F3a-1 同构: 发票走 Authorization header(主 JWT 绝不进 URL), 消费端 permitAll 只认
 * 短时票(scope=upload∧scene=files∧TTL 5min∧限速), 鉴权与上传闸收敛在 FabushiH5UploadService
 * (scene 通用票据服务, 类名承 F3a-1 历史)。独立控制器而非挂 FileController——其被
 * PermissionBehaviorMatrixTest 瘦上下文显式导入构造, 加依赖会炸其用例(F3a-1 实测教训)。
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileH5UploadController {

    private final FabushiH5UploadService h5UploadService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 签发短时上传票(scene=files; file:upload 门——与原生上传入口同权限码)。 */
    @GetMapping("/upload-ticket")
    @PreAuthorize("hasAuthority('file:upload')")
    public Result<Map<String, Object>> uploadTicket(jakarta.servlet.http.HttpServletRequest request) {
        Long uid = SecurityUtils.getCurrentUserId();
        String parentJti = null;
        String auth = request != null ? request.getHeader("Authorization") : null;
        if (auth != null && auth.startsWith("Bearer ")) {
            parentJti = jwtTokenProvider.getJtiFromToken(auth.substring(7));
        }
        return Result.ok(h5UploadService.issueFileTicket(uid, parentJti));
    }

    /** H5 上传消费(permitAll, 无主 JWT): 复用 FileService.upload 同路径同闸(文件夹权限/配额/扫描);
     *  类型大小与原生通道同口径不另设闸。回滚=aigicrm.file.h5-upload.enabled=false → 两端点 404。 */
    @PostMapping("/upload/h5")
    public Result<Map<String, Object>> uploadH5(@RequestParam("ticket") String ticket,
                                                @RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "scope", required = false) String scope,
                                                @RequestParam(value = "folderId", required = false) Long folderId) {
        return Result.ok(h5UploadService.consumeFileUpload(ticket, file, folderId, scope));
    }
}
