package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 【PR-24 2026-09-19】规则版本(append-only:记录规则参数每次变更)。 */
@Data
@TableName("sys_rule_version")
public class SysRuleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleKey;
    private String oldValue;
    private String newValue;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
}
