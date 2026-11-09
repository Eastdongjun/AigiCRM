package com.aigicrm.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【2026-11-09 类型级默认权限】文件空间(公共/个人)默认权限行。
 * perms 词表 = 现行 6 短名(view/upload/edit/delete/share/download, 与 biz_folder_acl 同款)
 * + create(可建该类型根文件夹)。新建文件夹时除 create 外物化进 biz_folder_acl,
 * 可逐夹微调; tenant_id 列 DB 默认 1 不映射(与 BizFolderAcl 同款)。
 */
@Data
@TableName("biz_folder_type_default")
public class BizFolderTypeDefault {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String folderType;   // public | private
    private Long roleId;
    private String perms;        // CSV
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
