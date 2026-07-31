package com.ycmn.module.sea.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_sea_config")
public class BizSeaConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String poolName;
    private String regionIds;       // 派生办事处ID缓存(逗号分隔,dept_type=3),由 visibleDeptIds/visibleUserIds 派生,用于释放路由
    private String viewRoleIds;      // 已废弃,保留列不再使用
    private String visibleDeptIds;   // 可见部门ID(逗号分隔,dept_type=4)
    private String visibleUserIds;   // 可见人员ID(逗号分隔)
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String regionNames;      // 派生办事处名称(展示用)
    @TableField(exist = false)
    private String viewRoleNames;
    @TableField(exist = false)
    private String visibleNames;     // 可见部门+人员名称拼接(展示用)
}
