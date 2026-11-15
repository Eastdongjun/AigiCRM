package com.aigicrm.common.auth;

import com.aigicrm.common.BusinessException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 权限码依赖规则(2026-11-15 权限体系收口)。
 *
 * <p>字段/附属码必须依附父功能码, 否则就是“勾了不生效”的死配置:
 * <ul>
 *   <li>customer:date:edit / customer:invoice-date:edit → customer:edit（字段码）</li>
 *   <li>contact:view / finance:view → customer:view（数据掩码码）</li>
 * </ul>
 *
 * <p>保存语义:
 * <ul>
 *   <li>提交集有子码无父码, 且旧绑定没有父码 → 400 FIELD_PERM_REQUIRED（拒绝无效配置）；</li>
 *   <li>提交集有子码无父码, 但旧绑定有父码 → 视为“主动取消父码”, 子码一并移除（不阻断保存）。</li>
 * </ul>
 */
public final class PermissionDependencyRules {

    private PermissionDependencyRules() {
    }

    /** 子码 → 父功能码 */
    public static final Map<String, String> REQUIRES = Map.of(
            "customer:date:edit", "customer:edit",
            "customer:invoice-date:edit", "customer:edit",
            "contact:view", "customer:view",
            "finance:view", "customer:view",
            "fabushi:create", "fabushi:view",
            "fabushi:book:manage", "fabushi:book:view",
            "fabushi:content:manage", "fabushi:content:view",
            "fabushi:stats:export", "fabushi:stats:view",
            "fabushi:activity:manage", "fabushi:view");

    /**
     * 解析依赖：必要时从 includedCodes / expanded 中移除子码，或抛出 400。
     *
     * @param includedCodes 本次提交的权限码集合（可修改）
     * @param expanded      本次提交的 menuId 集合（可修改）
     * @param previousCodes 变更前角色已绑定的权限码集合（用于区分“取消父码”与“只勾子码”）
     * @param codeById      menuId → permission 映射（用于同步裁剪 expanded）
     */
    public static void resolve(Set<String> includedCodes, Set<Long> expanded,
                               Set<String> previousCodes, Map<Long, String> codeById) {
        if (includedCodes == null || includedCodes.isEmpty()) return;
        Map<Long, String> idToCode = codeById == null ? new HashMap<>() : codeById;
        for (Map.Entry<String, String> e : REQUIRES.entrySet()) {
            String child = e.getKey();
            String parent = e.getValue();
            if (!includedCodes.contains(child) || includedCodes.contains(parent)) continue;
            boolean parentWasBound = previousCodes != null && previousCodes.contains(parent);
            if (parentWasBound) {
                includedCodes.remove(child);
                if (expanded != null) expanded.removeIf(id -> child.equals(idToCode.get(id)));
            } else {
                throw new BusinessException(400, "FIELD_PERM_REQUIRED",
                        "字段/附属码 " + child + " 需同时勾选父功能码 " + parent);
            }
        }
    }
}
