package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_fabushi_user_setting")
public class BizFabushiUserSetting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer reminderEnabled;
    private String freqCode;
    private String remindTime;
    private Integer soundEnabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
