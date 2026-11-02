package com.aigicrm.module.customer.service;

import com.aigicrm.module.customer.entity.BizCustomerEvent;
import com.aigicrm.module.customer.mapper.BizCustomerEventMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户生命线事件(2026-11-02 裁决D)。
 * 只增不改: 各业务路径在关键日期事件发生时调 {@link #record} 追加一行,
 * PC 客户详情按时间倒序展示完整生命线。
 * 事件写入与主业务同事务(同库追加写,不吞主流程异常——记录失败即整体回滚,
 * 与"发票删除留痕"同一取舍: 宁可业务失败不留无痕成功)。
 */
@Service
public class CustomerEventService {

    public static final String TYPE_CREATED = "created";
    public static final String TYPE_INVOICE_ADV = "invoice_adv";
    public static final String TYPE_INVOICE_MANUAL = "invoice_manual";
    public static final String TYPE_EXTEND = "extend";
    public static final String TYPE_RELEASED = "released";
    public static final String TYPE_CLAIMED = "claimed";
    public static final String TYPE_TRANSFERRED = "transferred";

    /** 展示层事件类型白名单(新类型必须同时登记,防脏值直出) */
    public static final Set<String> ALL_TYPES = Set.of(
            TYPE_CREATED, TYPE_INVOICE_ADV, TYPE_INVOICE_MANUAL,
            TYPE_EXTEND, TYPE_RELEASED, TYPE_CLAIMED, TYPE_TRANSFERRED);

    private final BizCustomerEventMapper eventMapper;
    private final SysUserMapper sysUserMapper;

    public CustomerEventService(BizCustomerEventMapper eventMapper, SysUserMapper sysUserMapper) {
        this.eventMapper = eventMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Transactional
    public void record(Long customerId, String eventType, LocalDate eventDate, String detail,
                       String oldValue, String newValue, Long createdBy) {
        BizCustomerEvent e = new BizCustomerEvent();
        e.setCustomerId(customerId);
        e.setEventType(eventType);
        e.setEventDate(eventDate);
        e.setDetail(detail);
        e.setOldValue(oldValue);
        e.setNewValue(newValue);
        e.setCreatedBy(createdBy);
        eventMapper.insert(e);
    }

    /** 客户详情生命线: 时间倒序(同日内按id倒序,后写在上), 最多200条防超大客户刷屏 */
    public List<BizCustomerEvent> listByCustomer(Long customerId) {
        List<BizCustomerEvent> events = eventMapper.selectList(new LambdaQueryWrapper<BizCustomerEvent>()
                .eq(BizCustomerEvent::getCustomerId, customerId)
                .in(BizCustomerEvent::getEventType, ALL_TYPES)
                .orderByDesc(BizCustomerEvent::getEventDate)
                .orderByDesc(BizCustomerEvent::getId)
                .last("LIMIT 200"));
        fillNames(events);
        return events;
    }

    private void fillNames(List<BizCustomerEvent> events) {
        if (events == null || events.isEmpty()) return;
        Set<Long> userIds = events.stream()
                .map(BizCustomerEvent::getCreatedBy).filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return;
        sysUserMapper.selectByIds(userIds).forEach(
                u -> events.stream()
                        .filter(e -> u.getId().equals(e.getCreatedBy()))
                        .forEach(e -> e.setCreatedByName(u.getRealName())));
        // SysUser 可能被软删后 selectByIds 仍返回: 名字缺失保持 null, 前端显示"系统"
    }
}
