package com.aigicrm.module.incentive.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("biz_incentive_category")
public class BizIncentiveCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private Integer score;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<BizIncentiveCategory> children;

    /** 分类角标：当前账号权限可见范围内的记录数（含子分类聚合，前端 cat._recordCount 展示） */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty("_recordCount")
    private Integer recordCount;
}
