package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 法布施统一评论(text文字/highlight重点/audio语音跟读 共用, 一期 text 先行)。 */
@Data
@TableName("biz_fabushi_comment")
public class BizFabushiComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String type;
    private String content;
    private Long audioFileId;
    private Long parentCommentId;
    private String status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String userName;
}
