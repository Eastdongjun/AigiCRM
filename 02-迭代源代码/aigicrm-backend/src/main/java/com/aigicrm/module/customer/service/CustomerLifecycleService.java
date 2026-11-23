package com.aigicrm.module.customer.service;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 客户保护期/到期日计算。三线取最晚：建档线、开票线、捞取线，再加已批延期。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出，供建/改/捞取/延期/财务回算共用同一公式源。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerLifecycleService {

    private final SysConfigService configService;

    /**
     * 【2026-11-02 口径批·用户裁决 A/B】三线取最晚公式:
     * expireDate = MAX(建档线, 开票线, 捞取线) + approved_extend_days。
     */
    public void calculateExpireDates(BizCustomer customer) {
        LocalDate invoice = customer.getLastInvoiceDate();
        LocalDate register = customer.getRegisterDate();
        LocalDate claim = customer.getClaimDate();
        int protectionDays = getConfigInt("customer.protection.days", 365);
        int adjustDays = getConfigInt("customer.adjust.days", 180);
        int extendDays = customer.getApprovedExtendDays() != null ? customer.getApprovedExtendDays() : 0;

        LocalDate expireBase = null;
        // 开票线
        if (invoice != null) {
            expireBase = invoice.plusDays(protectionDays);
        }
        // 建档线(仅有捞取线时不参与, 见方法注释)
        if (claim == null && register != null) {
            LocalDate registerLine = register.plusDays(protectionDays);
            expireBase = expireBase == null || registerLine.isAfter(expireBase) ? registerLine : expireBase;
        }
        // 捞取线(裁决B: 捞取后 180 天短保护)
        if (claim != null) {
            LocalDate claimLine = claim.plusDays(adjustDays);
            expireBase = expireBase == null || claimLine.isAfter(expireBase) ? claimLine : expireBase;
        }
        if (expireBase != null) {
            customer.setExpireDate(expireBase.plusDays(extendDays));
        }
        // 捞取到期日(裁决C): 仅捞取客户有值; 非捞取客户置 null(存量已由 V2026.11.02.4 清理)
        customer.setAdjustDate(claim != null ? claim.plusDays(adjustDays) : null);
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }
}
