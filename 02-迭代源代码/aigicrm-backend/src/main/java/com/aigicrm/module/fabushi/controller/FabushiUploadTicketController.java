package com.aigicrm.module.fabushi.controller;

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
 * 【F3a 2026-11-19 spec v2 GAP-B】法布施 H5 上传通道端点对(发票 + 消费)。
 * 红线(MP-TOKEN-001 教训, QC 验收 grep 项): 主 JWT 绝不进 URL——发票走 Authorization header,
 * H5 消费端只认短时上传票(scope=upload∧scene=fabushi∧TTL 5min∧限速 10 张/60s)。
 * 消费端点刻意独立成控制器(而非 FileController): FileController 被 PermissionBehaviorMatrixTest
 * 瘦上下文显式导入构造, 加必填依赖会炸其 39 个用例; 本端点 permitAll 无 @PreAuthorize,
 * 已登记 PermissionMatrixTest 写方法白名单(票据鉴权设计取舍, 同 AuthController.login 家族)。
 */
@RestController
@RequestMapping("/api/fabushi")
@RequiredArgsConstructor
public class FabushiUploadTicketController {

    private final FabushiH5UploadService h5UploadService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 签发短时上传票(fabushi:create 门, 主 JWT 走 header; pjti 随主 token 撤销联动)。 */
    @GetMapping("/upload-ticket")
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> uploadTicket(jakarta.servlet.http.HttpServletRequest request) {
        Long uid = SecurityUtils.getCurrentUserId();
        String parentJti = null;
        String auth = request != null ? request.getHeader("Authorization") : null;
        if (auth != null && auth.startsWith("Bearer ")) {
            parentJti = jwtTokenProvider.getJtiFromToken(auth.substring(7));
        }
        return Result.ok(h5UploadService.issueTicket(uid, parentJti));
    }

    /** 【F3a-2】法布施阅读器短时票(scope=download∧scene=fabushi-reader, 60min, pjti 联检):
     *  reader H5 以 Bearer 票拉取 PDF(/api/file/download/** 属过滤器放行的下载路径);
     *  URL 从此不携带主 JWT——MP-TOKEN-001 法布施侧闭环(QC 红线终验项)。 */
    @GetMapping("/reader-ticket")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> readerTicket(jakarta.servlet.http.HttpServletRequest request) {
        Long uid = SecurityUtils.getCurrentUserId();
        String parentJti = null;
        String auth = request != null ? request.getHeader("Authorization") : null;
        if (auth != null && auth.startsWith("Bearer ")) {
            parentJti = jwtTokenProvider.getJtiFromToken(auth.substring(7));
        }
        String ticket = jwtTokenProvider.generateTransferToken(uid, parentJti, "download",
                "fabushi-reader", 60L);
        return Result.ok(Map.of("ticket", ticket, "expiresIn", 3600L));
    }

    /** H5 上传消费(permitAll, 无主 JWT): 鉴权收敛在 FabushiH5UploadService(票校验+分型大小闸+
     *  复用 FileService 同路径同闸: 配额/病毒扫描/租户/归属快照)。回滚=开关 404, revert 即回。
     *  【§7.2】permitAll 端点必须留痕(票内 uid/scene/落夹经 detail 可见)。 */
    @PostMapping("/upload/h5")
    @com.aigicrm.common.annotation.AigicrmLog(module = "法布施", action = "H5票据上传", targetType = "file")
    public Result<Map<String, Object>> uploadH5(@RequestParam("ticket") String ticket,
                                                @RequestParam("file") MultipartFile file) {
        return Result.ok(h5UploadService.consumeUpload(ticket, file));
    }
}
