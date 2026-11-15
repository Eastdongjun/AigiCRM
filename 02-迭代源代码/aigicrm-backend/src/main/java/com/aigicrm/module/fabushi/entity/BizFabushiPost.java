package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施统一内容(一期 2026-11-15): 心得heart/诵读recitation/正能量znl 共用一张表,
 * scope_type 区分(V2026.11.16.1; 需求 G4 统一内容表裁决)。
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
    @TableField(exist = false)
    private String bookTitle;
    @TableField(exist = false)
    private Long likeCount;
    @TableField(exist = false)
    private Long commentCount;
    @TableField(exist = false)
    private Boolean readByMe;
    @TableField(exist = false)
    private Boolean likedByMe;
}
