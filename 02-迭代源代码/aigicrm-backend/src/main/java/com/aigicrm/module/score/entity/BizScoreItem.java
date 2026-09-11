package com.aigicrm.module.score.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_score_item")
public class BizScoreItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String product;
    private Integer scoreMin;
    private Integer scoreMax;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 大类显示名（非DB字段） */
    @TableField(exist = false)
    private String categoryName;
}
