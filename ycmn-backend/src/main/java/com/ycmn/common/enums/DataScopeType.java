package com.ycmn.common.enums;

import lombok.Getter;

/**
 * 数据权限范围枚举（六级）
 *
 * 声明序即宽松度降序: all < custom < region < office < dept < team < self
 * rank() 即 ordinal，用于多角色叠加时取最高权限（数字越小越宽松）
 *
 * 兼容说明: ORG_AND_CHILDREN/ORG 为旧值遗留常量（@Deprecated），
 * 由调用方迁移为 dept 语义后删除；删除前不得移除（主代码仍有引用）
 */
@Getter
public enum DataScopeType {

    /** 全部数据（本集团） */
    ALL("all", "全部数据"),

    /** 自定义（sys_role_org 配置的组织集合） */
    CUSTOM("custom", "自定义数据"),

    /** 本大区及以下（全部办事处→部门→人） */
    REGION("region", "本大区及以下"),

    /** 本办事处及以下（全部部门→人） */
    OFFICE("office", "本办事处及以下"),

    /** 本部门及以下（含子部门，兼容） */
    DEPT("dept", "本部门及以下"),

    /** @deprecated 旧"本组织及下级"语义，由 DEPT 兼容替代，调用方迁移后删除 */
    @Deprecated
    ORG_AND_CHILDREN("org_and_children", "本组织及下级"),

    /** @deprecated 旧"仅本组织"语义，调用方迁移（org→dept）后删除 */
    @Deprecated
    ORG("org", "仅本组织"),

    /** 本人及全部下级（manager_id 递归限5层） */
    TEAM("team", "本人及下级"),

    /** 仅本人 */
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
