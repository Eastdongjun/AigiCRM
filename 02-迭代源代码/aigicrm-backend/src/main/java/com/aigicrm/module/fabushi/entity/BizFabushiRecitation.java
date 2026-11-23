package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施复诵记录（D14 2026-11-23，Q6 拍板=独立实体表）：他人对已发布内容的朗读/背诵。
 * 同人同帖可多次（每次一行，计数=行数）；复诵不计入本人每日诵读完成度（防刷口径）。
 */
@Data
@TableName("biz_fabushi_recitation")
public class BizFabushiRecitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private Long audioFileId;
    private Integer durationSeconds;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String userName;
}
