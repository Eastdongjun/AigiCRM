package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表格列显示偏好（按人持久化到服务器，跨浏览器/设备生效）
 */
@Data
@TableName("sys_user_column_pref")
public class SysUserColumnPref {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 页面标识（如 incentive-record，对应前端表格页面） */
    private String pageKey;

    /** 显示列 key 数组（JSON 字符串，如 ["customer","industry"]；null=使用前端默认） */
    private String columns;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
