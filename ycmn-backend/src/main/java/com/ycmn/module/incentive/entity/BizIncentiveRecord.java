package com.ycmn.module.incentive.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

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
    private Long fileId;
    private String preInstallQuestion;
    private Long filePreId;
    private Long fileMidId;
    private Long filePostId;
    private String remark;
    private Integer score;
    private Integer shared;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String userName;
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
}
