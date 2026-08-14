package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 集团 — 集团制权限体系的顶层组织单元 */
@Data
@TableName("sys_group")
public class SysGroup {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 集团名称（如：青阳耀东集团） */
    private String groupName;

    /** 集团编码（唯一） */
    private String groupCode;

    /** 集团描述 */
    private String description;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 0=禁用 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
