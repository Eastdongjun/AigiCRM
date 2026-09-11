package com.aigicrm.module.score.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_score_entry")
public class BizScoreEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String period;
    private Long itemId;
    private Long userId;
    private Integer score;
    private Long scorerId;
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
