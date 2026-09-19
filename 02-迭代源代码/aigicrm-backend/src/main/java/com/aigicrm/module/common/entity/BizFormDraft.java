package com.aigicrm.module.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 【议题四裁决 2026-09-19】表单草稿(7 天保留/账号+集团隔离/版本冲突检测)。 */
@Data
@TableName("biz_form_draft")
public class BizFormDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long groupId;
    private String draftType;
    private String draftData;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
