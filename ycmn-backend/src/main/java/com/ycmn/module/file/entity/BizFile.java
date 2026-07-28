package com.ycmn.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_file")
public class BizFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long folderId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String ossKey;
    private String ossUrl;
    private String fileScope;
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String uploaderName;
}
