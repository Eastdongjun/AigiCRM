package com.ycmn.module.customer.dto;

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

    @Data
    @AllArgsConstructor
    public static class FailDetail {
        private int rowNum;
        private String customerName;
        private String reason;
    }
}
