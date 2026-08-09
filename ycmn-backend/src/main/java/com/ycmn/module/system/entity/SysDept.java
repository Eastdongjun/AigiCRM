package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_dept")
public class SysDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    /** 所属集团ID */
    private Long groupId;
    /** 所属法人公司ID */
    private Long legalEntityId;
    private String deptName;
    private String deptCode;
    private Integer deptType;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<SysDept> children;
    @TableField(exist = false)
    private String leaderName;
}
