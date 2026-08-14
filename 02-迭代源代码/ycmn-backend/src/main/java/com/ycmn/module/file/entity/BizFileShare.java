package com.ycmn.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_file_share")
public class BizFileShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Long sharedToUserId;
    private Long sharedByUserId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String sharedToUserName;
    @TableField(exist = false)
    private String fileName;
}
