package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict_type")
public class SysDictType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictName;
    private String dictCode;
    /** 【2026-11-16】字典模块分类：customer/file/incentive/service/sea/workflow/score/fabushi/statistics/system/common */
    private String moduleCode;
    private Integer status;
    private Integer builtIn;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
