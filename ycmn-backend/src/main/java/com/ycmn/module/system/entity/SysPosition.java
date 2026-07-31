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

    /** 岗位描述 */
    private String description;

    /** 所属组织ID */
    private Long orgId;

    /** 状态 0=禁用 1=启用 */
    private Integer status;

    /** 排序 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联的规则ID列表（非数据库字段） */
    @TableField(exist = false)
    private java.util.List<Long> ruleIds;
}
