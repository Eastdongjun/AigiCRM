package com.aigicrm.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_approval_config")
public class BizApprovalConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowName;
    private String flowType;
    private String approverType;   // USER / ROLE / DEPT
    private String approverIds;    // JSON: [1,2,3]
    private String signMode;       // ALL=会签 / ANY=非会签
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
