package com.aigicrm.module.customer.dto;

import java.math.BigDecimal;

/** 【裁决 2026-10-10】阶段性开票/回款总额录入请求体。
 *  periodType: H1=上半年 H2=下半年 FY=全年; 两项金额单位万元, 至少一项 > 0, 可只填其一(另一项按 0)。 */
public record StageAmountRequest(Integer periodYear, String periodType,
                                 BigDecimal invoiceAmount, BigDecimal paymentAmount) {}
