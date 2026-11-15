package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityUtils;

/**
 * 客户字段级权限统一入口(2026-11-15 权限体系收口)。
 *
 * <p>字段码(customer:date:edit / customer:invoice-date:edit)是 customer:edit 的从属码:
 * 方法级仍要求 customer:edit, 这里只判定“本次是否允许修改受控字段”。</p>
 *
 * <p>字段码缺失统一抛 400 + FIELD_PERM_REQUIRED, 与功能码缺失(403 PERM_DENIED)区分,
 * 前端/测试可按 errorCode 分支, 不再靠文案猜测。</p>
 */
public final class FieldPermissionService {

    private FieldPermissionService() {
    }

    /** 注册日(registerDate)是否可维护 */
    public static boolean canEditRegisterDate() {
        return SecurityUtils.hasAuthority("customer:date:edit");
    }

    /** 最近开票日(lastInvoiceDate)是否可人工维护 */
    public static boolean canEditInvoiceDate() {
        return SecurityUtils.hasAuthority("customer:invoice-date:edit");
    }

    public static void requireRegisterDate() {
        if (!canEditRegisterDate()) {
            throw new BusinessException(400, "FIELD_PERM_REQUIRED",
                    "注册日录入后仅总监或行政可修改（缺少 customer:date:edit 字段权限）");
        }
    }

    public static void requireInvoiceDate() {
        if (!canEditInvoiceDate()) {
            throw new BusinessException(400, "FIELD_PERM_REQUIRED",
                    "最近开票日由开票记录自动推导，仅授权人员可维护（缺少 customer:invoice-date:edit 字段权限）");
        }
    }
}
