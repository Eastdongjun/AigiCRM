package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施书目(一期 2026-11-15): 类别=字典 fabushi_category; 人人可提交(pending), admin 审核后展示。
 * PDF 挂载: pdf_file_id 指向 scope=fabushi 的 biz_file 行(上传走 /api/file/upload?scope=fabushi)。
 */
@Data
@TableName("biz_fabushi_book")
public class BizFabushiBook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private String title;
    private String author;
    private String publisher;
    private String editionType;
    private Integer pages;
    private Long pdfFileId;
    private String status;
    private String rejectReason;
    private Long createdBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String submitterName;
    @TableField(exist = false)
    private String categoryName;
    @TableField(exist = false)
    private Long postCount;
    @TableField(exist = false)
    private Long heartCount;
    @TableField(exist = false)
    private Long recitationCount;
}
