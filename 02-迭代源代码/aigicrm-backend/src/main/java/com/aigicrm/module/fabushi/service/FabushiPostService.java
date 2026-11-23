package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.FabushiScopeType;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiLike;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiPostAttachment;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.entity.BizFabushiSection;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 法布施统一内容服务(一期 2026-11-15): 心得/诵读(文字版)/正能量 共用 fb_post 形态(需求 G4)。
 * 附件(图/音/视)二期激活, 表与实体一期就绪; 互动(评论/木鱼/已读)一期全量。
 */
@Service
@RequiredArgsConstructor
public class FabushiPostService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_PUBLISHED = "published";
    private static final String LIT_X = "内容不存在或已下架";
    private static final String LIT_FILEID = "fileId";
    private static final String LIT_FILETYPE = "fileType";
    private static final String LIT_DURATIONSECONDS = "durationSeconds";
    private static final String LIT_X_2 = "内容不存在";
    private static final String LIT_CONTENT = "content";


    private final BizFabushiPostMapper postMapper;
    private final BizFabushiBookMapper bookMapper;
    private final BizFabushiCommentMapper commentMapper;
    private final BizFabushiLikeMapper likeMapper;
    private final FabushiRecitationService recitationService;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiPostAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;
    private final BizFileMapper fileMapper;
    private final com.aigicrm.security.SecurityAuditService securityAuditService;
    /** 【P2 2026-11-23】章节标题联显（按书浏览表格「章节」列）；只读查询，不参与写路径。 */
    private final BizFabushiSectionMapper sectionMapper;

    /** 【D1 2026-11-23】类型白名单收敛到单源枚举 FabushiScopeType（songdu/suishi/zaidu）；旧值已由 V2026.11.23.1 迁移。 */
    private static final Set<String> SCOPE_TYPES = FabushiScopeType.codes();
    // Sonar java:S1192 —— body 键与审计前缀在多个切片中共用，收敛为常量
    private static final String LIT_TITLE = "title";
    private static final String LIT_SECTION_ID = "sectionId";
    private static final String LIT_POST_KEY = "post:";

    /** 【F1 2026-11-19 spec v2】附件分型限额: 图≤5 / 视≤5 / 音≤10 段。 */
    private static final int MAX_IMAGE = 5;
    /** 【Q14 2026-11-23 拍板】随施(朋友圈式)图片对齐 9 张；songdu/zaidu 沿用原文规格 5 张。 */
    private static final int MAX_IMAGE_SUISHI = 9;
    private static final int MAX_VIDEO = 5;
    private static final int MAX_AUDIO = 10;
    /** 【F1】音单段时长上限(秒); durationSeconds 未知暂放行, F2 录音上线后必填。 */
    private static final int MAX_AUDIO_SECONDS = 60;
    /** 【F1 2026-11-19 spec v2】"一月内上传显示 NEW" 窗口(天)。 */
    private static final long NEW_WINDOW_DAYS = 30;

    /**
     * 【F2 2026-11-19 拍板③】评论类型白名单: text 默认 / highlight 重点描述 / audio 语音诵读。
     * 与 DDL biz_fabushi_comment.type 注释一致(type 列建表即预留三态, 此前 service 硬编码 text 未接线)。
     */
    /** 【F2b】java:S1192——"audio" 字面量 4 处达阈值, 常量化(纯重构零逻辑变化)。 */
    private static final String TYPE_AUDIO = "audio";

    private static final Set<String> COMMENT_TYPES = Set.of("text", "highlight", TYPE_AUDIO);

    /** 【F4a】提醒触达在线自检日界口径: Asia/Shanghai(与 C4 时区结论同源)。 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 内容分页(fabushi:view 门): 默认 published 按时间倒序 + 联显/互动计数。
     * status 传参仅治理门生效: 持 fabushi:content:manage 者可查 offline 记录(内容治理页),
     * 其余调用方一律强制 published——防越权翻看下架内容。
     */
    public Page<BizFabushiPost> page(String scopeType, Long bookId, String status, boolean manageAllowed,
                                     Long viewerId, String keyword, long current, long size) {
        boolean publishedOnly = !manageAllowed || status == null || status.isBlank() || LIT_PUBLISHED.equals(status);
        LambdaQueryWrapper<BizFabushiPost> qw = new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, publishedOnly ? LIT_PUBLISHED : status)
                .eq(scopeType != null && !scopeType.isBlank(), BizFabushiPost::getScopeType, scopeType)
                .eq(bookId != null, BizFabushiPost::getBookId, bookId)
                .orderByDesc(BizFabushiPost::getCreatedAt);
        applyKeyword(qw, keyword);
        Page<BizFabushiPost> page = postMapper.selectPage(new Page<>(current, size), qw);
        fillInteractions(page.getRecords(), viewerId);
        return page;
    }

    /**
     * 【D16 2026-11-23】服务端搜索（人名/书籍/具体文字）：人名/书名先解析为 id 集再 or-in，
     * 正文/标题 like；仅叠加在既有 status/scope 条件内——已删帖由逻辑删自动滤除（负例契约
     * check-fabushi-search-visibility.sql：offline/deleted 命中必须 0 行）。
     */
    private void applyKeyword(LambdaQueryWrapper<BizFabushiPost> qw, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        List<Long> uids = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getRealName, kw)).stream().map(SysUser::getId).toList();
        List<Long> bids = bookMapper.selectList(new LambdaQueryWrapper<BizFabushiBook>()
                .like(BizFabushiBook::getTitle, kw)).stream().map(BizFabushiBook::getId).toList();
        qw.and(w -> {
            w.like(BizFabushiPost::getContent, kw).or().like(BizFabushiPost::getTitle, kw);
            if (!uids.isEmpty()) {
                w.or().in(BizFabushiPost::getUserId, uids);
            }
            if (!bids.isEmpty()) {
                w.or().in(BizFabushiPost::getBookId, bids);
            }
        });
    }

    // ===== Controller 层 Map 化输出(ArchUnit: controller_must_not_depend_on_entity) =====

    /** 内容分页 Map 化(records/total)。 */
    public Map<String, Object> pageData(String scopeType, Long bookId, String status, boolean manageAllowed,
                                        Long viewerId, String keyword, long current, long size) {
        Page<BizFabushiPost> p = page(scopeType, bookId, status, manageAllowed, viewerId, keyword, current, size);
        // 【D21】朋友圈式图片网格：批量查附件按帖组装（image 类型在前端过滤展示）
        if (!p.getRecords().isEmpty()) {
            Set<Long> ids = p.getRecords().stream().map(BizFabushiPost::getId).collect(Collectors.toSet());
            Map<Long, List<BizFabushiPostAttachment>> byPost = attachmentMapper.selectList(
                            new LambdaQueryWrapper<BizFabushiPostAttachment>().in(BizFabushiPostAttachment::getPostId, ids))
                    .stream().collect(Collectors.groupingBy(BizFabushiPostAttachment::getPostId));
            p.getRecords().forEach(r -> r.setAttachments(byPost.getOrDefault(r.getId(), List.of())));
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return data;
    }

    /** 发布 Map 化(body → 实体组装收在 service)。 */
    public Map<String, Object> createData(Map<String, Object> body) {
        BizFabushiPost post = new BizFabushiPost();
        post.setScopeType(body.get("scopeType") == null ? null : String.valueOf(body.get("scopeType")));
        post.setTitle(body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)));
        post.setContent(body.get(LIT_CONTENT) == null ? null : String.valueOf(body.get(LIT_CONTENT)));
        if (body.get("bookId") != null) {
            post.setBookId(Long.valueOf(String.valueOf(body.get("bookId"))));
        }
        if (body.get(LIT_SECTION_ID) != null) {
            post.setSectionId(Long.valueOf(String.valueOf(body.get(LIT_SECTION_ID))));
        }
        BizFabushiPost created = create(post);
        return Map.of("id", created.getId());
    }

    /** 评论 Map 化(F2: 透传 type/audioFileId; parentCommentId 建模未接线, 不扩)。 */
    public Map<String, Object> addCommentData(Long postId, Map<String, Object> body) {
        BizFabushiComment c = new BizFabushiComment();
        Object content = body == null ? null : body.get(LIT_CONTENT);
        c.setContent(content == null ? null : String.valueOf(content));
        if (body != null && body.get("type") != null) {
            c.setType(String.valueOf(body.get("type")));
        }
        if (body != null && body.get("audioFileId") != null) {
            c.setAudioFileId(Long.valueOf(String.valueOf(body.get("audioFileId"))));
        }
        BizFabushiComment created = addComment(postId, c);
        return Map.of("id", created.getId());
    }

    /** 内容详情(fabushi:view 门): 帖+附件+互动计数+复诵列表(D14)。 */
    public Map<String, Object> detail(Long id, Long viewerId) {
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, LIT_X);
        }
        fillInteractions(List.of(post), viewerId);
        List<Map<String, Object>> attachments = attachmentData(id);
        List<Map<String, Object>> recitations = recitationService.listData(id);
        return Map.of(
                "post", post,
                "attachments", attachments,
                "likeCount", post.getLikeCount() == null ? 0L : post.getLikeCount(),
                "likedByMe", isLikedBy(id, viewerId),
                "recitationCount", post.getRecitationCount() == null ? 0L : post.getRecitationCount(),
                "recitations", recitations);
    }

    /** 附件输入：文件 id / 类型 / 可选时长（create 用）。 */
    private record AttachmentInput(Long fileId, String fileType, Integer durationSeconds) {}

    private void validateFabushiFile(Long fileId) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null || (file.getDeleted() != null && file.getDeleted() == 1)) throw new BusinessException(404, "文件不存在");
        if (!"fabushi".equals(file.getFileScope())) throw new BusinessException(400, "附件须上传到法布施空间");
    }

    private AttachmentInput parseAttachmentInput(Map<String, Object> body) {
        Long fileId = body == null || body.get(LIT_FILEID) == null ? null : Long.valueOf(String.valueOf(body.get(LIT_FILEID)));
        String fileType = body == null || body.get(LIT_FILETYPE) == null ? null : String.valueOf(body.get(LIT_FILETYPE));
        if (fileId == null || !Set.of("image", TYPE_AUDIO, "video").contains(fileType)) {
            throw new BusinessException(400, "附件类型或文件不合法");
        }
        Integer durationSeconds = body.get(LIT_DURATIONSECONDS) == null ? null
                : Integer.parseInt(String.valueOf(body.get(LIT_DURATIONSECONDS)));
        return new AttachmentInput(fileId, fileType, durationSeconds);
    }

    private BizFabushiPost requireOwnedPost(Long postId, Long uid) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(404, LIT_X_2);
        if (!Objects.equals(post.getUserId(), uid) && !com.aigicrm.security.SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "只能维护自己发布内容的附件");
        }
        return post;
    }

    private BizFabushiPost requireOwnedPublishedPost(Long postId, Long uid) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) throw new BusinessException(404, LIT_X);
        if (!Objects.equals(post.getUserId(), uid) && !com.aigicrm.security.SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "只能维护自己发布内容的附件");
        }
        return post;
    }

    /** 内容附件(二期): 文件必须已上传到 scope=fabushi; 仅作者/管理员可挂载。 */
    @Transactional
    public Map<String, Object> addAttachment(Long postId, Map<String, Object> body) {
        Long uid = com.aigicrm.security.SecurityUtils.getCurrentUserId();
        requireOwnedPublishedPost(postId, uid);
        AttachmentInput input = parseAttachmentInput(body);
        validateFabushiFile(input.fileId());
        enforceAttachmentLimits(postId, input);
        Long count = attachmentMapper.selectCount(new LambdaQueryWrapper<BizFabushiPostAttachment>()
                .eq(BizFabushiPostAttachment::getPostId, postId));
        BizFabushiPostAttachment row = new BizFabushiPostAttachment();
        row.setPostId(postId); row.setFileId(input.fileId()); row.setFileType(input.fileType());
        row.setSortOrder(count == null ? 0 : count.intValue());
        if (input.durationSeconds() != null) row.setDurationSeconds(input.durationSeconds());
        attachmentMapper.insert(row);
        return Map.of("id", row.getId(), LIT_FILEID, input.fileId(), LIT_FILETYPE, input.fileType());
    }

    @Transactional
    public void removeAttachment(Long postId, Long attachmentId) {
        Long uid = com.aigicrm.security.SecurityUtils.getCurrentUserId();
        requireOwnedPost(postId, uid);
        BizFabushiPostAttachment row = attachmentMapper.selectById(attachmentId);
        if (row == null || !postId.equals(row.getPostId())) throw new BusinessException(404, "附件不存在");
        attachmentMapper.deleteById(attachmentId);
    }

    /**
     * 【F1 2026-11-19 spec v2】分型限额: 图≤5 / 视≤5 / 音≤10 段; 音单段>60s 拒绝。
     * durationSeconds 未知(旧聊天选文件路径)暂放行, F2 录音上线后改为必填校验。
     */
    private void enforceAttachmentLimits(Long postId, AttachmentInput input) {
        BizFabushiPost owner = postMapper.selectById(postId);
        boolean isSuishi = owner != null && FabushiScopeType.SUISHI.getCode().equals(owner.getScopeType());
        int maxImage = isSuishi ? MAX_IMAGE_SUISHI : MAX_IMAGE;
        List<BizFabushiPostAttachment> existing = attachmentMapper.selectList(new LambdaQueryWrapper<BizFabushiPostAttachment>()
                .eq(BizFabushiPostAttachment::getPostId, postId));
        long sameType = existing.stream().filter(a -> input.fileType().equals(a.getFileType())).count();
        switch (input.fileType()) {
            case "image" -> {
                if (sameType >= maxImage) {
                    throw new BusinessException(400, isSuishi ? "图片已达上限9张" : "图片已达上限5张");
                }
            }
            case "video" -> {
                if (sameType >= MAX_VIDEO) throw new BusinessException(400, "视频已达上限5段");
            }
            case TYPE_AUDIO -> {
                if (sameType >= MAX_AUDIO) throw new BusinessException(400, "语音已达上限10段");
                if (input.durationSeconds() != null && input.durationSeconds() > MAX_AUDIO_SECONDS)
                    throw new BusinessException(400, "单段语音不得超过60秒");
            }
            default -> { /* parseAttachmentInput 已白名单 {image,audio,video}, 不可达 */ }
        }
    }

    private List<Map<String, Object>> attachmentData(Long postId) {
        List<BizFabushiPostAttachment> rows = attachmentMapper.selectList(new LambdaQueryWrapper<BizFabushiPostAttachment>()
                .eq(BizFabushiPostAttachment::getPostId, postId)
                .orderByAsc(BizFabushiPostAttachment::getSortOrder));
        if (rows.isEmpty()) return List.of();
        Set<Long> fileIds = rows.stream().map(BizFabushiPostAttachment::getFileId).collect(Collectors.toSet());
        Map<Long, BizFile> files = fileMapper.selectBatchIds(fileIds).stream().collect(Collectors.toMap(BizFile::getId, f -> f));
        return rows.stream().map(row -> {
            BizFile file = files.get(row.getFileId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.getId()); m.put("postId", row.getPostId()); m.put(LIT_FILEID, row.getFileId());
            m.put(LIT_FILETYPE, row.getFileType()); m.put(LIT_DURATIONSECONDS, row.getDurationSeconds());
            m.put("fileName", file == null ? null : file.getFileName());
            m.put("fileSize", file == null ? null : file.getFileSize());
            return m;
        }).toList();
    }

    /**
     * 发布(fabushi:create 门): scope_type 白名单(单源枚举) + 分型校验(D1 2026-11-23, Q4 拍板)。
     * songdu/zaidu：必选书（如同四项激励先选客户）且书目须 approved——补齐此前"注释写了没实现"的校验；
     * suishi：免标题免书目（负例 400：带 title/bookId 即拒，朋友圈式一键发）。
     * 【S3776 2026-11-23】原方法认知复杂度 21（阈值 15），把"类型解析/分型校验/书目校验"各自
     * 抽成单一职责私有方法；校验顺序与异常码逐条保持不变（有测试钉住）。
     */
    public BizFabushiPost create(BizFabushiPost post) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        FabushiScopeType type = resolveScopeType(post);
        validateScopeTarget(post, type);
        if (isBlankText(post.getContent()) && isBlankText(post.getTitle())) {
            throw new BusinessException(400, "内容不能为空");
        }
        post.setId(null);
        post.setUserId(user.getUserId());
        post.setStatus(LIT_PUBLISHED);
        postMapper.insert(post);
        return post;
    }

    /** scope_type 必须在单源枚举内，否则 400。 */
    private FabushiScopeType resolveScopeType(BizFabushiPost post) {
        if (post.getScopeType() == null || !SCOPE_TYPES.contains(post.getScopeType())) {
            throw new BusinessException(400, "内容类型不合法");
        }
        return FabushiScopeType.valueOf(post.getScopeType().toUpperCase());
    }

    /** 分型校验：需书的类型必须挂已审核书目；免书类型不得带标题/书目。 */
    private void validateScopeTarget(BizFabushiPost post, FabushiScopeType type) {
        if (type.requiresBook()) {
            requireApprovedBook(post.getBookId());
            return;
        }
        if (!isBlankText(post.getTitle())) {
            throw new BusinessException(400, "每日随施不设置标题");
        }
        if (post.getBookId() != null) {
            throw new BusinessException(400, "每日随施不关联书目");
        }
    }

    /** 书目必须存在、未删、已审核。 */
    private void requireApprovedBook(Long bookId) {
        if (bookId == null) {
            throw new BusinessException(400, "该类型必须关联具体书目（先选书再发布）");
        }
        BizFabushiBook book = bookMapper.selectById(bookId);
        if (book == null
                || (book.getDeleted() != null && book.getDeleted() == 1)
                || !"approved".equals(book.getStatus())) {
            throw new BusinessException(400, "关联书目不存在或未通过审核");
        }
    }

    private static boolean isBlankText(String s) {
        return s == null || s.isBlank();
    }

    /** 下架(fabushi:content:manage 门; G3 留痕: 原因必填 + 审计切面 + PERMISSION_CHANGE 时间线)。 */
    @Transactional
    public void offline(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "下架原因不能为空");
        }
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(404, LIT_X_2);
        }
        post.setStatus("offline");
        post.setOfflineBy(com.aigicrm.security.SecurityUtils.getCurrentUserId());
        post.setOfflineAt(LocalDateTime.now());
        post.setOfflineReason(reason);
        postMapper.updateById(post);
        securityAuditService.permissionChange("fabushi_post_offline", LIT_POST_KEY + id);
    }

    /** 恢复已下架内容(fabushi:content:manage 门): 回到 published, 清空下架留痕。 */
    @Transactional
    public void restore(Long id) {
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(404, LIT_X_2);
        }
        if (!"offline".equals(post.getStatus())) {
            throw new BusinessException(400, "只有已下架内容可以恢复");
        }
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getId, id)
                .set(BizFabushiPost::getStatus, LIT_PUBLISHED)
                .set(BizFabushiPost::getOfflineBy, null)
                .set(BizFabushiPost::getOfflineAt, null)
                .set(BizFabushiPost::getOfflineReason, null));
        securityAuditService.permissionChange("fabushi_post_restore", LIT_POST_KEY + id);
    }

    // ===== D13 2026-11-23 本人修改/删除 + 治理已删除（计划切片 2，权限审计口径：PUT 仅作者本人，DELETE 作者或 admin） =====

    /** 本人编辑(仅作者本人，admin 不代编辑；offline 帖禁编辑)：可改 title/content/sectionId，分型规则保持。 */
    @Transactional
    public void updateOwn(Long id, Map<String, Object> body) {
        BizFabushiPost post = requireOwnedPost(id);
        if (!LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(400, "已下架内容不可编辑");
        }
        BizFabushiPost upd = buildEditablePatch(id, body);
        validateEditable(post, upd);
        postMapper.updateById(upd);
        securityAuditService.permissionChange("fabushi_post_edit", LIT_POST_KEY + id);
    }

    /** 【S3776 2026-11-23】updateOwn 认知复杂度拆分：登录/存在/归属校验集中一处。 */
    private BizFabushiPost requireOwnedPost(Long id) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(404, LIT_X_2);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(403, "只能编辑自己发布的内容");
        }
        return post;
    }

    /** 只把 body 里出现的字段装进 patch（未出现=不改），与旧逻辑逐字段 containsKey 等价。 */
    private static BizFabushiPost buildEditablePatch(Long id, Map<String, Object> body) {
        BizFabushiPost upd = new BizFabushiPost();
        upd.setId(id);
        if (body.containsKey(LIT_TITLE)) {
            upd.setTitle(body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)));
        }
        if (body.containsKey(LIT_CONTENT)) {
            upd.setContent(body.get(LIT_CONTENT) == null ? null : String.valueOf(body.get(LIT_CONTENT)));
        }
        if (body.containsKey(LIT_SECTION_ID)) {
            upd.setSectionId(body.get(LIT_SECTION_ID) == null
                    ? null : Long.valueOf(String.valueOf(body.get(LIT_SECTION_ID))));
        }
        return upd;
    }

    /** 编辑后整体校验：合并后仍不能全空；每日随施不得带标题。 */
    private static void validateEditable(BizFabushiPost post, BizFabushiPost upd) {
        String finalTitle = upd.getTitle() != null ? upd.getTitle() : post.getTitle();
        String finalContent = upd.getContent() != null ? upd.getContent() : post.getContent();
        if ((finalTitle == null || finalTitle.isBlank()) && (finalContent == null || finalContent.isBlank())) {
            throw new BusinessException(400, "内容不能为空");
        }
        if (FabushiScopeType.SUISHI.getCode().equals(post.getScopeType())
                && upd.getTitle() != null && !upd.getTitle().isBlank()) {
            throw new BusinessException(400, "每日随施不设置标题");
        }
    }

    /** 本人删除(作者本人或 admin)：逻辑删除；附件文件保留文件中心（口径=文件独立生命周期，归上传人）。 */
    @Transactional
    public void deleteOwn(Long id) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(404, LIT_X_2);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId()) && !com.aigicrm.security.SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "只能删除自己发布的内容");
        }
        postMapper.deleteById(id);
        securityAuditService.permissionChange("fabushi_post_delete", LIT_POST_KEY + id);
    }

    /** 治理侧"已删除"分页（content:manage 门，自定义 SQL 绕过逻辑删）。 */
    public Map<String, Object> deletedPageData(long current, long size) {
        // 【SMOKE-20261123 F1】筛选条件已下沉到 Mapper SQL（deleted = 1），此处不再传 Wrapper：
        // 原实现传 null 导致片段为空、WHERE 丢失，已删除页签会列出全部内容。
        com.baomidou.mybatisplus.core.metadata.IPage<BizFabushiPost> p =
                postMapper.selectDeletedPage(new Page<>(current, size));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return data;
    }

    /** 治理侧恢复已删帖（content:manage 门，回到 published）。 */
    @Transactional
    public void restoreDeleted(Long id) {
        int rows = postMapper.restoreDeleted(id);
        if (rows == 0) {
            throw new BusinessException(404, "已删除内容不存在");
        }
        securityAuditService.permissionChange("fabushi_post_restore_deleted", LIT_POST_KEY + id);
    }

    /** 评论(fabushi:create 门): 帖须 published。 */
    public BizFabushiComment addComment(Long postId, BizFabushiComment comment) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, LIT_X);
        }
        // 【F2 2026-11-19 拍板③】类型白名单; audio=语音诵读(标题可选, 须挂 fabushi 空间音频文件)
        String type = comment.getType() == null || comment.getType().isBlank() ? "text" : comment.getType();
        if (!COMMENT_TYPES.contains(type)) {
            throw new BusinessException(400, "评论类型不合法");
        }
        if (TYPE_AUDIO.equals(type)) {
            if (comment.getAudioFileId() == null) {
                throw new BusinessException(400, "语音评论缺少音频文件");
            }
            validateFabushiFile(comment.getAudioFileId());
        } else if (comment.getContent() == null || comment.getContent().isBlank()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        comment.setId(null);
        comment.setPostId(postId);
        comment.setUserId(user == null ? null : user.getUserId());
        comment.setType(type);
        comment.setStatus(LIT_PUBLISHED);
        commentMapper.insert(comment);
        return comment;
    }

    /** 评论列表(fabushi:view 门): published 按时间正序。 */
    public List<BizFabushiComment> listComments(Long postId) {
        List<BizFabushiComment> comments = commentMapper.selectList(new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getPostId, postId)
                .eq(BizFabushiComment::getStatus, LIT_PUBLISHED)
                .orderByAsc(BizFabushiComment::getCreatedAt));
        if (!comments.isEmpty()) {
            Set<Long> uids = comments.stream().map(BizFabushiComment::getUserId).collect(Collectors.toSet());
            Map<Long, String> names = userMapper.selectBatchIds(uids).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
            comments.forEach(c -> c.setUserName(names.get(c.getUserId())));
        }
        return comments;
    }

    /** 评论列表 Map 化(controller 脱实体; Jackson convertValue 保字段全量含 LocalDateTime ISO 格式)。 */
    public List<Map<String, Object>> commentData(Long postId) {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return listComments(postId).stream()
                .<Map<String, Object>>map(c -> om.convertValue(c,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                .toList();
    }

    /** 木鱼点赞 toggle(fabushi:create 门; 唯一键幂等: 存在即取消, 不存在即点赞)。 */
    @Transactional
    public Map<String, Object> toggleLike(Long postId, Long userId) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, LIT_X);
        }
        Long existing = likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .eq(BizFabushiLike::getPostId, postId).eq(BizFabushiLike::getUserId, userId));
        boolean liked;
        if (existing > 0) {
            likeMapper.delete(new LambdaQueryWrapper<BizFabushiLike>()
                    .eq(BizFabushiLike::getPostId, postId).eq(BizFabushiLike::getUserId, userId));
            liked = false;
        } else {
            BizFabushiLike like = new BizFabushiLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likeMapper.insert(like);
            liked = true;
        }
        long total = likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .eq(BizFabushiLike::getPostId, postId));
        return Map.of("liked", liked, "likeCount", total);
    }

    /**
     * 已读标记(fabushi:view 门; 浏览行为幂等)。
     * 【2026-11-15 刻意偏离 IncentiveService.markRead 样板】激励样板用 JdbcTemplate 原生
     * upsert——原生 SQL 不经租户拦截器, INSERT 漏 tenant_id 列(评审 R7 家族教训)。改走
     * MP insert(拦截器自动补租户) + 唯一键防重(并发撞键捕获忽略=幂等), 语义等价且租户正确。
     */
    public void markRead(Long postId, Long userId) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, LIT_X_2);
        }
        Long existing = readStatusMapper.selectCount(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .eq(BizFabushiReadStatus::getUserId, userId).eq(BizFabushiReadStatus::getPostId, postId));
        if (existing > 0) {
            // 重复浏览刷新 read_at(统计口径"今日已读人数"依赖当日时间戳)
            BizFabushiReadStatus patch = new BizFabushiReadStatus();
            patch.setReadAt(LocalDateTime.now());
            readStatusMapper.update(patch, new LambdaQueryWrapper<BizFabushiReadStatus>()
                    .eq(BizFabushiReadStatus::getUserId, userId).eq(BizFabushiReadStatus::getPostId, postId));
            return;
        }
        try {
            BizFabushiReadStatus rs = new BizFabushiReadStatus();
            rs.setUserId(userId);
            rs.setPostId(postId);
            readStatusMapper.insert(rs);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发浏览撞唯一键: 已被并发请求标记, 幂等忽略
        }
    }

    /** 【F4a 提醒触达·在线自检】今日(Asia/Shanghai 日界)是否已有已读记录: readAt ∈ [今日0点, 明日0点)。 */
    public boolean hasReadToday(Long userId) {
        if (userId == null) return false;
        LocalDateTime start = LocalDate.now(ZONE_SHANGHAI).atStartOfDay();
        return readStatusMapper.selectCount(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .eq(BizFabushiReadStatus::getUserId, userId)
                .ge(BizFabushiReadStatus::getReadAt, start)
                .lt(BizFabushiReadStatus::getReadAt, start.plusDays(1))) > 0;
    }

    /** 列表/详情互动联显: 发帖人名 + 木鱼数 + 本人点赞/已读标记 + NEW 标记(F1 30 天窗口)。 */
    private void fillInteractions(List<BizFabushiPost> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return;
        }
        Set<Long> postIds = posts.stream().map(BizFabushiPost::getId).collect(Collectors.toSet());
        Set<Long> userIds = posts.stream().map(BizFabushiPost::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<BizFabushiLike> likeRows = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                .in(BizFabushiLike::getPostId, postIds));
        Map<Long, List<BizFabushiLike>> likeRowsByPost = likeRows.stream()
                .collect(Collectors.groupingBy(BizFabushiLike::getPostId));
        Set<Long> myLiked = viewerId == null ? Collections.emptySet()
                : likeRows.stream().filter(l -> Objects.equals(l.getUserId(), viewerId))
                .map(BizFabushiLike::getPostId).collect(Collectors.toSet());
        Set<Long> myRead = viewerId == null ? Collections.emptySet()
                : readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .eq(BizFabushiReadStatus::getUserId, viewerId).in(BizFabushiReadStatus::getPostId, postIds)).stream()
                .map(BizFabushiReadStatus::getPostId).collect(Collectors.toSet());
        Map<Long, Long> commentCounts = commentMapper.selectList(new LambdaQueryWrapper<BizFabushiComment>()
                        .eq(BizFabushiComment::getStatus, LIT_PUBLISHED).in(BizFabushiComment::getPostId, postIds)).stream()
                .collect(Collectors.groupingBy(BizFabushiComment::getPostId, Collectors.counting()));
        Map<Long, Long> recitationCounts = recitationService.countByPosts(postIds);

        // 【P2 2026-11-23】书名/章节名批量联显：按书浏览表格要「章节」列，信息流要「《书》› 章节」来源行。
        // 两次批量查询，不做 N+1；书目/章节已被软删时取不到则留 null（前端显示占位，不抛错）。
        Set<Long> bookIds = posts.stream().map(BizFabushiPost::getBookId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> bookTitles = bookIds.isEmpty() ? Collections.emptyMap()
                : bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(BizFabushiBook::getId, BizFabushiBook::getTitle, (a, b) -> a));
        Set<Long> sectionIds = posts.stream().map(BizFabushiPost::getSectionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sectionTitles = sectionIds.isEmpty() ? Collections.emptyMap()
                : sectionMapper.selectBatchIds(sectionIds).stream()
                .collect(Collectors.toMap(BizFabushiSection::getId, BizFabushiSection::getTitle, (a, b) -> a));

        for (BizFabushiPost p : posts) {
            p.setUserName(names.get(p.getUserId()));
            p.setBookTitle(p.getBookId() == null ? null : bookTitles.get(p.getBookId()));
            p.setSectionTitle(p.getSectionId() == null ? null : sectionTitles.get(p.getSectionId()));
            List<BizFabushiLike> rows = likeRowsByPost.getOrDefault(p.getId(), List.of());
            p.setLikeCount((long) rows.size());
            p.setLikeNames(buildLikeNames(rows, names));
            p.setCommentCount(commentCounts.getOrDefault(p.getId(), 0L));
            p.setRecitationCount(recitationCounts.getOrDefault(p.getId(), 0L));
            p.setReadByMe(myRead.contains(p.getId()));
            p.setLikedByMe(myLiked.contains(p.getId()));
            p.setIsNew(p.getCreatedAt() != null
                    && p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(NEW_WINDOW_DAYS)));
        }
    }

    /** 【D21】点赞人名单：前 8 位昵称 + 总数（如"王晓兰、陈慧 等 12 人"；无则 null）。 */
    private String buildLikeNames(List<BizFabushiLike> rows, Map<Long, String> names) {
        if (rows.isEmpty()) {
            return null;
        }
        List<String> nick = rows.stream()
                .map(l -> names.getOrDefault(l.getUserId(), "同事"))
                .distinct()
                .toList();
        int total = rows.size();
        int show = Math.min(8, nick.size());
        String head = String.join("、", nick.subList(0, show));
        return total > show ? head + " 等 " + total + " 人" : head;
    }

    private boolean isLikedBy(Long postId, Long viewerId) {
        return viewerId != null && likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .eq(BizFabushiLike::getPostId, postId).eq(BizFabushiLike::getUserId, viewerId)) > 0;
    }
}
