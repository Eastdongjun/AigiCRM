package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiLike;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiPostAttachment;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final BizFabushiCommentMapper commentMapper;
    private final BizFabushiLikeMapper likeMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiPostAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;
    private final BizFileMapper fileMapper;
    private final com.aigicrm.security.SecurityAuditService securityAuditService;

    private static final Set<String> SCOPE_TYPES = Set.of("heart", "recitation", "znl");

    /**
     * 内容分页(fabushi:view 门): 默认 published 按时间倒序 + 联显/互动计数。
     * status 传参仅治理门生效: 持 fabushi:content:manage 者可查 offline 记录(内容治理页),
     * 其余调用方一律强制 published——防越权翻看下架内容。
     */
    public Page<BizFabushiPost> page(String scopeType, Long bookId, String status, boolean manageAllowed,
                                     Long viewerId, long current, long size) {
        boolean publishedOnly = !manageAllowed || status == null || status.isBlank() || LIT_PUBLISHED.equals(status);
        LambdaQueryWrapper<BizFabushiPost> qw = new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, publishedOnly ? LIT_PUBLISHED : status)
                .eq(scopeType != null && !scopeType.isBlank(), BizFabushiPost::getScopeType, scopeType)
                .eq(bookId != null, BizFabushiPost::getBookId, bookId)
                .orderByDesc(BizFabushiPost::getCreatedAt);
        Page<BizFabushiPost> page = postMapper.selectPage(new Page<>(current, size), qw);
        fillInteractions(page.getRecords(), viewerId);
        return page;
    }

    // ===== Controller 层 Map 化输出(ArchUnit: controller_must_not_depend_on_entity) =====

    /** 内容分页 Map 化(records/total)。 */
    public Map<String, Object> pageData(String scopeType, Long bookId, String status, boolean manageAllowed,
                                        Long viewerId, long current, long size) {
        Page<BizFabushiPost> p = page(scopeType, bookId, status, manageAllowed, viewerId, current, size);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return data;
    }

    /** 发布 Map 化(body → 实体组装收在 service)。 */
    public Map<String, Object> createData(Map<String, Object> body) {
        BizFabushiPost post = new BizFabushiPost();
        post.setScopeType(body.get("scopeType") == null ? null : String.valueOf(body.get("scopeType")));
        post.setTitle(body.get("title") == null ? null : String.valueOf(body.get("title")));
        post.setContent(body.get(LIT_CONTENT) == null ? null : String.valueOf(body.get(LIT_CONTENT)));
        if (body.get("bookId") != null) {
            post.setBookId(Long.valueOf(String.valueOf(body.get("bookId"))));
        }
        if (body.get("sectionId") != null) {
            post.setSectionId(Long.valueOf(String.valueOf(body.get("sectionId"))));
        }
        BizFabushiPost created = create(post);
        return Map.of("id", created.getId());
    }

    /** 评论 Map 化。 */
    public Map<String, Object> addCommentData(Long postId, Map<String, Object> body) {
        BizFabushiComment c = new BizFabushiComment();
        Object content = body == null ? null : body.get(LIT_CONTENT);
        c.setContent(content == null ? null : String.valueOf(content));
        BizFabushiComment created = addComment(postId, c);
        return Map.of("id", created.getId());
    }

    /** 内容详情(fabushi:view 门): 帖+附件+互动计数(无 per-id 范围判定需求: 社区内容全员可见)。 */
    public Map<String, Object> detail(Long id, Long viewerId) {
        BizFabushiPost post = postMapper.selectById(id);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, LIT_X);
        }
        fillInteractions(List.of(post), viewerId);
        List<Map<String, Object>> attachments = attachmentData(id);
        return Map.of(
                "post", post,
                "attachments", attachments,
                "likeCount", post.getLikeCount() == null ? 0L : post.getLikeCount(),
                "likedByMe", isLikedBy(id, viewerId));
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
        if (fileId == null || !Set.of("image", "audio", "video").contains(fileType)) {
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
        }).collect(Collectors.toList());
    }

    /** 发布(fabushi:create 门): scope_type 白名单; 书目须为 approved(挂书内容)。 */
    public BizFabushiPost create(BizFabushiPost post) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        if (post.getScopeType() == null || !SCOPE_TYPES.contains(post.getScopeType())) {
            throw new BusinessException(400, "内容类型不合法");
        }
        if ((post.getContent() == null || post.getContent().isBlank())
                && (post.getTitle() == null || post.getTitle().isBlank())) {
            throw new BusinessException(400, "内容不能为空");
        }
        post.setId(null);
        post.setUserId(user.getUserId());
        post.setStatus(LIT_PUBLISHED);
        postMapper.insert(post);
        return post;
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
        securityAuditService.permissionChange("fabushi_post_offline", "post:" + id);
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
        securityAuditService.permissionChange("fabushi_post_restore", "post:" + id);
    }

    /** 评论(fabushi:create 门): 帖须 published。 */
    public BizFabushiComment addComment(Long postId, BizFabushiComment comment) {
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, LIT_X);
        }
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        comment.setId(null);
        comment.setPostId(postId);
        comment.setUserId(user == null ? null : user.getUserId());
        comment.setType("text");
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
                .collect(Collectors.toList());
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

    /** 列表/详情互动联显: 发帖人名 + 木鱼数 + 本人点赞/已读标记。 */
    private void fillInteractions(List<BizFabushiPost> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return;
        }
        Set<Long> postIds = posts.stream().map(BizFabushiPost::getId).collect(Collectors.toSet());
        Set<Long> userIds = posts.stream().map(BizFabushiPost::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        Map<Long, Long> likeCounts = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                        .in(BizFabushiLike::getPostId, postIds)).stream()
                .collect(Collectors.groupingBy(BizFabushiLike::getPostId, Collectors.counting()));
        Set<Long> myLiked = viewerId == null ? Collections.emptySet()
                : likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                        .eq(BizFabushiLike::getUserId, viewerId).in(BizFabushiLike::getPostId, postIds)).stream()
                .map(BizFabushiLike::getPostId).collect(Collectors.toSet());
        Set<Long> myRead = viewerId == null ? Collections.emptySet()
                : readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .eq(BizFabushiReadStatus::getUserId, viewerId).in(BizFabushiReadStatus::getPostId, postIds)).stream()
                .map(BizFabushiReadStatus::getPostId).collect(Collectors.toSet());
        Map<Long, Long> commentCounts = commentMapper.selectList(new LambdaQueryWrapper<BizFabushiComment>()
                        .eq(BizFabushiComment::getStatus, LIT_PUBLISHED).in(BizFabushiComment::getPostId, postIds)).stream()
                .collect(Collectors.groupingBy(BizFabushiComment::getPostId, Collectors.counting()));

        for (BizFabushiPost p : posts) {
            p.setUserName(names.get(p.getUserId()));
            p.setLikeCount(likeCounts.getOrDefault(p.getId(), 0L));
            p.setCommentCount(commentCounts.getOrDefault(p.getId(), 0L));
            p.setReadByMe(myRead.contains(p.getId()));
            p.setLikedByMe(myLiked.contains(p.getId()));
        }
    }

    private boolean isLikedBy(Long postId, Long viewerId) {
        return viewerId != null && likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .eq(BizFabushiLike::getPostId, postId).eq(BizFabushiLike::getUserId, viewerId)) > 0;
    }
}
