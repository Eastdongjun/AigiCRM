package com.aigicrm.module.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerImportResult {
    private int totalCount;
    private int successCount;
    private int failCount;
    private List<FailDetail> failures = new ArrayList<>();
    /** 【D3】不阻断导入的提示(如注册日缺失按导入日兜底) */
    private List<FailDetail> warnings = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class FailDetail {
        private int rowNum;
        private String customerName;
        private String reason;
    }
}
