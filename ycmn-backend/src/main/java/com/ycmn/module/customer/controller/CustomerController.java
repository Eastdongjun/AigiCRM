package com.ycmn.module.customer.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycmn.common.BusinessException;
import com.ycmn.common.Result;
import com.ycmn.module.customer.dto.CustomerImportDTO;
import com.ycmn.module.customer.dto.CustomerImportResult;
import com.ycmn.module.customer.entity.*;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final SeaService seaService;

    @GetMapping("/list")
    public Result<IPage<BizCustomer>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String scope) {
        return Result.ok(customerService.page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope));
    }

    @GetMapping("/list/counts")
    public Result<Map<String, Long>> listCounts() {
        return Result.ok(customerService.listScopeCounts());
    }

    @GetMapping("/{id}")
    public Result<BizCustomer> getById(@PathVariable Long id) {
        return Result.ok(customerService.getById(id));
    }

    @PostMapping("/add")
    public Result<Long> create(@RequestBody BizCustomer customer) {
        customerService.create(customer);
        return Result.ok(customer.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BizCustomer customer) {
        customer.setId(id);
        customerService.update(customer);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<List<BizCustomerLog>> getLogs(@PathVariable Long id) {
        return Result.ok(customerService.getLogs(id));
    }

    @PostMapping("/{id}/logs")
    public Result<Void> addLog(@PathVariable Long id, @RequestBody BizCustomerLog log) {
        log.setCustomerId(id);
        customerService.addLog(log);
        return Result.ok();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(customerService.getStatistics());
    }

    // ====== 开票记录 ======

    @GetMapping("/{id}/invoices")
    public Result<List<BizInvoice>> getInvoices(@PathVariable Long id) {
        return Result.ok(customerService.getInvoices(id));
    }

    @GetMapping("/{id}/invoices/summary")
    public Result<Map<String, Object>> getInvoiceSummary(@PathVariable Long id) {
        return Result.ok(customerService.getInvoiceSummary(id));
    }

    @PostMapping("/{id}/invoices")
    public Result<Void> addInvoice(@PathVariable Long id, @RequestBody BizInvoice invoice) {
        customerService.addInvoice(id, invoice);
        return Result.ok();
    }

    @DeleteMapping("/{id}/invoices/{invoiceId}")
    public Result<Void> deleteInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        customerService.deleteInvoice(invoiceId);
        return Result.ok();
    }

    // ====== 客户指标 ======

    @GetMapping("/{id}/indicator")
    public Result<BizCustomerIndicator> getIndicator(@PathVariable Long id) {
        return Result.ok(customerService.getIndicator(id));
    }

    @PostMapping("/{id}/indicator")
    public Result<Void> saveIndicator(@PathVariable Long id, @RequestBody BizCustomerIndicator data) {
        customerService.saveIndicator(id, data);
        return Result.ok();
    }

    @PutMapping("/{id}/relationship")
    public Result<Void> saveRelationship(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        customerService.saveRelationshipScore(id, body.getOrDefault("score", 0));
        return Result.ok();
    }

    // ====== 客户分享 ======

    @GetMapping("/{id}/shares")
    public Result<List<BizCustomerShare>> getShares(@PathVariable Long id) {
        return Result.ok(customerService.getShares(id));
    }

    @PostMapping("/{id}/shares")
    public Result<Void> addShares(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        customerService.addShares(id, body.getOrDefault("userIds", List.of()));
        return Result.ok();
    }

    @DeleteMapping("/{id}/shares/{userId}")
    public Result<Void> removeShare(@PathVariable Long id, @PathVariable Long userId) {
        customerService.removeShare(id, userId);
        return Result.ok();
    }

    /**
     * 手动释放客户到公海池
     */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestBody Map<String, String> body) {
        BizCustomer customer = customerService.getById(id);
        if (customer == null) throw new BusinessException("客户不存在");
        // 只有客户负责人才能释放
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String dataScope = SecurityUtils.getCurrentDataScope();
        if (!"all".equals(dataScope) && !currentUserId.equals(customer.getOwnerId())) {
            throw new BusinessException("只有客户负责人才能释放客户");
        }
        // 客户所属办事处必须能匹配到公海池,否则阻止释放
        if (seaService.matchSeaConfig(customer.getOfficeId()) == null) {
            throw new BusinessException("该客户所属办事处未配置公海池，无法释放");
        }
        String reason = body.getOrDefault("reason", "放弃客户");
        seaService.addToSea(id, reason);
        // 释放后删除该客户的所有分享
        customerService.removeAllShares(id);
        return Result.ok();
    }

    /**
     * 简易用户列表（用于分享选人，不需要system:user:view权限）
     */
    @GetMapping("/users/simple")
    public Result<List<Map<String, Object>>> simpleUserList() {
        return Result.ok(customerService.getSimpleUserList());
    }

    // ====== 联系人 ======

    @GetMapping("/{id}/contacts")
    public Result<List<BizContact>> getContacts(@PathVariable Long id) {
        return Result.ok(customerService.getContacts(id));
    }

    @PostMapping("/{id}/contacts")
    public Result<Void> addContact(@PathVariable Long id, @RequestBody BizContact contact) {
        customerService.addContact(id, contact);
        return Result.ok();
    }

    @PutMapping("/{id}/contacts/{contactId}")
    public Result<Void> updateContact(@PathVariable Long id, @PathVariable Long contactId,
                                       @RequestBody BizContact contact) {
        contact.setId(contactId);
        contact.setCustomerId(id);
        customerService.updateContact(contact);
        return Result.ok();
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable Long id, @PathVariable Long contactId) {
        customerService.deleteContact(contactId);
        return Result.ok();
    }

    // ====== 回款记录 ======

    @GetMapping("/{id}/payments")
    public Result<List<BizPayment>> getPayments(@PathVariable Long id) {
        return Result.ok(customerService.getPayments(id));
    }

    @GetMapping("/{id}/payments/summary")
    public Result<Map<String, Object>> getPaymentSummary(@PathVariable Long id) {
        return Result.ok(customerService.getPaymentSummary(id));
    }

    @PostMapping("/{id}/payments")
    public Result<Void> addPayment(@PathVariable Long id, @RequestBody BizPayment payment) {
        customerService.addPayment(id, payment);
        return Result.ok();
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public Result<Void> deletePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        customerService.deletePayment(paymentId);
        return Result.ok();
    }

    // ====== 批量导入 ======

    @PostMapping("/import")
    public Result<CustomerImportResult> importCustomers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 格式的文件");
        }
        return Result.ok(customerService.importCustomers(file));
    }

    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" +
                URLEncoder.encode("客户导入模板.xlsx", StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), CustomerImportDTO.class)
                .sheet("客户导入模板")
                .doWrite(List.of());
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" +
                URLEncoder.encode("客户列表.xlsx", StandardCharsets.UTF_8));
        // 查全部（不分页），受数据范围控制
        IPage<BizCustomer> page = customerService.page(1, 10000, keyword, industry, officeId, regionId, status, null);
        EasyExcel.write(response.getOutputStream(), BizCustomer.class)
                .excludeColumnFieldNames(List.of("deleted", "createdAt", "updatedAt", "id"))
                .sheet("客户列表")
                .doWrite(page.getRecords());
    }
}
