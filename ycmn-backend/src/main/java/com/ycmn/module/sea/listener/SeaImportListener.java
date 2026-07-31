package com.ycmn.module.sea.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.ycmn.module.customer.dto.CustomerImportDTO;
import com.ycmn.module.customer.dto.CustomerImportResult;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.sea.service.SeaService;

/**
 * 公海池批量导入监听器：每行新建客户后直接释放进公海(状态 sea、待捞取、原因"保护期满")。
 * 模板与客户导入完全一致(复用 CustomerImportDTO)。
 */
public class SeaImportListener implements ReadListener<CustomerImportDTO> {

    private final CustomerService customerService;
    private final SeaService seaService;
    private final Long currentUserId;
    private final Long currentOfficeId;
    private final CustomerImportResult result;
    private int rowNum = 1; // header is row 1

    public SeaImportListener(CustomerService customerService, SeaService seaService,
                             Long currentUserId, Long currentOfficeId,
                             CustomerImportResult result) {
        this.customerService = customerService;
        this.seaService = seaService;
        this.currentUserId = currentUserId;
        this.currentOfficeId = currentOfficeId;
        this.result = result;
    }

    @Override
    public void invoke(CustomerImportDTO dto, AnalysisContext context) {
        rowNum++;
        try {
            if (dto.getCustomerName() == null || dto.getCustomerName().trim().isEmpty()) {
                addFailure(rowNum, null, "客户名称不能为空");
                return;
            }
            String name = dto.getCustomerName().trim();

            if (customerService.existsByName(name)) {
                addFailure(rowNum, name, "客户名称已存在");
                return;
            }

            BizCustomer customer = toEntity(dto);
            customer.setOwnerId(currentUserId);
            customer.setOfficeId(currentOfficeId);

            // 1. 新建客户
            customerService.create(customer);
            // 2. 直接释放进公海(原负责人=导入人所在办事处路由匹配公海池)
            seaService.addToSea(customer.getId(), "保护期满");

            result.setSuccessCount(result.getSuccessCount() + 1);
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
}
