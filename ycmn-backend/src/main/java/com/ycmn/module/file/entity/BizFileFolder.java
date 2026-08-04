package com.ycmn.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("biz_file_folder")
public class BizFileFolder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String folderName;
    private String folderType;
    private Long ownerId;
    private Integer sortOrder;
    private Integer downloadable;   // 新增：下载开关 1=允许 0=仅预览（默认1）
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<BizFileFolder> children;
    @TableField(exist = false)
    private Integer fileCount;
}
