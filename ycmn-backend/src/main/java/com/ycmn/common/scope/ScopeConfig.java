package com.ycmn.common.scope;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据范围配置条目(批③ 2026-08-10)——功能权限×数据范围正交模型的配置载体。
 * 登录时随 SecurityUser 一次性装载(数据量=主体×模块×动作, 百行级), 热路径零 DB。
 * 主体三级: ROLE(角色编码)/POSITION(岗位ID)/USER(用户ID), 三级当前以并集装载。
 * ⚠️ USER>POSITION>ROLE 优先级尚未实现; scope_level 暂未进入 OrgScopeResolver 解析链路
 * (仅 anchor 被 anchorOf 消费)——生效前勿依赖该配置做安全判定。
 */
@Data
public class ScopeConfig implements Serializable {

    public static final String ASSIGNEE_ROLE = "ROLE";
    public static final String ASSIGNEE_POSITION = "POSITION";
    public static final String ASSIGNEE_USER = "USER";

    private String assigneeType;
    private String assigneeKey;
    private String module;
    private String action;
    private String scopeLevel;
    /** 锚点: OWNER=按负责人 / ORG=按客户归属组织 */
    private String anchor;
}
