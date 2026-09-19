package com.aigicrm.module.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 【PR-10 2026-09-19】任务实体:跟进记录衔接的待办事项。
 * 口径:提醒已读 ≠ 任务完成;完成必须记录结果(completion_note)。
 */
@Data
@TableName("biz_task")
public class BizTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String title;
    private String detail;
    private Long assigneeId;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime completedAt;
    private String completionNote;
    private Long sourceLogId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    private Long tenantId;

    @TableField(exist = false)
    private String assigneeName;
    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private Boolean overdue;
}
