package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 【F3a 2026-11-19 spec v2 GAP-B】法布施 H5 上传通道: 短时上传票 + 分型大小闸 + 复用既有上传路径。
 * 【F3b 2026-11-19】scene 通用化: 同一套票机制服务 fabushi 与 files(/mp-file-picker 换票,
 * MP-TOKEN-001 最后一处)两个通道; 类名承 F3a-1 历史, 如后续通道再增可择批重命名。
 * 红线(QC 验收 grep 项): 主 JWT 绝不进 URL——发票端点走 Authorization header, H5 消费端只认票。
 * 回滚: aigicrm.fabushi.h5-upload.enabled / aigicrm.file.h5-upload.enabled 分别关两通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FabushiH5UploadService {

    public static final String SCOPE_UPLOAD = "upload";
    public static final String SCENE_FABUSHI = "fabushi";
    public static final String SCENE_FILES = "files";
    private static final long TICKET_TTL_MINUTES = 5;
    private static final long TICKET_TTL_SECONDS = TICKET_TTL_MINUTES * 60;
    /** 发票限速: 同 uid 60s 滚动窗 ≤10 张(防票池囤积)。 */
    private static final long RATE_WINDOW_SECONDS = 60;
    private static final long RATE_MAX_PER_WINDOW = 10;
    private static final String RATE_KEY_PREFIX = "fabushi:h5-ticket:";
    private static final String FILE_RATE_KEY_PREFIX = "file:h5-ticket:";

    /** H5 通道单文件分型大小闸(spec v2 口径, 后端强制; 与 MP 原生通道独立)。 */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 100L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTS = Set.of("JPG", "JPEG", "PNG", "GIF", "WEBP", "BMP");
    private static final Set<String> AUDIO_EXTS = Set.of("MP3", "M4A", "WAV", "AAC");
    private static final Set<String> VIDEO_EXTS = Set.of("MP4", "MOV", "AVI");
    private static final String PDF_EXT = "PDF";

    private final JwtTokenProvider jwtTokenProvider;
    private final FileService fileService;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 【收口窗修复】分享心得系统夹解析(H5 票通道落夹)。 */
    private final com.aigicrm.module.file.service.FabushiFolderService fabushiFolderService;

    /** 默认开; 生产可经 yml/环境变量一键关(回滚开关)。 */
    @Value("${aigicrm.fabushi.h5-upload.enabled:true}")
    private boolean enabled = true;
    /** 【F3b】files 通道独立开关。 */
    @Value("${aigicrm.file.h5-upload.enabled:true}")
    private boolean fileEnabled = true;

    /** 【F3b-fix】java:S1192——开关关闭文案 ×3 达阈值, 常量化(本文件已做全量字面量自查, 其余均 <3)。 */
    private static final String MSG_H5_DISABLED = "H5 上传未启用";

    /** 签发短时上传票(scope=upload∧scene=fabushi, TTL 5min, 携 pjti 随主 token 撤销联动)。 */
    public Map<String, Object> issueTicket(Long userId, String parentJti) {
        requireEnabled();
        enforceIssueRate(RATE_KEY_PREFIX, userId);
        String ticket = jwtTokenProvider.generateTransferToken(userId, parentJti, SCOPE_UPLOAD, SCENE_FABUSHI,
                TICKET_TTL_MINUTES);
        return Map.of("ticket", ticket, "expiresIn", TICKET_TTL_SECONDS);
    }

    /** 【F3b】文件中心 H5 上传票(scope=upload∧scene=files, 同限速口径, 独立 Redis 键)。 */
    public Map<String, Object> issueFileTicket(Long userId, String parentJti) {
        if (!fileEnabled) throw new BusinessException(404, MSG_H5_DISABLED);
        enforceIssueRate(FILE_RATE_KEY_PREFIX, userId);
        String ticket = jwtTokenProvider.generateTransferToken(userId, parentJti, SCOPE_UPLOAD, SCENE_FILES,
                TICKET_TTL_MINUTES);
        return Map.of("ticket", ticket, "expiresIn", TICKET_TTL_SECONDS);
    }

    /** H5 上传消费(无主 JWT): 票校验 → 分型大小闸 → 以票内用户身份复用 FileService.upload 同路径同闸。
     *  返回 Map 化(controller_must_not_depend_on_entity 铁律, 同 FabushiPostService 口径)。 */
    public Map<String, Object> consumeUpload(String ticket, MultipartFile file) {
        requireEnabled();
        Long userId = validateTicket(ticket, SCENE_FABUSHI);
        validateTypeAndSize(file);
        UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));
        if (!(userDetails instanceof SecurityUser su)) {
            throw new BusinessException(401, "上传票内用户无效");
        }
        applyMinimalGroup(su);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(su, null, userDetails.getAuthorities());
        BizFile out;
        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
            // scope 固定 fabushi, folderId=null(帖子附件不挂文件夹)——配额/病毒扫描/租户/归属快照全复用
            // 【收口窗修复】H5 票通道直调本服务绕过 FileController 路由层——此处显式落分享心
            // 得系统夹(与 MP 原生附件通道同口径; scene=files 通道走 consumeFileUpload 的票内夹)
            out = fileService.upload(file, fabushiFolderService.shareFolderId(), SCENE_FABUSHI);
        } finally {
            SecurityContextHolder.clearContext();
        }
        return Map.of("id", out.getId(), "fileName", out.getFileName(),
                "fileSize", out.getFileSize(), "fileType", out.getFileType());
    }

    /**
     * 【F3b】文件中心 H5 上传消费(/mp-file-picker 换票, MP-TOKEN-001 最后一处):
     * 票校验(scene=files)后以票内用户身份复用 FileService.upload 同路径同闸——文件夹上传权限/
     * 配额/病毒扫描/归属快照全复用; 类型大小不另设闸(与原生 /api/file/upload 完全同口径,
     * 唯一硬闸=Spring multipart 200MB, application.yml max-file-size)。
     */
    public Map<String, Object> consumeFileUpload(String ticket, MultipartFile file, Long folderId, String scope) {
        if (!fileEnabled) throw new BusinessException(404, MSG_H5_DISABLED);
        Long userId = validateTicket(ticket, SCENE_FILES);
        UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));
        if (!(userDetails instanceof SecurityUser su)) {
            throw new BusinessException(401, "上传票内用户无效");
        }
        applyMinimalGroup(su);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(su, null, userDetails.getAuthorities());
        BizFile out;
        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
            String uploadScope = scope == null || scope.isBlank() ? "private" : scope;
            out = fileService.upload(file, folderId, uploadScope);
        } finally {
            SecurityContextHolder.clearContext();
        }
        return Map.of("id", out.getId(), "fileName", out.getFileName(),
                "fileSize", out.getFileSize(), "fileType", out.getFileType());
    }

    private void requireEnabled() {
        if (!enabled) throw new BusinessException(404, MSG_H5_DISABLED);
    }

    private void enforceIssueRate(String keyPrefix, Long userId) {
        try {
            String key = keyPrefix + userId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(RATE_WINDOW_SECONDS));
            }
            if (count != null && count > RATE_MAX_PER_WINDOW) {
                throw new BusinessException(429, "发票过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // fail-open: 签发仍需 fabushi:create 会话门; Redis 故障放行并留审计日志
            log.warn("H5 上传票限速 Redis 异常(本张放行): {}", e.getMessage());
        }
    }

    private Long validateTicket(String ticket, String expectedScene) {
        if (ticket == null || ticket.isBlank() || !jwtTokenProvider.validateToken(ticket)) {
            throw new BusinessException(401, "上传票无效或已过期");
        }
        if (!SCOPE_UPLOAD.equals(jwtTokenProvider.getScopeFromToken(ticket))) {
            throw new BusinessException(403, "上传票 scope 不符");
        }
        if (!expectedScene.equals(jwtTokenProvider.getSceneFromToken(ticket))) {
            throw new BusinessException(403, "上传票 scene 不符");
        }
        String jti = jwtTokenProvider.getJtiFromToken(ticket);
        if (jti != null && jwtTokenProvider.isBlacklisted(jti)) {
            throw new BusinessException(401, "上传票已撤销");
        }
        String parentJti = jwtTokenProvider.getParentJtiFromToken(ticket);
        if (parentJti != null && jwtTokenProvider.isBlacklisted(parentJti)) {
            throw new BusinessException(401, "上传票已随登录撤销");
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(ticket);
        if (userId == null) {
            throw new BusinessException(401, "上传票无效或已过期");
        }
        return userId;
    }

    private void validateTypeAndSize(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
        long size = file.getSize();
        if (IMAGE_EXTS.contains(ext)) {
            if (size > MAX_IMAGE_BYTES) throw new BusinessException(400, "图片不能超过10MB");
        } else if (AUDIO_EXTS.contains(ext)) {
            if (size > MAX_AUDIO_BYTES) throw new BusinessException(400, "语音不能超过20MB");
        } else if (VIDEO_EXTS.contains(ext)) {
            if (size > MAX_VIDEO_BYTES) throw new BusinessException(400, "视频不能超过100MB");
        } else if (PDF_EXT.equals(ext)) {
            if (size > MAX_PDF_BYTES) throw new BusinessException(400, "PDF不能超过100MB");
        } else {
            throw new BusinessException(400, "不支持的文件类型(仅图片/语音/视频/PDF)");
        }
    }

    /** 票内用户无会话集团 claim——最小集团兜底(与 JwtAuthFilter.applySessionGroup 回退分支同口径)。 */
    private void applyMinimalGroup(SecurityUser su) {
        Long cg = null;
        if (su.getGroupIds() != null && !su.getGroupIds().isEmpty()) {
            cg = su.getGroupIds().stream().min(Long::compareTo).orElse(null);
        }
        su.setCurrentGroupId(cg);
    }
}
