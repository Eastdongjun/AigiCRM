package com.aigicrm.module.file.entity;

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
    /** 归属组织快照(上传时上传人组织节点, 转岗不漂移) */
    private Long orgId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private Integer version;        // 新增：版本号，覆盖替换+1，默认1
    private Long replaceBy;         // 新增：最近替换人
    private LocalDateTime replacedAt; // 新增：最近替换时间
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String uploaderName;
}
