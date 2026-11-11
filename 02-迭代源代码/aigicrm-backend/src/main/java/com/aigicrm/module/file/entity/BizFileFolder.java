package com.aigicrm.module.file.entity;

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
    /** 归属组织快照(建夹时创建人组织节点; 存量公共夹归总部) */
    private Long orgId;
    private Integer sortOrder;
    private Integer downloadable;   // 新增：下载开关 1=允许 0=仅预览（默认1）
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<BizFileFolder> children;
    @TableField(exist = false)
    private Integer fileCount;
    @TableField(exist = false)
    private List<String> perms;   // 当前用户对该文件夹的权限列表（前端逐行按钮控制）
    /** 【修复 2026-11-12 根因5】夹自身动作视图(rename/delete 细词), 与 perms(文件动作词)分字段防词义混叠 */
    @TableField(exist = false)
    private List<String> folderActions;
}
