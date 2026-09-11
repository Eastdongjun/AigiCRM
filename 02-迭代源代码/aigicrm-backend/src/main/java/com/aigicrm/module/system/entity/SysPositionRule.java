package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/** 岗位-规则关联 */
@Data
@TableName("sys_position_rule")
public class SysPositionRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long positionId;
    private Long ruleId;
}
