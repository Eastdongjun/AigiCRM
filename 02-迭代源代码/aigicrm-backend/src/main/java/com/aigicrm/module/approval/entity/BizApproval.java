package com.aigicrm.module.approval.entity;

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
    /** 【11-08 修复清单9】当前节点(状态=pending)审批人==登录用户; 列表与详情均填充, 前端 MP 同批消费 */
    @TableField(exist = false)
    private Boolean currentApprover;
}
