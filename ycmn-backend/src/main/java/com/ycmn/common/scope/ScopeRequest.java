package com.ycmn.common.scope;

import com.ycmn.security.SecurityUser;
import lombok.Builder;
import lombok.Data;

/**
 * 统一数据访问层请求(批② 2026-08-10)。
 * 列表/统计/单条访问控制共用同一解析链路, 根治"列表与详情口径分裂"(P1-05)。
 */
@Data
@Builder
public class ScopeRequest {

    /** 数据范围锚点: OWNER=按负责人(人维度) / ORG=按客户归属组织(部门维度) */
    public enum AnchorType { OWNER, ORG }

    private SecurityUser user;
    /** 模块(如 customer/approval/reminder/statistics/score/log) */
    private String module;
    /** 动作(view/edit/delete/...) */
    private String action;
    /** 锚点(模块级配置决定; 未配置默认 OWNER) */
    private AnchorType anchor;

    /** 单条判定: 目标记录负责人(OWNER 锚点) */
    private Long targetOwnerId;
    /** 单条判定: 目标记录归属部门(ORG 锚点) */
    private Long targetDeptId;
}
