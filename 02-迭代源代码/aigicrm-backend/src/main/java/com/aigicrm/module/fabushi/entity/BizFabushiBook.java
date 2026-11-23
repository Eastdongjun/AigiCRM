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
    /** 【D19 切片 7】版式标记：text=文本版（默认偏重排）/ scan=影印版（默认原样增强）。 */
    private String layoutType;
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
    /** 【P2 2026-11-23】该书参与人数（已发布内容的去重作者数），按书浏览左栏排序与「N 人」角标用。 */
    @TableField(exist = false)
    private Long memberCount;
    /**
     * 心得（=随施）条数。**历史命名**：小程序书目页（aigicrm-miniprogram/pages/fabushi/book）
     * 直接读 heartCount 并显示为「心得」，改名会断小程序，故保留原名；
     * 新代码请用语义正确的 {@link #suishiCount}（两者取值恒等）。
     */
    @TableField(exist = false)
    private Long heartCount;
    /**
     * 诵读条数。**历史命名**，同 {@link #heartCount} 的成因：小程序按 recitationCount 取诵读数；
     * 新代码请用 {@link #songduCount}（两者取值恒等）。
     */
    @TableField(exist = false)
    private Long recitationCount;

    /** 【P1 2026-11-23】该书 D1 三类型内容条数，右栏「今日推荐书目」按原型展示
     *  「诵读 N · 随施 N · 再读 N 人 · 复诵 N」一行，不再只给总条数。 */
    @TableField(exist = false)
    private Long songduCount;
    @TableField(exist = false)
    private Long suishiCount;
    @TableField(exist = false)
    private Long zaiduCount;
    /** 【P1 2026-11-23】正在「再读」本书的去重人数（原型文案「再读 N 人」按人计，不是条数）。 */
    @TableField(exist = false)
    private Long zaiduMemberCount;
    /** 【P1 2026-11-23】该书全部内容的复诵总次数（他人对本书内容的复诵行数）。 */
    @TableField(exist = false)
    private Long recitationTotal;
}
