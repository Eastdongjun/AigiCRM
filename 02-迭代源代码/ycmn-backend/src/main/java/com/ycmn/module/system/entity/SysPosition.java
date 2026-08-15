package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 岗位 — 一人多岗的数据载体 */
@Data
@TableName("sys_position")
public class SysPosition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位名称（如：华东大区经理） */
    private String name;

    /** 岗位编码（唯一标识） */
    private String code;

    private String description;

    private Long orgId;

    /** 集团归属ID(默认1=主集团;列表按集团边界过滤) */
    private Long groupId;

    /** 状态 0=禁用 1=启用 */
    private Integer status;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联的规则ID列表（非数据库字段） */
    @TableField(exist = false)
    private java.util.List<Long> ruleIds;
}
