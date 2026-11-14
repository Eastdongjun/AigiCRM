package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 法布施木鱼点赞(点赞=敲木鱼, 需求 D4+C4 木鱼双用; 唯一键 post_id+user_id 幂等)。 */
@Data
@TableName("biz_fabushi_like")
public class BizFabushiLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
