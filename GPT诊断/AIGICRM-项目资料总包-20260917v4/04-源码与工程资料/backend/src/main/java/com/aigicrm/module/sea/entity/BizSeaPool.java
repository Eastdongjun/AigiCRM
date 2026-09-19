package com.aigicrm.module.sea.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_sea_pool")
public class BizSeaPool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long poolId;
    private Long originalOwnerId;
    private String enterReason;
    private LocalDateTime enterDate;
    private Long claimUserId;
    private LocalDateTime claimDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private String customerCode;
    @TableField(exist = false)
    private String industry;
    @TableField(exist = false)
    private String originalOwnerName;
    @TableField(exist = false)
    private String region;
    // 【2026-08-10 池级权限】当前用户对该池的操作权限(前端按钮用)
    @TableField(exist = false)
    private Boolean canEdit;
    @TableField(exist = false)
    private Boolean canDelete;
    @TableField(exist = false)
    private Boolean canClaim;
}
