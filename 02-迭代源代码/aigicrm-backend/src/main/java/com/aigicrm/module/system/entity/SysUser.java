package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String realName;
    private String phone;
    private String email;
    @JsonIgnore
    private String wechatOpenid;
    @JsonIgnore
    private String wechatUnionid;
    private String avatar;
    private Long deptId;
    private Long officeId;
    private Long orgId;
    /**
     * 直接上级（team 数据范围按 manager_id 反向递归）。
     * updateStrategy=ALWAYS：updateById 时 null 也进入 SET 子句，
     * 保证"清空上级"能真正回写 NULL（同 dataScopeOverride 的缺陷修复模式，
     * 默认 NOT_NULL 策略下 null 被跳过导致旧值残留）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long managerId;
    /**
     * 用户级数据范围覆盖（空/NULL=跟随角色默认；取值见 DataScopeType.code）
     * updateStrategy=ALWAYS：updateById 时 null 也进入 SET 子句，
     * 保证"清空=跟随角色"能真正回写 NULL，而非 NOT_NULL 默认策略下被跳过残留旧值
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dataScopeOverride;
    /** 离职状态（DB 列 varchar(20) 存中文标签如「离职」，工程门禁 G23 发现实体 Integer 与列类型冲突会读抛异常） */
    private String leaveStatus;
    private LocalDate leaveDate;
    private String leaveReason;
    /** 【2026-08-14 安全 P0】改密时刻：DB 级 token 撤销依据（早于该时刻签发的 token 一律失效，不依赖 Redis） */
    private LocalDateTime pwdChangedAt;
    /** 【2026-08-14 缓存自愈】权限版本号：任一权限变更 +1；缓存命中时与 DB 比对，防 evict 漏网残留 */
    private Integer permVersion;
    /** 【2026-08-14 安全 P0】首次登录强制改密标记（1=需改密；配合随机初始口令消除硬编码 123456） */
    private Integer pwdResetRequired;
    private Long storageQuota;
    private Long storageUsed;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String officeName;
    @TableField(exist = false)
    private String roleName;
    @TableField(exist = false)
    private Long roleId;
    @TableField(exist = false)
    private String managerName;
    /** 【2026-08-08 用户级 custom 授权】自定义数据范围时勾选的组织ID（瞬态，不入库） */
    @TableField(exist = false)
    private List<Long> orgIds;
    /** 【WP1.1 2026-11-13 多角色并集】角色ID列表（瞬态，不入库）；空时回退单值 roleId 兼容旧客户端 */
    @TableField(exist = false)
    private List<Long> roleIds;
}
