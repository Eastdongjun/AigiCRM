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
 * 【A2 2026-11-18】+space_type（SPACE 空间类型，NULL=任意空间）/scope_mode（ANY/RELATIVE/EXACT/SUBTREE）
 * /source（manual/legacy/dual_write/migration）/legacy_ref（旧表迁移来源 ftd:&lt;id&gt;:&lt;action&gt;）。
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
    /** SPACE 空间类型 public/private/incentive/fabushi；NULL=任意空间（兼容旧 blank 迁移行） */
    private String spaceType;
    private String action;
    /** ALLOW/DENY */
    private String effect;
    private Integer inherit;
    private Long scopeOrgId;
    /** RELATIVE 档位 self/dept/office/region（相对当前查看人的数据范围，非空间类型） */
    private String scopeLevel;
    /** ANY/RELATIVE/EXACT/SUBTREE；legacy 迁移行=ANY/RELATIVE，手工新授权默认 SUBTREE */
    private String scopeMode;
    private LocalDateTime expireAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    /** 旧表迁移来源 ftd:<旧id>:<动作>，双写 upsert 幂等键；手工行为 NULL */
    private String legacyRef;
    /** manual/legacy/dual_write/migration */
    private String source;
}
