package com.aigicrm.module.customer.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.service.CustomerService;

import java.time.LocalDate;

public class CustomerImportListener implements ReadListener<CustomerImportDTO> {

    private final CustomerService customerService;
    private final Long currentUserId;
    private final Long currentOfficeId;
    private final CustomerImportResult result;
    private int rowNum = 1; // header is row 1

    public CustomerImportListener(CustomerService customerService,
                                  Long currentUserId, Long currentOfficeId,
                                  CustomerImportResult result) {
        this.customerService = customerService;
        this.currentUserId = currentUserId;
        this.currentOfficeId = currentOfficeId;
        this.result = result;
    }

    @Override
    public void invoke(CustomerImportDTO dto, AnalysisContext context) {
        rowNum++;
        // 【BS-D 2026-09-19】行数上限:超限中断解析(EasyExcel 捕获 invoke 异常即停止读取)
        com.aigicrm.common.util.ImportGuard.checkRow(rowNum);
        try {
            // 1. 校验必填字段
            if (dto.getCustomerName() == null || dto.getCustomerName().trim().isEmpty()) {
                addFailure(rowNum, null, "客户名称不能为空");
                return;
            }
            String name = dto.getCustomerName().trim();

            // 2. 检查重名
            if (customerService.existsByName(name)) {
                addFailure(rowNum, name, "客户名称已存在");
                return;
            }

            // 3. 转换为实体
            BizCustomer customer = toEntity(dto);
            customer.setOwnerId(currentUserId);
            customer.setOfficeId(currentOfficeId);

            // 4. 复用现有创建逻辑（create 内对缺失注册日做当天兜底）
            boolean registerDateMissing = dto.getRegisterDate() == null;
            customerService.create(customer);
            result.setSuccessCount(result.getSuccessCount() + 1);
            if (registerDateMissing) {
                addWarning(rowNum, name, "未填写注册日期，已按导入日期 " + LocalDate.now() + " 兜底计算保护期");
            }

        } catch (Exception e) {
            String name = dto.getCustomerName() != null ? dto.getCustomerName().trim() : null;
            addFailure(rowNum, name, "导入失败: " + e.getMessage());
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        result.setTotalCount(rowNum - 1);
        result.setFailCount(result.getFailures().size());
    }

    private void addFailure(int row, String customerName, String reason) {
        result.getFailures().add(new CustomerImportResult.FailDetail(row, customerName, reason));
    }

    private void addWarning(int row, String customerName, String reason) {
        result.getWarnings().add(new CustomerImportResult.FailDetail(row, customerName, reason));
    }

    private BizCustomer toEntity(CustomerImportDTO dto) {
        BizCustomer c = new BizCustomer();
        c.setCustomerName(dto.getCustomerName().trim());
        c.setInvoiceName(dto.getInvoiceName());
        c.setIndustry(dto.getIndustry());
        c.setDevType(dto.getDevType());
        c.setGroupName(dto.getGroupName());
        c.setBranchCompany(dto.getBranchCompany());
        c.setUnitName(dto.getUnitName());
        c.setCapacity(dto.getCapacity());
        c.setProvince(dto.getProvince());
        c.setCity(dto.getCity());
        c.setDistrict(dto.getDistrict());
        c.setAddress(dto.getAddress());
        c.setHasTerminal(dto.getHasTerminal() != null ? dto.getHasTerminal() : 0);
        c.setRegisterDate(dto.getRegisterDate());
        c.setLastInvoiceDate(dto.getLastInvoiceDate());
        c.setRemark(dto.getRemark());
        return c;
    }

    public CustomerImportResult getResult() {
        return result;
    }
}
