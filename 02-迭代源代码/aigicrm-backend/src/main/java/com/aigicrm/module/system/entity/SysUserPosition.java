package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户-岗位关联 */
@Data
@TableName("sys_user_position")
public class SysUserPosition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 岗位ID */
    private Long positionId;

    /** 是否主岗位 */
    private Integer isPrimary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
