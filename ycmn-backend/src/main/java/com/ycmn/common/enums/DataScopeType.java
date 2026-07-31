package com.ycmn.common.enums;

import lombok.Getter;

/**
 * 数据权限范围枚举
 * 参考若依 5 级模型，精简 org/dept 统一为 org
 *
 * 优先级: all < custom < org_and_children < org < self
 */
@Getter
public enum DataScopeType {

    /** 全部数据 */
    ALL("all", "全部数据"),

    /** 自定义（通过 sys_role_org 表配置具体组织范围） */
    CUSTOM("custom", "自定义数据"),

    /** 本组织及所有下级（递归） */
    ORG_AND_CHILDREN("org_and_children", "本组织及下级"),

    /** 仅本组织 */
    ORG("org", "仅本组织"),

    /** 仅本人创建的数据 */
    SELF("self", "仅本人");

    private final String code;
    private final String label;

    DataScopeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据 code 获取枚举，null 或未知值默认返回 SELF
     */
    public static DataScopeType fromCode(String code) {
        if (code == null) return SELF;
        for (DataScopeType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return SELF;
    }

    /**
     * 获取优先级（数字越小越宽松，权限越大）
     * 用于多角色叠加时取最高权限
     */
    public int rank() {
        return this.ordinal();
    }

    /**
     * 是否需要递归查询子组织
     */
    public boolean needChildren() {
        return this == ORG_AND_CHILDREN;
    }
}
