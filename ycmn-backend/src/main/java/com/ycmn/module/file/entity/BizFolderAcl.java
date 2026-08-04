package com.ycmn.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_folder_acl")
public class BizFolderAcl {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long folderId;
    private Long roleId;
    private String perms;
    private Long createdBy;
    private LocalDateTime createdAt;
}
