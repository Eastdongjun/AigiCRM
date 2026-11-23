package com.aigicrm.module.fabushi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法布施书架分组（D20 2026-11-23）：
 * user_id=0 的 auto 行=10 大类型自动组模板（只读展开给全用户）；
 * user_id>0 的 custom 行=用户自建分组，可增删改名。
 */
@Data
@TableName("biz_fabushi_shelf_group")
public class BizFabushiShelfGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String groupType;
    private String categoryCode;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Long bookCount;
}
