package com.ycmn.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_approval_node")
public class BizApprovalNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalId;
    private Long approverId;
    private Integer nodeOrder;
    private String action;
    private String comment;
    private LocalDateTime operatedAt;
    private Long configId;
    private String signMode;
    private String status;

    @TableField(exist = false)
    private String approverName;
}
