package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施内容已读记录(需求 B5; 唯一键 user_id+post_id 幂等, 照 biz_incentive_read 模式)。
 * NEW 徽章口径: created_at(内容) 30 天内 + 本记录不存在 → 未读新内容。
 */
@Data
@TableName("biz_fabushi_read_status")
public class BizFabushiReadStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private LocalDateTime readAt;
}
