package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_org")
public class SysOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    /** 所属集团ID */
    private Long groupId;
    /** 所属法人公司ID */
    private Long legalEntityId;
    private String orgName;
    private String orgCode;
    private String orgType;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<SysOrg> children;
    @TableField(exist = false)
    private String leaderName;
}
