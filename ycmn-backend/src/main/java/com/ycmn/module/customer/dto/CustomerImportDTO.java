package com.ycmn.module.customer.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CustomerImportDTO {
    @ExcelProperty("客户名称")
    private String customerName;

    @ExcelProperty("开票名称")
    private String invoiceName;

    @ExcelProperty("行业")
    private String industry;

    @ExcelProperty("开发类型")
    private String devType;

    @ExcelProperty("所属集团")
    private String groupName;

    @ExcelProperty("所属分公司")
    private String branchCompany;

    @ExcelProperty("所属单位")
    private String unitName;

    @ExcelProperty("产能")
    private String capacity;

    @ExcelProperty("省")
    private String province;

    @ExcelProperty("市")
    private String city;

    @ExcelProperty("县")
    private String district;

    @ExcelProperty("厂址")
    private String address;

    @ExcelProperty("终端")
    private Integer hasTerminal;

    @ExcelProperty("注册日")
    private LocalDate registerDate;

    @ExcelProperty("最近开票日")
    private LocalDate lastInvoiceDate;

    @ExcelProperty("备注")
    private String remark;
}
