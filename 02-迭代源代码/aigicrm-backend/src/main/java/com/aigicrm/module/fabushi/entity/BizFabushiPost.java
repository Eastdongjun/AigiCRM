package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施统一内容(一期 2026-11-15; D1 2026-11-23 类型重构): 每日诵读songdu/每日随施suishi/再读一本zaidu 共用一张表,
 * scope_type 区分(V2026.11.16.1 建表; V2026.11.23.1 迁移; 单源枚举 FabushiScopeType)。
 * 下架留痕: status=offline 时 offline_by/at/reason 必填(内容治理 fabushi:content:manage)。
 */
@Data
@TableName("biz_fabushi_post")
public class BizFabushiPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;
    private Long bookId;
    private Long sectionId;
    private Long userId;
    private String title;
    private String content;
    private String status;
    private Long offlineBy;
    private LocalDateTime offlineAt;
    private String offlineReason;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String userName;
    /** 【P2 2026-11-23】关联书名。此前只声明未装配（恒 null，前端靠本地书目表兜底），fillInteractions 批量补。 */
    @TableField(exist = false)
    private String bookTitle;
    /** 【P2 2026-11-23】关联章节标题（biz_fabushi_section.title），按书浏览表格「章节」列用；无章节=null。 */
    @TableField(exist = false)
    private String sectionTitle;
    @TableField(exist = false)
    private Long likeCount;
    @TableField(exist = false)
    private Long commentCount;
    @TableField(exist = false)
    private Boolean readByMe;
    @TableField(exist = false)
    private Boolean likedByMe;
    /** 【F1 2026-11-19 spec v2】"一月内上传显示 NEW"——30 天窗口, fillInteractions 装配 */
    @TableField(exist = false)
    private Boolean isNew;
    /** 【D14 2026-11-23】复诵计数（biz_fabushi_recitation 行数, fillInteractions 装配） */
    @TableField(exist = false)
    private Long recitationCount;
    /** 【D21 2026-11-23】朋友圈式点赞人名单（前 8 位昵称+总数, fillInteractions 装配） */
    @TableField(exist = false)
    private String likeNames;
    /** 【D21 2026-11-23】朋友圈式图片网格（pageData 批量组装, 仅 image 附件） */
    @TableField(exist = false)
    private java.util.List<BizFabushiPostAttachment> attachments;
}
