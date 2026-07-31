package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法人公司 — 集团下独立法人实体
 * 一个集团可以有多个法人公司，数据权限可按法人公司隔离
 */
@Data
@TableName("sys_legal_entity")
public class SysLegalEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 法人公司名称 */
    private String entityName;

    /** 法人公司编码（唯一，如：qyyd-bj） */
    private String entityCode;

    /** 所属集团ID */
    private Long groupId;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 描述 */
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
