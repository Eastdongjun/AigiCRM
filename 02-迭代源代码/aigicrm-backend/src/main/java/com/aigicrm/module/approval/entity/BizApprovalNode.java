package com.aigicrm.module.approval.entity;

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

    /** 【PR-03 2026-09-19】审批人已停用/删除(在途卡单可见性):pending/waiting 节点为 true 时,
     * 该节点当前无人可办,需管理员转交或恢复账号(转交规则待业务确认,引擎不自动处置)。 */
    @TableField(exist = false)
    private Boolean approverInvalid;
}
