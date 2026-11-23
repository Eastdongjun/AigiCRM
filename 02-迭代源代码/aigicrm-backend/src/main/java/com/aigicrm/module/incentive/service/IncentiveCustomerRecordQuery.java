package com.aigicrm.module.incentive.service;

/** 激励客户记录查询条件（12 个筛选维度的参数对象，替代超长参数列表）。 */
public record IncentiveCustomerRecordQuery(
        String industry,
        Long mainCategoryId,
        String customerName,
        String salesperson,
        String startDate,
        String endDate,
        String period,
        String orgIds,
        String devType,
        String product,
        String keyword,
        Long recordId) {
}
