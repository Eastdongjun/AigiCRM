package com.aigicrm.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【2026-11-09 文件权限 v2】文件空间(公共/私人)类型授权行。
 * 主体 ROLE|USER(V2026.11.09.5 扩展): 管理员可按角色整批或直接勾某个人。
 * perms 词表 = 8 细动作(view,upload,rename,replace,delete,share,download,create,
 * 与逐夹 ACL 的 6 短名同源但把 edit 伞拆成 rename/replace 两项; create=可建该类型根文件夹)。
 * 判定层实时生效(FolderPermissionService.typeGranted), 不再建夹时物化进 biz_folder_acl;
 * 存量行的 edit 由判定层读时展开为 rename+replace, 数据不回填。tenant_id 列 DB 默认 1 不映射(与 BizFolderAcl 同款)。
 */
@Data
@TableName("biz_folder_type_default")
public class BizFolderTypeDefault {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String folderType;   // public | private
    private String subjectType;  // ROLE | USER
    private Long subjectId;      // sys_role.id | sys_user.id
    private String perms;        // CSV
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
