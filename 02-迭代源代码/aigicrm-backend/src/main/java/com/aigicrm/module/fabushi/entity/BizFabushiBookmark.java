package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【D19/D24 切片 7】阅读器页码书签（批1：标记/跳转/管理）。
 * 逻辑删复用 @TableLogic，与法布施其余实体口径一致；只存当前用户维度。
 */
@Data
@TableName("biz_fabushi_bookmark")
public class BizFabushiBookmark {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private Integer page;
    private String note;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
