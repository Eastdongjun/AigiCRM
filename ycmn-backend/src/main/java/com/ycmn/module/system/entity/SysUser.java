package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private Long officeId;
    private Long orgId;
    private Long managerId;
    /** 用户级数据范围覆盖（空/NULL=跟随角色默认；取值见 DataScopeType.code） */
    private String dataScopeOverride;
    private Integer leaveStatus;
    private LocalDate leaveDate;
    private String leaveReason;
    private Long storageQuota;
    private Long storageUsed;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String officeName;
    @TableField(exist = false)
    private String roleName;
    @TableField(exist = false)
    private Long roleId;
    @TableField(exist = false)
    private String managerName;
}
