package com.ycmn.module.incentive.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("biz_incentive_record")
public class BizIncentiveRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long categoryId;
    private Long customerId;
    private String period;
    private String industry;            // 行业(取字典 industry)
    private String relatedProducts;     // 相关产品(多行文本)
    private String specificProduct;   // 具体产品（可选，仅 trial 分类使用）
    private String fileId;
    private String preInstallQuestion;
    private String filePreId;
    private String fileMidId;
    private String filePostId;
    private String fileTrackId;
    private String remark;
    private Integer score;
    private Integer shared;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;             // 最后修改人ID（审计，对齐 biz_customer_indicator.updated_by）

    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String updatedByName;       // 最后修改人姓名（查询时回填）
    @TableField(exist = false)
    private String categoryName;
    @TableField(exist = false)
    private String fileName;
    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private String filePreName;
    @TableField(exist = false)
    private String fileMidName;
    @TableField(exist = false)
    private String filePostName;
    @TableField(exist = false)
    private String fileTrackName;
    @TableField(exist = false)
    private java.time.LocalDate lastInvoiceDate;
    @TableField(exist = false)
    private Long overdueDays;

    // ===== JSON 工具方法 =====

    /** 解析 JSON 数组为 Long 列表 */
    private static List<Long> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        try {
            // 格式: "[1,2,3]" 或 "1,2,3"
            String inner = json.trim();
            if (inner.startsWith("[")) inner = inner.substring(1, inner.length() - 1);
            if (inner.isBlank()) return List.of();
            return java.util.Arrays.stream(inner.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 将 Long 列表序列化为 JSON 数组字符串 */
    private static String toJsonArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return "[" + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    /** 判断某个字段是否有文件 */
    public boolean hasFiles(String field) {
        return !parseJsonArray(field).isEmpty();
    }

    /** 获取任意槽位是否有文件 */
    public boolean hasAnyFile() {
        return hasFiles(fileId) || hasFiles(filePreId) || hasFiles(fileMidId)
            || hasFiles(filePostId) || hasFiles(fileTrackId);
    }

    /** 设置 pre 文件列表 */
    public void setFilePreIds(List<Long> ids) { this.filePreId = toJsonArray(ids); }
    public List<Long> getFilePreIds() { return parseJsonArray(filePreId); }
    public void setFileMidIds(List<Long> ids) { this.fileMidId = toJsonArray(ids); }
    public List<Long> getFileMidIds() { return parseJsonArray(fileMidId); }
    public void setFilePostIds(List<Long> ids) { this.filePostId = toJsonArray(ids); }
    public List<Long> getFilePostIds() { return parseJsonArray(filePostId); }
    public void setFileTrackIds(List<Long> ids) { this.fileTrackId = toJsonArray(ids); }
    public List<Long> getFileTrackIds() { return parseJsonArray(fileTrackId); }
    public void setFileIds(List<Long> ids) { this.fileId = toJsonArray(ids); }
    public List<Long> getFileIds() { return parseJsonArray(fileId); }
}
