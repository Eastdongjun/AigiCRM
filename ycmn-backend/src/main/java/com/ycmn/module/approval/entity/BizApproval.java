package com.ycmn.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_approval")
public class BizApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalCode;
    private String approvalType;
    private Long applicantId;
    private Long customerId;
    private String currentNode;
    private String actionAfterApprove;
    private String extraData;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String applicantName;
    @TableField(exist = false)
    private String customerName;
}
