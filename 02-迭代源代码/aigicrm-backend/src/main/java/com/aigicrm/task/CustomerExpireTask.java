package com.aigicrm.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerExpireTask {

    private final BizCustomerMapper customerMapper;
    private final BizReminderMapper reminderMapper;
    private final SeaService seaService;
    private final SysConfigService configService;
    // 【可观测性 2026-09-22】定时任务指标(放末位保持既有构造参数序稳定): 运行/入海/失败计数 + 末次成功时间
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    // 【裁决 2026-09-24】公海锁超时释放所需的审批读写
    private final com.aigicrm.module.approval.mapper.BizApprovalMapper approvalMapper;
    private final com.aigicrm.module.approval.mapper.BizApprovalNodeMapper approvalNodeMapper;
    private final com.aigicrm.module.sea.mapper.BizSeaPoolMapper seaPoolMapper;
    // 【H2 2026-10-08】定时任务系统身份:定时线程无 SecurityContext,prod 租户拦截器会把这些查询
    // 改写为恒空——以系统身份(admin, id=1)执行 2AM 任务链。放末位保持既有构造参数序稳定
    // (与 meterRegistry 注释同口径;UserDetailsService 复用登录链路的完整装配,与 JwtAuthFilter 同款注入)。
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    /** 【H2】系统身份用户 id:内置 admin(id=1),租户拦截器 ignoreTable 对 admin 放行=跨集团 */
    private static final long SYSTEM_ADMIN_USER_ID = 1L;

    /** 【裁决 2026-09-24】公海捞取审批锁超时自动释放: 时长=系统设置 sea.claim.lock.days(默认3,0=不启用)。
     *  锁定(pending_approval)超过 N 天的池行释放回 available 并清申请人, 同时把对应 claim 审批
     *  连同 pending 节点置 rejected(防释放后审批人补批造成错误过户);逐行隔离,失败不中断其余释放。 */
    void releaseStaleClaimLocks() {
        int lockDays = getConfigInt("sea.claim.lock.days", 3);
        if (lockDays <= 0) {
            log.info("公海锁超时释放未启用(sea.claim.lock.days={})", lockDays);
            return;
        }
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusDays(lockDays);
        List<com.aigicrm.module.sea.entity.BizSeaPool> stale = seaPoolMapper.selectList(
                new LambdaQueryWrapper<com.aigicrm.module.sea.entity.BizSeaPool>()
                        .eq(com.aigicrm.module.sea.entity.BizSeaPool::getStatus, "pending_approval")
                        .lt(com.aigicrm.module.sea.entity.BizSeaPool::getClaimDate, deadline));
        for (com.aigicrm.module.sea.entity.BizSeaPool lock : stale) {
            try {
                lock.setStatus("available");
                lock.setClaimUserId(null);
                lock.setClaimDate(null);
                seaPoolMapper.updateById(lock);
                com.aigicrm.module.approval.entity.BizApproval claim = approvalMapper.selectOne(
                        new LambdaQueryWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                                .eq(com.aigicrm.module.approval.entity.BizApproval::getApprovalType, "claim")
                                .eq(com.aigicrm.module.approval.entity.BizApproval::getCustomerId, lock.getCustomerId())
                                .eq(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing")
                                .orderByDesc(com.aigicrm.module.approval.entity.BizApproval::getId)
                                .last("LIMIT 1"));
                if (claim != null) {
                    approvalMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                            .set(com.aigicrm.module.approval.entity.BizApproval::getStatus, "rejected")
                            .eq(com.aigicrm.module.approval.entity.BizApproval::getId, claim.getId())
                            .eq(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing"));
                    approvalNodeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aigicrm.module.approval.entity.BizApprovalNode>()
                            .set(com.aigicrm.module.approval.entity.BizApprovalNode::getStatus, "rejected")
                            .set(com.aigicrm.module.approval.entity.BizApprovalNode::getAction, "reject")
                            .set(com.aigicrm.module.approval.entity.BizApprovalNode::getComment, "捞取审批锁超时自动释放")
                            .set(com.aigicrm.module.approval.entity.BizApprovalNode::getOperatedAt, java.time.LocalDateTime.now())
                            .eq(com.aigicrm.module.approval.entity.BizApprovalNode::getApprovalId, claim.getId())
                            .eq(com.aigicrm.module.approval.entity.BizApprovalNode::getStatus, "pending"));
                }
                meterRegistry.counter("sea.claim.lock.released").increment();
                log.info("公海锁超时释放: customerId={}, 锁定于{}, 审批单{}",
                        lock.getCustomerId(), lock.getClaimDate(), claim != null ? claim.getId() : "无");
            } catch (Exception e) {
                log.error("公海锁超时释放失败 customerId={}: {}", lock.getCustomerId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkCustomerExpire() {
        // 【H2 2026-10-08】整个 2AM 任务链(到期/宽限/开票超期/公海锁释放)以系统身份执行:
        // 定时线程无 SecurityContext,prod 租户拦截器(TenantLineInnerInterceptor)会把无会话查询
        // 改写为 tenant_id = NULL 恒空,锁释放/宽限静默失效。全局维护任务以系统身份跨集团处理
        // 全部租户数据属预期语义(与拦截器 ignoreTable 对 admin 放行一致);try-finally 还原,
        // 测试直调方法时无认证也能正确运行并还原(见 runWithSystemIdentity)。
        runWithSystemIdentity(this::doCheckCustomerExpire);
    }

    /**
     * 【H2 2026-10-08】以系统身份(admin, id=1)执行任务体,finally 还原进入前的认证状态。
     * <ul>
     *   <li>admin 加载失败/被禁用(库异常)→ 降级为无身份执行并留痕,不阻断任务——与修复前行为
     *       一致,此时租户插件开启下查询恒空,问题在日志与空结果中可见而非被吞;</li>
     *   <li>定时线程池线程复用,系统身份不得泄漏:finally 里把认证还原为进入前的值(通常为 null);</li>
     *   <li>每次运行时加载(而非启动时缓存一次):任务日跑一次成本可忽略,且权限/角色变更即时生效
     *       (UserDetailsService 自带 Redis 权限缓存)。</li>
     * </ul>
     */
    void runWithSystemIdentity(Runnable body) {
        org.springframework.security.core.Authentication previous =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        org.springframework.security.core.Authentication systemAuth = buildSystemAuthentication();
        if (systemAuth == null) {
            body.run();
            return;
        }
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(systemAuth);
            body.run();
        } finally {
            // 还原为进入前的认证(定时线程通常为 null),即使任务体抛错也必须还原
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    /** 【H2】构建系统身份认证:loadUserByUsername("1") 走 id=1 的内置 admin,复用登录链路的
     *  完整装配(角色/权限/集团);任何失败返回 null(调用方降级为无身份执行)。 */
    private org.springframework.security.core.Authentication buildSystemAuthentication() {
        try {
            org.springframework.security.core.userdetails.UserDetails details =
                    userDetailsService.loadUserByUsername(String.valueOf(SYSTEM_ADMIN_USER_ID));
            if (!(details instanceof com.aigicrm.security.SecurityUser securityUser)) {
                return null;
            }
            return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    securityUser, null, details.getAuthorities());
        } catch (Exception e) {
            log.warn("系统身份(admin id={})加载失败, 任务将以无身份执行(租户插件开启时查询会被改写为恒空): {}",
                    SYSTEM_ADMIN_USER_ID, e.getMessage());
            return null;
        }
    }

    private void doCheckCustomerExpire() {
        log.info("开始检查客户保护期...");
        LocalDate today = LocalDate.now();
        int warningDays = getConfigInt("customer.warning.days", 30);
        LocalDate warningDate = today.plusDays(warningDays);

        List<BizCustomer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                        .isNotNull(BizCustomer::getExpireDate)
        );

        // 【B1 2026-09-22 逐客户隔离】单条脏数据/唯一键冲突不再中断整批(运行事故实证:
        // 一条 Duplicate entry 曾令当晚后续所有客户漏检); 末尾汇总, failed>0 记 error 作告警钩子
        int enteredSea = 0, warned = 0, failed = 0;
        java.util.List<Long> failedIds = new java.util.ArrayList<>();
        for (BizCustomer customer : customers) {
          try {
            LocalDate expireDate = customer.getExpireDate();

            if (expireDate.isBefore(today) || expireDate.isEqual(today)) {
                // 已到期 -> 更新状态 + 进入公海
                if (!"expired".equals(customer.getProtectionStatus())) {
                    customer.setProtectionStatus("expired");
                    customer.setStatus("expired");
                    customerMapper.updateById(customer);
                }
                // 确保进了公海（防止状态已更新但未入池的情况）
                if (!"sea".equals(customer.getStatus())) {
                    // 【安全修复 2026-08-09】无公海池配置的办事处不自动入池（与手动释放一致），
                    // 避免产生 pool_id=NULL 的孤儿公海记录（普通用户不可见、也捞不了，变死数据）
                    if (seaService.matchSeaConfig(customer.getOfficeId()) != null) {
                        seaService.addToSea(customer.getId(), "保护期满");
                        log.info("客户 {} 保护期已到，进入公海", customer.getCustomerName());
                    } else {
                        log.info("客户 {} 所在办事处未配置公海池，保持过期状态", customer.getCustomerName());
                    }
                }
            } else if (!expireDate.isAfter(warningDate)) {
                // 即将到期（30天内,含第30天——【修复 2026-10-08 需求对齐】与 ReminderService 的
                // le(expireDate, today+warningDays) 口径对齐,原 isBefore 使剩余恰好30天的客户
                // 页面可见/计角标/补生成会物化,凌晨任务却不物化,同窗两口径差1天）
                if (!"expiring".equals(customer.getProtectionStatus())) {
                    customer.setProtectionStatus("expiring");
                    customer.setStatus("warning");
                    customerMapper.updateById(customer);
                }
                // 创建即将到期提醒（去重:pending 存在或今日已处理均不建——与 ReminderService.ensureExpiring
                // 同一判据,双口径曾是 401 条 pending 积压的根因;凌晨2点跑,processedToday 只拦 0-2 点处理过的情形）
                long existExpiring = reminderMapper.selectCount(
                        new LambdaQueryWrapper<BizReminder>()
                                .eq(BizReminder::getCustomerId, customer.getId())
                                .eq(BizReminder::getReminderType, "expiring")
                                .eq(BizReminder::getStatus, "pending")
                );
                long processedToday = existExpiring > 0 ? 1 : reminderMapper.selectCount(
                        new LambdaQueryWrapper<BizReminder>()
                                .eq(BizReminder::getCustomerId, customer.getId())
                                .eq(BizReminder::getReminderType, "expiring")
                                .eq(BizReminder::getStatus, "processed")
                                .ge(BizReminder::getProcessedAt, today.atStartOfDay())
                );
                if (existExpiring == 0 && processedToday == 0) {
                    long daysLeft = ChronoUnit.DAYS.between(today, expireDate);
                    BizReminder reminder = new BizReminder();
                    reminder.setCustomerId(customer.getId());
                    reminder.setReminderType("expiring");
                    reminder.setOwnerId(customer.getOwnerId());
                    reminder.setOfficeId(customer.getOfficeId());
                    reminder.setOverdueDays((int) daysLeft);
                    reminder.setStatus("pending");
                    reminderMapper.insert(reminder);
                    warned++;
                    log.info("客户 {} 还有{}天到期，已创建提醒", customer.getCustomerName(), daysLeft);
                }
            }
          } catch (Exception e) {
            failed++;
            failedIds.add(customer.getId());
            log.error("客户 {}({}) 保护期处理失败, 已跳过继续: {}", customer.getCustomerName(), customer.getId(), e.getMessage());
          }
        }
        String summary = String.format("客户保护期检查汇总: 共%d条, 入海%d, 新增预警%d, 失败%d %s",
                customers.size(), enteredSea, warned, failed, failedIds);
        if (failed > 0) {
            log.error("【告警】{} —— 失败客户需人工复核", summary);
        } else {
            log.info(summary);
        }
        // 【可观测性】Micrometer 计数与末次成功时间, /actuator/prometheus 可抓取, 告警规则可基于 customer_expire_failed_total
        meterRegistry.counter("customer_expire_run_total", "result", failed > 0 ? "partial" : "success").increment();
        meterRegistry.counter("customer_expire_entered_sea_total").increment(enteredSea);
        if (failed > 0) {
            meterRegistry.counter("customer_expire_failed_total").increment(failed);
        } else {
            meterRegistry.gauge("customer_expire_last_success_epoch_ms",
                    System.currentTimeMillis());
        }

        // 检查开票超一年的客户
        checkInvoiceOverdue(today);
        // 【裁决 2026-09-24】公海捞取审批锁超时释放(sea.claim.lock.days, 默认3天/0=不启用)
        releaseStaleClaimLocks();
        log.info("客户保护期检查完成");
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 【2026-08-08 设计规则：从未开票与开票超期分开处理】
     * - 已开票但超过 invoice.overdue.days 未再开票 → invoice_overdue（超期提醒，实际超期天数）
     * - 从未开票（last_invoice_date 为 NULL）→ invoice_never（待首次开票，独立类型，不进超期告警）
     * 修复：此前 NULL 被并入超期查询并写死 9999 天，误报"超期 9999 天"。
     */
    private void checkInvoiceOverdue(LocalDate today) {
        int overdueDaysConfig = getConfigInt("invoice.overdue.days", 365);
        LocalDate oneYearAgo = today.minusDays(overdueDaysConfig);

        List<BizCustomer> overdueCustomers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                        .isNotNull(BizCustomer::getLastInvoiceDate)
                        .lt(BizCustomer::getLastInvoiceDate, oneYearAgo)
        );
        for (BizCustomer customer : overdueCustomers) {
            int overdueDays = (int) ChronoUnit.DAYS.between(customer.getLastInvoiceDate(), today);
            createInvoiceReminder(customer, "invoice_overdue", overdueDays);
        }

        // 【裁决 2026-09-24 GPT清单P1-018 带默认值】从未开票提醒建客宽限期: 系统设置 invoice.never.grace.days
        // (默认30,0=无宽限保持原行为)。建客 created_at 早于(今天-宽限天数)才纳入催首次开票,
        // 刚建的客户不再当晚被催开票;按日界(当天0点)比较,任务凌晨2点跑不受时分影响。
        int neverGraceDays = getConfigInt("invoice.never.grace.days", 30);
        List<BizCustomer> neverInvoiced = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                        .isNull(BizCustomer::getLastInvoiceDate)
                        // 宽限天数<=0 时跳过 created_at 条件(条件重载),保持原"从未开票即提醒"行为
                        .lt(neverGraceDays > 0, BizCustomer::getCreatedAt, today.minusDays(neverGraceDays).atStartOfDay())
        );
        for (BizCustomer customer : neverInvoiced) {
            createInvoiceReminder(customer, "invoice_never", 0);
        }
    }

    private void createInvoiceReminder(BizCustomer customer, String type, int overdueDays) {
        // 检查是否已有未处理的提醒（按类型去重）
        long existCount = reminderMapper.selectCount(
                new LambdaQueryWrapper<BizReminder>()
                        .eq(BizReminder::getCustomerId, customer.getId())
                        .eq(BizReminder::getReminderType, type)
                        .eq(BizReminder::getStatus, "pending")
        );
        if (existCount > 0) {
            return;
        }
        BizReminder reminder = new BizReminder();
        reminder.setCustomerId(customer.getId());
        reminder.setReminderType(type);
        reminder.setOwnerId(customer.getOwnerId());
        reminder.setOfficeId(customer.getOfficeId());
        reminder.setOverdueDays(overdueDays);
        reminder.setStatus("pending");
        reminderMapper.insert(reminder);
        log.info("客户 {} 创建提醒 type={} overdueDays={}", customer.getCustomerName(), type, overdueDays);
    }
}
