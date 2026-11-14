package com.aigicrm.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【V2026.11.14.1 版本历史】replace 归档的旧版本内容行。
 * 主行(biz_file)恒指向最新版本; replace 时把主行的旧内容(key/url/size/type)归档到此表,
 * 字节不再物理删除——版本列表/下载/回滚均以本表为源。version_no=被替换时主行的 version 值。
 */
@Data
@TableName("biz_file_version")
public class BizFileVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long fileId;
    private Integer versionNo;
    private String ossKey;
    private String ossUrl;
    private Long fileSize;
    private String fileType;
    private String fileName;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
