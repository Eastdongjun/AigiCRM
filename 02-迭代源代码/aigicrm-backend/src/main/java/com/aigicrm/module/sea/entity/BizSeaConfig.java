package com.aigicrm.module.sea.entity;

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
    private String visibleDeptIds;   // 可见部门ID(逗号分隔,dept_type=4) = 查看权限
    private String visibleUserIds;   // 可见人员ID(逗号分隔) = 查看权限
    // 【2026-08-10 池级权限】编辑/删除/导入/捞取 各组织范围(逗号分隔, 勾选上级含下级递归)
    private String editDeptIds;
    private String editUserIds;
    private String deleteDeptIds;
    private String deleteUserIds;
    private String importDeptIds;
    private String importUserIds;
    private String claimDeptIds;
    private String claimUserIds;
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
    // 【2026-08-10 池级权限】各权限名称拼接(展示用)
    @TableField(exist = false)
    private String editNames;
    @TableField(exist = false)
    private String deleteNames;
    @TableField(exist = false)
    private String importNames;
    @TableField(exist = false)
    private String claimNames;
}
