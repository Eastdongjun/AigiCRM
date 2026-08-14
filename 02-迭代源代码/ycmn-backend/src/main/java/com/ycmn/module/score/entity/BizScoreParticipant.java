package com.ycmn.module.score.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 积分录入周期参与同事名单：持久化某周期表格里显示的同事列，
 * 保证保存后重新打开时列（同事数量）与保存时一致。
 */
@Data
@TableName("biz_score_participant")
public class BizScoreParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String period;
    private Long userId;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
