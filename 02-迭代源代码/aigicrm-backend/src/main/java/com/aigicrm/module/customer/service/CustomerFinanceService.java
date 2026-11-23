package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizInvoice;
import com.aigicrm.module.customer.entity.BizPayment;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户财务台账：发票/回款的读写、确认/作废/删除，以及开票基准对保护期的驱动。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出。核心重算委托
 * {@link CustomerIndicatorService}，到期日委托 {@link CustomerLifecycleService}，
 * 审计委托 {@link CustomerAuditService}。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerFinanceService {

    private static final String LIT_PENDING = "pending";
    private static final String LIT_VOIDED = "voided";
    private static final String LIT_X_2 = "发票不存在或不属于该客户";
    private static final String LIT_X_3 = "回款不存在或不属于该客户";
    private static final String LIT_LIMIT_1 = "LIMIT 1";

    private final BizInvoiceMapper invoiceMapper;
    private final BizPaymentMapper paymentMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final CustomerAccessService customerAccessService;
    private final CustomerIndicatorService customerIndicatorService;
    private final CustomerContactShareService customerContactShareService;
    private final CustomerEventService customerEventService;
    private final CustomerLifecycleService customerLifecycleService;
    private final CustomerAuditService customerAuditService;

    public List<BizInvoice> getInvoices(Long customerId) {
        return invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .orderByDesc(BizInvoice::getInvoiceDate));
    }

    public Map<String, Object> getInvoiceSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", invoiceMapper.countByCustomerId(customerId));
        summary.put("totalAmount", invoiceMapper.sumAmountByCustomerId(customerId));
        // 【PR-18 统计口径】最近开票日排除已作废发票(金额/笔数已由 Mapper 排除)
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .ne(BizInvoice::getStatus, LIT_VOIDED)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last(LIT_LIMIT_1));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getInvoiceDate());
        return summary;
    }

    @Transactional
    public void addInvoice(Long customerId, BizInvoice invoice) {
        // 【审查 backend-4 2026-10-18】财务写入口自守: 客户必须存在/未删/同集团,
        // 不依赖控制器层 finance:manage 分支的放行(代录也不能对已删/跨集团客户落台账)
        customerAccessService.assertFinanceCustomerWritable(customerId);
        invoice.setCustomerId(customerId);
        invoice.setCreatedBy(SecurityUtils.getCurrentUserId());
        invoiceMapper.insert(invoice);
        // 【延期规则收敛 2026-10-18】新开票是否重置已批准延期,与阶段总额共用同一判定:
        // 仅当最近开票日真正前移(新值晚于旧值)才清零;补录早于当前基准的旧票不清零。
        // 旧实现无条件清零,补录历史票也会误伤已批延期。判定/清零/重算集中在
        // advanceLastInvoiceDate,本方法只负责刷新最近开票日并触发一次到期日重算。
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            advanceLastInvoiceDate(customer, latestActiveInvoiceDate(customerId));
        }
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    /** 【PR-18 2026-09-19】台账确认:pending → confirmed(锁定,不可直接删) */
    @Transactional
    public void confirmInvoice(Long customerId, Long invoiceId) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        if (!LIT_PENDING.equals(invoice.getStatus())) {
            throw new BusinessException("仅待核对状态可确认");
        }
        invoice.setStatus("confirmed");
        invoice.setConfirmedBy(SecurityUtils.getCurrentUserId());
        invoice.setConfirmedAt(java.time.LocalDateTime.now());
        invoiceMapper.updateById(invoice);
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    /** 【PR-18】台账作废:confirmed/pending → voided(保留记录,不物理删) */
    @Transactional
    public void voidInvoice(Long customerId, Long invoiceId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("作废必须填写原因");
        }
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        if (LIT_VOIDED.equals(invoice.getStatus())) {
            throw new BusinessException("该发票已作废");
        }
        invoice.setStatus(LIT_VOIDED);
        invoice.setVoidedReason(reason.trim());
        invoiceMapper.updateById(invoice);
        refreshLastInvoiceDate(customerId);
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    /** 【PR-18】回款确认 */
    @Transactional
    public void confirmPayment(Long customerId, Long paymentId) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        if (!LIT_PENDING.equals(payment.getStatus())) {
            throw new BusinessException("仅待核对状态可确认");
        }
        payment.setStatus("confirmed");
        payment.setConfirmedBy(SecurityUtils.getCurrentUserId());
        payment.setConfirmedAt(java.time.LocalDateTime.now());
        paymentMapper.updateById(payment);
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    /** 【PR-18】回款作废 */
    @Transactional
    public void voidPayment(Long customerId, Long paymentId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("作废必须填写原因");
        }
        BizPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        if (LIT_VOIDED.equals(payment.getStatus())) {
            throw new BusinessException("该回款已作废");
        }
        payment.setStatus(LIT_VOIDED);
        payment.setVoidedReason(reason.trim());
        paymentMapper.updateById(payment);
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deleteInvoice(Long customerId, Long invoiceId, String reason) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException(LIT_X_2);
        }
        // 【裁决 2026-09-24 更新】"发票可以删除,但要有删除记录"——任何状态可删(替换 PR-18 仅待核对可删):
        // 必填删除原因，并落系统日志审计快照（票号/日期/金额/状态/原因/操作人），行保留可回查；
        // 保护期由最近开票日回算自动修正。
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写删除原因（财务记录删除必须留痕）");
        }
        customerAuditService.logFinanceDeletion("发票", customerId, invoiceId, reason.trim(),
                "票号=" + invoice.getInvoiceNumber() + ", 日期=" + invoice.getInvoiceDate()
                        + ", 金额=" + invoice.getAmount() + ", 状态=" + invoice.getStatus());
        invoiceMapper.deleteById(invoiceId);
        refreshLastInvoiceDate(invoice.getCustomerId());
        customerIndicatorService.recalculateCoreStatus(invoice.getCustomerId());
    }

    private void refreshLastInvoiceDate(Long customerId) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            customer.setLastInvoiceDate(latestActiveInvoiceDate(customerId));
            customerLifecycleService.calculateExpireDates(customer);
            customerMapper.updateById(customer);
        }
    }

    /** 【延期规则收敛 2026-10-18】最近开票日推导查询(排除已作废): addInvoice 与 refresh 共用同一口径 */
    private LocalDate latestActiveInvoiceDate(Long customerId) {
        // 【PR-18 统计口径】最近开票日排除已作废发票——作废发票不得驱动保护期/到期日计算
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .ne(BizInvoice::getStatus, LIT_VOIDED)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last(LIT_LIMIT_1));
        return latest.isEmpty() ? null : latest.get(0).getInvoiceDate();
    }

    /**
     * 【延期规则收敛 2026-10-18 · 审计 biz-0】开票驱动保护期基准前移的唯一入口:
     * addInvoice(逐笔补录)与 saveStageAmount(阶段总额)的前移路径都只经本方法写 lastInvoiceDate。
     * <ul>
     *   <li>仅当候选日期晚于当前最近开票日(含当前为空的首个基准)才算"前移":清零
     *       approved_extend_days 并重算到期日一次——新基准上的保护期已覆盖旧延期,
     *       继续叠加会造成双重延长(新基准 360 天 + 旧基准批出的延期);</li>
     *   <li>补录早于/等于当前基准的旧票不算前移:保持基准与已批延期,不清零、不重算
     *       (旧实现 addInvoice 无条件清零,补录历史票会误伤延期);</li>
     *   <li>删票/作废仍走 refreshLastInvoiceDate 回算(可能后退),不收回已批延期——
     *       与 2026-09-19 议题一裁决一致。</li>
     * </ul>
     *
     * @return true=发生前移并已落库(调用方据此决定是否清 invoice 类 pending 提醒)
     */
    boolean advanceLastInvoiceDate(BizCustomer customer, LocalDate candidateDate) {
        if (customer == null || candidateDate == null) return false;
        // 【2026-11-02 裁决B 守卫】捞取后新周期: 早于捞取日的老票日期不得经删票/作废回算回填
        // 最近开票日(捞取置空后 current=null, 无此守卫会把上一周期的老票日期写回, 开票线复活
        // 拉长保护期, 架空"捞取重新起算")。
        if (customer.getClaimDate() != null && candidateDate.isBefore(customer.getClaimDate())) return false;
        LocalDate current = customer.getLastInvoiceDate();
        if (current != null && !candidateDate.isAfter(current)) return false;
        customer.setLastInvoiceDate(candidateDate);
        customer.setApprovedExtendDays(0);
        customerLifecycleService.calculateExpireDates(customer);
        customerMapper.updateById(customer);
        // 【生命线 2026-11-02 裁决D】开票推进事件(逐笔 addInvoice 与阶段 saveStageAmount 共用本口)
        customerEventService.record(customer.getId(), CustomerEventService.TYPE_INVOICE_ADV,
                candidateDate, "开票推进最近开票日",
                current != null ? current.toString() : null, candidateDate.toString(),
                com.aigicrm.security.SecurityUtils.getCurrentUserId());
        return true;
    }

    /** 阶段总额录入的开票推进入口：仅当前移时清延期提醒。 */
    void advanceInvoicePeriod(BizCustomer customer, Long customerId, int year, String type) {
        LocalDate periodEnd = "H1".equals(type) ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31);
        // 提前录入进行中的周期时按今天封顶, 防止把保护期推到未来
        LocalDate effective = periodEnd.isAfter(LocalDate.now()) ? LocalDate.now() : periodEnd;
        if (advanceLastInvoiceDate(customer, effective)) {
            customerContactShareService.cleanInvoicePendingReminders(customerId);
        }
    }

    public List<BizPayment> getPayments(Long customerId) {
        List<BizPayment> payments = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate));
        for (BizPayment p : payments) {
            if (p.getCreatedBy() != null) {
                SysUser user = userMapper.selectById(p.getCreatedBy());
                if (user != null) p.setCreatedByName(user.getRealName());
            }
        }
        return payments;
    }

    public Map<String, Object> getPaymentSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", paymentMapper.countByCustomerId(customerId));
        summary.put("totalAmount", paymentMapper.sumAmountByCustomerId(customerId));
        List<BizPayment> latest = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate)
                        .last(LIT_LIMIT_1));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getPaymentDate());
        return summary;
    }

    @Transactional
    public void addPayment(Long customerId, BizPayment payment) {
        // 【审查 backend-4 2026-10-18】与 addInvoice 同口径: 客户存在/未删/同集团
        customerAccessService.assertFinanceCustomerWritable(customerId);
        payment.setCustomerId(customerId);
        payment.setCreatedBy(SecurityUtils.getCurrentUserId());
        paymentMapper.insert(payment);
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deletePayment(Long customerId, Long paymentId, String reason) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException(LIT_X_3);
        }
        // 【裁决 2026-09-24 更新】与发票同口径: 任何状态可删+必填原因+审计快照+逻辑删
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写删除原因（财务记录删除必须留痕）");
        }
        customerAuditService.logFinanceDeletion("回款", customerId, paymentId, reason.trim(),
                "日期=" + payment.getPaymentDate() + ", 金额=" + payment.getPaymentAmount()
                        + ", 方式=" + payment.getPaymentMethod() + ", 状态=" + payment.getStatus());
        paymentMapper.deleteById(paymentId);
        customerIndicatorService.recalculateCoreStatus(payment.getCustomerId());
    }

}
