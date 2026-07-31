package com.ycmn.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_approval_flow")
public class BizApprovalFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowType;
    private String flowName;
    private Long approverRoleId;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
}
