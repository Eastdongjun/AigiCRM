package com.aigicrm.module.customer.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.entity.*;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final SeaService seaService;
    private final com.aigicrm.common.idempotency.IdempotencyService idempotencyService;
    private final CustomerAccessService accessService;

    // 【2026-09-13 移除「客户文件」功能】原 GET/POST/DELETE /{id}/files 三个端点及其
    // CustomerFileRelationService 依赖已整体删除（用户确认：小程序内部使用，该需求作废）。
    // 关联表 biz_customer_file 由 V2026.09.13.5 迁移删除。

    @PreAuthorize("hasAuthority('customer:view')")
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
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String devType,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String protectionStatus,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city) {
        return Result.ok(customerService.page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, devType, groupName, deptId, protectionStatus, province, city));
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/list/counts")
    public Result<Map<String, Long>> listCounts() {
        return Result.ok(customerService.listScopeCounts());
    }

    /**
     * 新增/编辑客户表单「所属集团」下拉候选（历史集团名去重，范围同客户列表）
     * 【2026-09-13】此前该字段是纯自由文本，同一集团易出现多种写法；改为可选可新建下拉。
     * 注：不使用 sys_group —— 那是「集团/法人租户配置」（权限体系顶层单元），与本字段语义不同。
     */
    @PreAuthorize("hasAuthority('customer:add') or hasAuthority('customer:edit')")
    @GetMapping("/group-names")
    public Result<List<String>> groupNames() {
        return Result.ok(customerService.listGroupNames());
    }

    @PreAuthorize("hasAuthority('score:view') or hasAuthority('ranking:customer:view') or hasAuthority('statistics:view')")
    @GetMapping("/rankings")
    public Result<Map<String, Object>> rankings() {
        return Result.ok(customerService.customerRankings());
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}")
    public Result<BizCustomer> getById(@PathVariable Long id) {
        accessService.assertCanView(id);
        return Result.ok(customerService.getById(id));
    }

    @PreAuthorize("hasAuthority('customer:add')")
    @PostMapping("/add")
    public Result<Long> create(@RequestBody BizCustomer customer) {
        customerService.create(customer);
        return Result.ok(customer.getId());
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BizCustomer customer) {
        accessService.assertCanEdit(id);
        customer.setId(id);
        customerService.update(customer);
        return Result.ok();
    }

    // ====== 【PR-05 2026-09-19】客户+指标同事务组合提交 ======
    // 两步保存的部分成功问题(建客户成功、存指标失败→界面报"保存失败"但客户已入库,
    // 重试重复建客)由组合端点根治;旧的两步端点保留兼容。

    /** 组合提交请求体:customer 必填,indicator 可空(空=只存客户,与表单 shouldSaveIndicator 口径一致) */
    @lombok.Data
    public static class CustomerWithIndicatorRequest {
        private BizCustomer customer;
        private BizCustomerIndicator indicator;
    }

    @PreAuthorize("hasAuthority('customer:add')")
    @PostMapping("/full")
    public Result<Long> createFull(@RequestBody CustomerWithIndicatorRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            jakarta.servlet.http.HttpServletResponse response) {
        if (body.getCustomer() == null) {
            throw new BusinessException(400, "缺少客户信息");
        }
        // 指标写入沿用独立指标端点的 customer:edit 语义——组合端点不放宽权限
        if (body.getIndicator() != null && !SecurityUtils.hasAuthority("customer:edit")) {
            throw new BusinessException(403, "保存客情指标需要客户编辑权限");
        }
        // 【PR-05 残余 2026-09-21】强幂等:客户端随机 Idempotency-Key + Redis PENDING/RESULT。
        // 重试/双击拿到同一个客户 id(响应头 Idempotent-Replay: true),不再重复建客;
        // Redis 异常按"无幂等"放行,不阻断业务(与安全审计同口径的旁路降级)。
        String scoped = null;
        boolean owner = false;
        if (idempotencyService.isValidKey(idempotencyKey)) {
            scoped = idempotencyService.scope("customer:full", SecurityUtils.getCurrentUserId(), idempotencyKey);
            String prior = idempotencyService.awaitResult(scoped, 0);
            if (prior != null) {
                response.setHeader("Idempotent-Replay", "true");
                return Result.ok(Long.valueOf(prior));
            }
            owner = idempotencyService.tryBegin(scoped);
            if (!owner) {
                // 同键请求正在执行:短等其结果(最多 5s),等不到则放行(幂等降级)
                String waited = idempotencyService.awaitResult(scoped, 5000);
                if (waited != null) {
                    response.setHeader("Idempotent-Replay", "true");
                    return Result.ok(Long.valueOf(waited));
                }
            }
        }
        try {
            Long id = customerService.createWithIndicator(body.getCustomer(), body.getIndicator());
            if (scoped != null && owner) {
                idempotencyService.finish(scoped, String.valueOf(id));
            }
            return Result.ok(id);
        } catch (RuntimeException e) {
            if (scoped != null && owner) {
                idempotencyService.abort(scoped);   // 失败释放,允许立即重试
            }
            throw e;
        }
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PutMapping("/{id}/full")
    public Result<Void> updateFull(@PathVariable Long id, @RequestBody CustomerWithIndicatorRequest body) {
        if (body.getCustomer() == null) {
            throw new BusinessException(400, "缺少客户信息");
        }
        accessService.assertCanEdit(id);
        customerService.updateWithIndicator(id, body.getCustomer(), body.getIndicator());
        return Result.ok();
    }

    /** 【议题五.4 裁决】归档(默认隐藏,可筛含归档) */
    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        customerService.archive(id);
        return Result.ok();
    }

    /** 【议题五.4】从归档恢复 */
    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        customerService.restore(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accessService.assertCanDelete(id);
        customerService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/logs")
    public Result<List<BizCustomerLog>> getLogs(@PathVariable Long id) {
        accessService.assertCanView(id);
        return Result.ok(customerService.getLogs(id));
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/logs")
    public Result<Void> addLog(@PathVariable Long id, @RequestBody BizCustomerLog log) {
        accessService.assertCanEdit(id);
        log.setCustomerId(id);
        customerService.addLog(log);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(customerService.getStatistics());
    }


    // ====== 开票记录 ======

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/invoices")
    public Result<List<BizInvoice>> getInvoices(@PathVariable Long id) {
        accessService.assertCanView(id);
        if (!SecurityUtils.hasAuthority("finance:view")) return Result.ok(List.of());
        return Result.ok(customerService.getInvoices(id));
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/invoices/summary")
    public Result<Map<String, Object>> getInvoiceSummary(@PathVariable Long id) {
        accessService.assertCanView(id);
        if (!SecurityUtils.hasAuthority("finance:view")) return Result.ok(Map.of("totalCount", 0, "totalAmount", 0));
        return Result.ok(customerService.getInvoiceSummary(id));
    }

    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/invoices")
    public Result<Void> addInvoice(@PathVariable Long id, @RequestBody BizInvoice invoice) {
        accessService.assertCanEdit(id);
        customerService.addInvoice(id, invoice);
        return Result.ok();
    }

    /** 【PR-18 2026-09-19】台账确认(pending → confirmed) */
    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/invoices/{invoiceId}/confirm")
    public Result<Void> confirmInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        customerService.confirmInvoice(id, invoiceId);
        return Result.ok();
    }

    /** 【PR-18】台账作废(保留记录) */
    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/invoices/{invoiceId}/void")
    public Result<Void> voidInvoice(@PathVariable Long id, @PathVariable Long invoiceId,
                                    @RequestBody Map<String, String> body) {
        customerService.voidInvoice(id, invoiceId, body.get("reason"));
        return Result.ok();
    }

    /** 【PR-18】回款确认 */
    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/payments/{paymentId}/confirm")
    public Result<Void> confirmPayment(@PathVariable Long id, @PathVariable Long paymentId) {
        customerService.confirmPayment(id, paymentId);
        return Result.ok();
    }

    /** 【PR-18】回款作废 */
    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/payments/{paymentId}/void")
    public Result<Void> voidPayment(@PathVariable Long id, @PathVariable Long paymentId,
                                    @RequestBody Map<String, String> body) {
        customerService.voidPayment(id, paymentId, body.get("reason"));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @DeleteMapping("/{id}/invoices/{invoiceId}")
    public Result<Void> deleteInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        accessService.assertCanEdit(id);
        customerService.deleteInvoice(id, invoiceId);
        return Result.ok();
    }

    // ====== 客户指标 ======

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/indicator")
    public Result<BizCustomerIndicator> getIndicator(@PathVariable Long id) {
        accessService.assertCanView(id);
        return Result.ok(customerService.getIndicator(id));
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/indicator")
    public Result<Void> saveIndicator(@PathVariable Long id, @RequestBody BizCustomerIndicator data) {
        accessService.assertCanEdit(id);
        customerService.saveIndicator(id, data);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PutMapping("/{id}/relationship")
    public Result<Void> saveRelationship(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        accessService.assertCanEdit(id);
        customerService.saveRelationshipScore(id, body.getOrDefault("score", 0));
        return Result.ok();
    }

    // ====== 客户分享 ======

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/shares")
    public Result<List<BizCustomerShare>> getShares(@PathVariable Long id) {
        accessService.assertCanView(id);
        return Result.ok(customerService.getShares(id));
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/shares")
    public Result<Void> addShares(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        accessService.assertCanEdit(id);
        customerService.addShares(id, body.getOrDefault("userIds", List.of()));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:edit')")
    @DeleteMapping("/{id}/shares/{userId}")
    public Result<Void> removeShare(@PathVariable Long id, @PathVariable Long userId) {
        accessService.assertCanEdit(id);
        customerService.removeShare(id, userId);
        return Result.ok();
    }

    /**
     * 手动释放客户到公海池
     */
    @PreAuthorize("hasAuthority('customer:edit')")
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestBody Map<String, String> body) {
        accessService.assertCanEdit(id);
        BizCustomer customer = customerService.getById(id);
        // 【2026-08-14 404 防探测】与 assertCanEdit/Service.getById 统一文案, 消除存在性 oracle
        if (customer == null) throw new BusinessException(404, "客户不存在或无权访问");
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
    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/users/simple")
    public Result<List<Map<String, Object>>> simpleUserList() {
        return Result.ok(customerService.getSimpleUserList());
    }

    // ====== 联系人 ======

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/contacts")
    public Result<List<BizContact>> getContacts(@PathVariable Long id) {
        accessService.assertCanView(id);
        if (!SecurityUtils.hasAuthority("contact:view")) return Result.ok(List.of());
        return Result.ok(customerService.getContacts(id));
    }

    @PreAuthorize("hasAuthority('customer:edit') or hasAuthority('contact:manage')")
    @PostMapping("/{id}/contacts")
    public Result<Void> addContact(@PathVariable Long id, @RequestBody BizContact contact) {
        accessService.assertCanEdit(id);
        customerService.addContact(id, contact);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:edit') or hasAuthority('contact:manage')")
    @PutMapping("/{id}/contacts/{contactId}")
    public Result<Void> updateContact(@PathVariable Long id, @PathVariable Long contactId,
                                       @RequestBody BizContact contact) {
        accessService.assertCanEdit(id);
        customerService.updateContact(id, contactId, contact);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('customer:edit') or hasAuthority('contact:manage')")
    @DeleteMapping("/{id}/contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable Long id, @PathVariable Long contactId) {
        accessService.assertCanEdit(id);
        customerService.deleteContact(id, contactId);
        return Result.ok();
    }

    // ====== 回款记录 ======

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/payments")
    public Result<List<BizPayment>> getPayments(@PathVariable Long id) {
        accessService.assertCanView(id);
        if (!SecurityUtils.hasAuthority("finance:view")) return Result.ok(List.of());
        return Result.ok(customerService.getPayments(id));
    }

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/{id}/payments/summary")
    public Result<Map<String, Object>> getPaymentSummary(@PathVariable Long id) {
        accessService.assertCanView(id);
        if (!SecurityUtils.hasAuthority("finance:view")) return Result.ok(Map.of("totalCount", 0, "totalAmount", 0));
        return Result.ok(customerService.getPaymentSummary(id));
    }

    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @PostMapping("/{id}/payments")
    public Result<Void> addPayment(@PathVariable Long id, @RequestBody BizPayment payment) {
        accessService.assertCanEdit(id);
        customerService.addPayment(id, payment);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('finance:manage') or (hasAuthority('customer:edit') and hasAuthority('finance:view'))")
    @DeleteMapping("/{id}/payments/{paymentId}")
    public Result<Void> deletePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        accessService.assertCanEdit(id);
        customerService.deletePayment(id, paymentId);
        return Result.ok();
    }

    // ====== 批量导入 ======

    @PreAuthorize("hasAuthority('customer:import')")
    @PostMapping("/import")
    public Result<CustomerImportResult> importCustomers(@RequestParam("file") MultipartFile file) {
        // 【BS-D 2026-09-19】导入资源上限统一收口(非空/扩展名/大小 5MB)
        com.aigicrm.common.util.ImportGuard.checkFile(file);
        return Result.ok(customerService.importCustomers(file));
    }

    @PreAuthorize("hasAuthority('customer:import')")
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" +
                URLEncoder.encode("客户导入模板.xlsx", StandardCharsets.UTF_8));

        // 示意数据行
        CustomerImportDTO sample = new CustomerImportDTO();
        sample.setCustomerName("示例：山西XX钢铁有限公司");
        sample.setInvoiceName("山西XX钢铁有限公司");
        sample.setIndustry("冶金");
        sample.setDevType("突破开拓");
        sample.setGroupName("XX钢铁集团");
        sample.setBranchCompany("XX分公司");
        sample.setUnitName("XX厂矿");
        sample.setCapacity("500万吨/年");
        sample.setProvince("山西省");
        sample.setCity("太原市");
        sample.setDistrict("清徐县");
        sample.setAddress("XX工业园区XX路XX号");
        sample.setHasTerminal(1);
        sample.setRegisterDate(java.time.LocalDate.of(2026, 1, 15));
        sample.setLastInvoiceDate(java.time.LocalDate.of(2026, 6, 20));
        sample.setRemark("此为示意数据，导入时请删除此行");

        // 填写说明数据（3列：字段/类型/填写说明）
        List<List<String>> refData = new java.util.ArrayList<>();
        refData.add(List.of("客户名称", "必填", "订货客户的全称，如'山西XX钢铁有限公司'"));
        refData.add(List.of("开票名称", "选填", "发票抬头，与客户名称相同可留空"));
        refData.add(List.of("行业", "必填", "可选: 冶金/煤矿/化工/电力/有色金属/机械/工程公司/维修厂/其他终端客户"));
        refData.add(List.of("开发类型", "必填", "可选: 成熟创新/突破开拓/铺垫宣传"));
        refData.add(List.of("所属集团", "选填", "集团公司名称，如'XX钢铁集团'"));
        refData.add(List.of("所属分公司", "选填", "分公司名称"));
        refData.add(List.of("所属单位", "选填", "厂矿单位名称"));
        refData.add(List.of("产能", "选填", "如'500万吨/年'"));
        refData.add(List.of("省", "选填", "如'山西省'"));
        refData.add(List.of("市", "选填", "如'太原市'"));
        refData.add(List.of("县", "选填", "如'清徐县'"));
        refData.add(List.of("厂址", "选填", "详细地址"));
        refData.add(List.of("终端", "选填", "1=是 0=否"));
        refData.add(List.of("注册日", "选填", "日期格式: 2026-01-15"));
        refData.add(List.of("最近开票日", "选填", "日期格式: 2026-06-20"));
        refData.add(List.of("备注", "选填", "任何补充信息，导入时删除示例行"));

        // 填写说明表头
        List<List<String>> refHeaders = new java.util.ArrayList<>();
        refHeaders.add(List.of("字段"));
        refHeaders.add(List.of("类型"));
        refHeaders.add(List.of("填写说明"));

        // 用 DTO 模型写模板 sheet
        com.alibaba.excel.ExcelWriter writer = EasyExcel.write(response.getOutputStream(), CustomerImportDTO.class).build();
        com.alibaba.excel.write.metadata.WriteSheet sheet1 = EasyExcel.writerSheet(0, "客户导入模板").build();
        writer.write(List.of(sample), sheet1);

        // 填写说明 sheet（无模型，手动头）
        com.alibaba.excel.write.metadata.WriteSheet sheet2 = EasyExcel.writerSheet(1, "填写说明").head(refHeaders).build();
        writer.write(refData, sheet2);
        writer.finish();
    }

    @PreAuthorize("hasAuthority('customer:export')")
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

    @PreAuthorize("hasAuthority('customer:view')")
    @GetMapping("/check-name")
    public Result<java.util.Map<String, Object>> checkName(@RequestParam String name) {
        boolean exists = customerService.existsByName(name);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("exists", exists);
        if (exists) {
            var info = customerService.getDuplicateInfo(name);
            if (info != null) {
                result.put("createdBy", info.getOrDefault("createdBy", ""));
                result.put("createdAt", info.getOrDefault("createdAt", ""));
            }
        }
        return Result.ok(result);
    }
}
