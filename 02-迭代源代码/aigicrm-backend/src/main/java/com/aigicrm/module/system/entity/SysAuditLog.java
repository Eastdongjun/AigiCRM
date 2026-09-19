package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 【BS-R 2026-09-19】安全审计日志(append-only:库层触发器拒绝 UPDATE/DELETE,仅允许 INSERT)。 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private Long actorId;
    private String actorUsername;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
