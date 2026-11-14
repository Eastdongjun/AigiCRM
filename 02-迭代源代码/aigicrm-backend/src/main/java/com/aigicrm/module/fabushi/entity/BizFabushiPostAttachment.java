package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 法布施内容附件(图/音/视; file_id 指向 scope=fabushi 的 biz_file, 不进文件管理列表)。 */
@Data
@TableName("biz_fabushi_post_attachment")
public class BizFabushiPostAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String fileType;
    private Long fileId;
    private Integer sortOrder;
    private Integer durationSeconds;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
