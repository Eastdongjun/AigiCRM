package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiRecitation;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiRecitationMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 法布施复诵服务（D14 2026-11-23，Q6 拍板=独立实体表）：
 * 他人对已发布内容（三类型皆可）的朗读/背诵，同人同帖可多次（计数=行数）；
 * 复诵不计入本人每日诵读完成度（防刷口径，只进复诵统计）。
 * 音频权限链：复诵音频= published 帖下的第四类社区放行（isFabushiCommunityFile，本批同步）。
 */
@Service
@RequiredArgsConstructor
public class FabushiRecitationService {
    private static final String LIT_PUBLISHED = "published";
    /** 【FABUSHI-DELTA-20261128 R1】单段上限 5 分钟(300s); 前端录制 duration 同批改 300000ms。 */
    private static final int MAX_SECONDS = 300;

    private final BizFabushiRecitationMapper recitationMapper;
    private final BizFabushiPostMapper postMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SecurityAuditService securityAuditService;
    private final com.aigicrm.module.system.service.RecycleBinService recycleBinService;

    /** 复诵(fabushi:create 门): 帖须 published；audio 必挂 fabushi 空间；≤300s；同人同帖可多次。 */
    @Transactional
    public BizFabushiRecitation create(Long postId, Long audioFileId, Integer durationSeconds) {
        return doCreate(postId, audioFileId, durationSeconds);
    }

    /**
     * 复诵落库核心逻辑（无事务注解）。
     * 【S6809 2026-11-23】原 createData() 直接自调本类 @Transactional 的 create()，Spring 代理被绕过，
     * 内层事务不生效（外层 createData 的事务在兜底，属"侥幸正确"）。改为两入口都调本私有方法，
     * 事务只由各自的入口注解持有，不再出现同类事务方法自调。
     */
    private BizFabushiRecitation doCreate(Long postId, Long audioFileId, Integer durationSeconds) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        BizFabushiPost post = postMapper.selectById(postId);
        if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, "内容不存在或已下架");
        }
        if (audioFileId == null) {
            throw new BusinessException(400, "复诵缺少音频文件");
        }
        if (durationSeconds != null && durationSeconds > MAX_SECONDS) {
            throw new BusinessException(400, "复诵不能超过 5 分钟");
        }
        BizFile file = fileMapper.selectById(audioFileId);
        if (file == null || (file.getDeleted() != null && file.getDeleted() == 1)) {
            throw new BusinessException(404, "文件不存在");
        }
        if (!"fabushi".equals(file.getFileScope())) {
            throw new BusinessException(400, "复诵音频须上传到法布施空间");
        }
        BizFabushiRecitation row = new BizFabushiRecitation();
        row.setPostId(postId);
        row.setUserId(user.getUserId());
        row.setAudioFileId(audioFileId);
        row.setDurationSeconds(durationSeconds);
        recitationMapper.insert(row);
        securityAuditService.permissionChange("fabushi_recitation", "post:" + postId);
        return row;
    }

    /** 复诵 Map 化（controller 脱实体；ArchUnit 约束）。事务在本入口，落库走 doCreate。 */
    @Transactional
    public Map<String, Object> createData(Long postId, Map<String, Object> body) {
        Long audioFileId = body == null || body.get("audioFileId") == null
                ? null : Long.valueOf(String.valueOf(body.get("audioFileId")));
        Integer durationSeconds = body == null || body.get("durationSeconds") == null
                ? null : Integer.parseInt(String.valueOf(body.get("durationSeconds")));
        BizFabushiRecitation row = doCreate(postId, audioFileId, durationSeconds);
        return Map.of("id", row.getId());
    }

    /** 复诵列表(fabushi:view 门): published 帖下按时间倒序, 联复诵人姓名。 */
    public List<BizFabushiRecitation> list(Long postId) {
        List<BizFabushiRecitation> rows = recitationMapper.selectList(new LambdaQueryWrapper<BizFabushiRecitation>()
                .eq(BizFabushiRecitation::getPostId, postId)
                .orderByDesc(BizFabushiRecitation::getCreatedAt));
        if (!rows.isEmpty()) {
            Set<Long> uids = rows.stream().map(BizFabushiRecitation::getUserId).collect(Collectors.toSet());
            Map<Long, String> names = userMapper.selectByIds(uids).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
            rows.forEach(r -> r.setUserName(names.get(r.getUserId())));
        }
        return rows;
    }

    /** 列表 Map 化(controller 脱实体; LocalDateTime ISO)。 */
    public List<Map<String, Object>> listData(Long postId) {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return list(postId).stream()
                .<Map<String, Object>>map(r -> om.convertValue(r,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                .toList();
    }

    /** 批量计数（feed/详情 fill 用；post 集为空直接空 Map）。 */
    public Map<Long, Long> countByPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return recitationMapper.selectList(new LambdaQueryWrapper<BizFabushiRecitation>()
                        .in(BizFabushiRecitation::getPostId, postIds)).stream()
                .collect(Collectors.groupingBy(BizFabushiRecitation::getPostId, Collectors.counting()));
    }

    /**
     * 【FABUSHI-DELTA-20261128 R5】复诵移除(发起人本人)：行逻辑删 + 音频服务端软删(回收站+审计)。
     * 不走 FileMutationService.deleteFile：fabushi scope 删除要求 fabushi:file:delete 码
     * (canDeleteFabushiFile)，发起人通常没有——此处授权由「发起人本人」校验承担，
     * 音频处理结果与删除链同口径(deleted=1 + 回收站记录 + 审计；发起人=上传者)。
     */
    @Transactional
    public Map<String, Object> removeData(Long postId, Long recitationId) {
        com.aigicrm.security.SecurityUser user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        BizFabushiRecitation row = recitationMapper.selectById(recitationId);
        if (row == null || !postId.equals(row.getPostId())) {
            throw new BusinessException(404, "复诵不存在");
        }
        if (!user.getUserId().equals(row.getUserId())) {
            throw new BusinessException(403, "只能移除自己的复诵");
        }
        recitationMapper.deleteById(recitationId);
        BizFile audio = fileMapper.selectById(row.getAudioFileId());
        if (audio != null && (audio.getDeleted() == null || audio.getDeleted() != 1)) {
            // 先填删除人/时间再翻删除标志（与文件删除链同口径：「我的回收站」归属依赖 deleted_by）
            BizFile patch = new BizFile();
            patch.setId(audio.getId());
            patch.setDeletedAt(java.time.LocalDateTime.now());
            patch.setDeletedBy(user.getUserId());
            fileMapper.updateById(patch);
            fileMapper.deleteById(audio.getId());
            recycleBinService.record("file", audio.getId(), java.util.Map.of(
                    "id", audio.getId(),
                    "fileName", String.valueOf(audio.getFileName()),
                    "fileSize", String.valueOf(audio.getFileSize()),
                    "fileScope", String.valueOf(audio.getFileScope()),
                    "uploaderId", String.valueOf(audio.getUploaderId())
            ), "复诵移除", "user");
        }
        securityAuditService.permissionChange("fabushi_recitation_remove",
                "post:" + postId + " recitation:" + recitationId + " by:" + user.getUserId());
        return java.util.Map.of("id", recitationId);
    }
}
