package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private Long officeId;
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
}
