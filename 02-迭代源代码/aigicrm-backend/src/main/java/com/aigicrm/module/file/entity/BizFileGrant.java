package com.aigicrm.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【2026-11-17 阶段二】文件统一授权行。
 * 与 V2026.11.17.1 的 biz_file_grant 表对应；ALLOW/DENY + 继承 + 过期 + 组织范围。
 * 现有 biz_folder_type_default 继续保留（灰度期双读，DENY 优先，ALLOW 作为例外通道）。
 */
@Data
@TableName("biz_file_grant")
public class BizFileGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** ROLE/POSITION/USER */
    private String subjectType;
    private Long subjectId;
    /** SPACE/FOLDER/FILE */
    private String objectType;
    private Long objectId;
    private String action;
    /** ALLOW/DENY */
    private String effect;
    private Integer inherit;
    private Long scopeOrgId;
    private String scopeLevel;
    private LocalDateTime expireAt;
    private Long createdBy;
    private LocalDateTime createdAt;
}
