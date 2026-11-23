package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizContact;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerShare;
import com.aigicrm.module.customer.mapper.BizContactMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户分享与联系人。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出的第一块：分享（含集团边界/提醒联动）与联系人 CRUD。
 * 不依赖 CustomerService，避免门面反向依赖。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerContactShareService {

    private static final String LIT_X = "客户不存在";
    private static final String LIT_SHARE_TO = "share_to";
    private static final String LIT_PENDING = "pending";

    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper shareMapper;
    private final BizReminderMapper reminderMapper;
    private final BizContactMapper contactMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;

    public List<BizCustomerShare> getShares(Long customerId) {
        List<BizCustomerShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId));
        for (BizCustomerShare s : shares) {
            SysUser user = userMapper.selectById(s.getSharedToUserId());
            if (user != null) s.setSharedToUserName(user.getRealName());
        }
        return shares;
    }

    @Transactional
    public void addShares(Long customerId, List<Long> userIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new BusinessException(LIT_X);
        // 【PR-22 批次四 2026-09-19 活体发现】共享不得绕过集团边界(总册 F01):
        // 此前跨集团共享静默成功——写入乙集团用户永远无法使用的共享行,还向其发送
        // 提醒通知(脏数据+误导)。租户拦截器虽挡住了读取,写入本身也应拒绝。
        if (!userIds.isEmpty()) {
            Long group = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (group != null) {
                List<Long> groupMembers = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, group);
                List<Long> foreign = userIds.stream().filter(uid -> !groupMembers.contains(uid)).toList();
                if (!foreign.isEmpty()) {
                    throw new BusinessException("不能共享给其他集团用户（目标用户不在当前集团）");
                }
            } else {
                throw new BusinessException(403, "无有效集团归属，禁止共享");
            }
        }

        for (Long uid : userIds) {
            Long count = shareMapper.selectCount(
                    new LambdaQueryWrapper<BizCustomerShare>()
                            .eq(BizCustomerShare::getCustomerId, customerId)
                            .eq(BizCustomerShare::getSharedToUserId, uid));
            if (count == 0) {
                BizCustomerShare share = new BizCustomerShare();
                share.setCustomerId(customerId);
                share.setSharedToUserId(uid);
                share.setSharedByUserId(currentUserId);
                share.setCreatedAt(LocalDateTime.now());
                shareMapper.insert(share);

                // 1. 通知被分享人
                BizReminder r = new BizReminder();
                r.setCustomerId(customerId);
                r.setReminderType(LIT_SHARE_TO);
                r.setOwnerId(uid);
                r.setStatus(LIT_PENDING);
                r.setCreatedAt(LocalDateTime.now());
                reminderMapper.insert(r);

                // 2. 通知分享人
                BizReminder r2 = new BizReminder();
                r2.setCustomerId(customerId);
                r2.setReminderType("share_by");
                r2.setOwnerId(currentUserId);
                r2.setStatus(LIT_PENDING);
                r2.setCreatedAt(LocalDateTime.now());
                reminderMapper.insert(r2);
            }
        }

        // 3. 通知客户负责人（如果不是分享人自己）
        if (customer.getOwnerId() != null && !customer.getOwnerId().equals(currentUserId)) {
            BizReminder r3 = new BizReminder();
            r3.setCustomerId(customerId);
            r3.setReminderType("share_owner");
            r3.setOwnerId(customer.getOwnerId());
            r3.setStatus(LIT_PENDING);
            r3.setCreatedAt(LocalDateTime.now());
            reminderMapper.insert(r3);
        }
    }

    public void removeShare(Long customerId, Long userId) {
        shareMapper.delete(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId)
                        .eq(BizCustomerShare::getSharedToUserId, userId));
        // 【2026-10-09】被移除者的待处理分享提醒随分享失效（分享已不存在，提醒永无合法处理路径）；
        // 分享人/负责人侧的成组通知，在其他分享仍在时不清掉。
        reminderMapper.delete(new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getCustomerId, customerId)
                .eq(BizReminder::getOwnerId, userId)
                .eq(BizReminder::getReminderType, LIT_SHARE_TO)
                .eq(BizReminder::getStatus, LIT_PENDING));
    }

    public void removeAllShares(Long customerId) {
        shareMapper.delete(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId));
        // 【2026-10-09】分享全部撤销(入海/捞取/删除/管理端清空)时,三类 pending 分享提醒
        // 指向的分享已不存在,一并清理;processed 历史留痕不动
        cleanPendingShareReminders(customerId);
    }

    /**
     * 【体检 P1 2026-10-12】清理客户全部类型的 pending 提醒(INV-05 全量口径)。
     * 背景: 四个生命周期钩子(删除/归档/入海/捞取)此前只清分享类, expiring/invoice_* 类
     * 死 pending 无常驻清理, 积压成「—」死行(2026-10-12 生产摸底 59 条; INV-05 条文为
     * 不限类型)。processed 历史留痕不动; 恢复/重新捞取后按各自规则重新生成。
     */
    public void cleanPendingReminders(Long customerId) {
        reminderMapper.delete(new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getCustomerId, customerId)
                .eq(BizReminder::getStatus, LIT_PENDING));
    }

    /**
     * 【体检 P1 2026-10-12】治愈回收: 客户重新开票(lastInvoiceDate 前移)后,
     * pending 的 invoice_never/invoice_overdue 提醒不再回收也不改判, 旧文案持续显示
     * 「待首次开票/已超期N天」且 N 冻结在创建日——治愈即清, 由凌晨任务按新事实重建。
     */
    void cleanInvoicePendingReminders(Long customerId) {
        reminderMapper.delete(new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getCustomerId, customerId)
                .in(BizReminder::getReminderType, java.util.List.of("invoice_never", "invoice_overdue"))
                .eq(BizReminder::getStatus, LIT_PENDING));
    }

    /**
     * 【2026-10-09】清理客户全部 pending 分享提醒(share_to/share_by/share_owner)。
     * 背景: 分享被撤销/客户终态转移后这些提醒悬挂留存,列表上 customerName 整列「—」
     * (客户被 @TableLogic 过滤,批量回填查不到)。与 V2026.09.23.8 到期提醒死 pending
     * 清理同口径: 客户不存在/已删/入海/归档即死,无合法处理路径。
     */
    private void cleanPendingShareReminders(Long customerId) {
        reminderMapper.delete(new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getCustomerId, customerId)
                .in(BizReminder::getReminderType, java.util.List.of(LIT_SHARE_TO, "share_by", "share_owner"))
                .eq(BizReminder::getStatus, LIT_PENDING));
    }

    public List<BizContact> getContacts(Long customerId) {
        return contactMapper.selectList(
                new LambdaQueryWrapper<BizContact>()
                        .eq(BizContact::getCustomerId, customerId)
                        .orderByAsc(BizContact::getId));
    }

    public void addContact(Long customerId, BizContact contact) {
        contact.setCustomerId(customerId);
        contactMapper.insert(contact);
    }

    @Transactional
    public void updateContact(Long customerId, Long contactId, BizContact contact) {
        BizContact existing = contactMapper.selectById(contactId);
        // 归属校验: 联系人必须属于请求路径中的客户, 防止跨客户改挂/删除(IDOR)
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            throw new BusinessException("联系人不存在或不属于该客户");
        }
        contact.setId(contactId);
        contact.setCustomerId(customerId);
        contactMapper.updateById(contact);
    }

    public void deleteContact(Long customerId, Long contactId) {
        BizContact existing = contactMapper.selectById(contactId);
        // 归属校验: 联系人必须属于请求路径中的客户, 防止跨客户删除(IDOR)
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            throw new BusinessException("联系人不存在或不属于该客户");
        }
        contactMapper.deleteById(contactId);
    }
}
