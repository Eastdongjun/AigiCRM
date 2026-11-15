package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_fabushi_activity")
public class BizFabushiActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long bookId;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long createdBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
