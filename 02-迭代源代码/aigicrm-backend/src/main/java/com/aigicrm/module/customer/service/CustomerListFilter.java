package com.aigicrm.module.customer.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户列表统一查询条件（分页/类型页签计数/导出三口径共用）。
 *
 * <p>从 {@link CustomerService.ListFilter} 提升为顶层类型，供 CustomerQueryService
 * 与控制器共享，避免查询 bean 反向依赖门面。</p>
 */
public record CustomerListFilter(String keyword, String industry, Long officeId, Long regionId, Long deptId,
                                 String status, Long ownerId, String scope, String devType, String groupName,
                                 String protectionStatus, String province, String city,
                                 LocalDate regDateStart, LocalDate regDateEnd,
                                 LocalDate expDateStart, LocalDate expDateEnd,
                                 List<Long> userIds, List<Long> officeIds, String ownerName) {
}
