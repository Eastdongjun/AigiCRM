package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private String dataScope;
    private Long orgId;

    /** 集团归属ID(默认1=主集团;列表按集团边界过滤) */
    private Long groupId;

    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private java.util.List<Long> menuIds;

    /** custom 类型时选中的组织ID列表 */
    @TableField(exist = false)
    private java.util.List<Long> orgIds;
}
