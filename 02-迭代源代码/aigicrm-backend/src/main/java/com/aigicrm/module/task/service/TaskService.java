package com.aigicrm.module.task.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.task.entity.BizTask;
import com.aigicrm.module.task.mapper.BizTaskMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【PR-10 2026-09-19】任务服务:跟进记录与下一步任务衔接。
 *
 * <p>口径(总册):简单记录不生成任务;明确承诺形成待办;
 * 完成必须记录结果(不是简单勾掉);提醒已读≠任务完成;
 * 任务入口嵌入现有页面(不加底部菜单)。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final BizTaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final BizCustomerMapper customerMapper;
    private final com.aigicrm.module.customer.mapper.BizCustomerLogMapper customerLogMapper;
    private final com.aigicrm.common.scope.OrgScopeResolver orgScopeResolver;
    private final com.aigicrm.common.engine.CustomerAccessService customerAccessService;

    /** 控制器 Map → 实体转换(控制器零 entity 依赖) */
    public BizTask createFromMap(Map<String, Object> body) {
        BizTask task = new BizTask();
        if (body.get("title") != null) task.setTitle(body.get("title").toString());
        if (body.get("detail") != null) task.setDetail(body.get("detail").toString());
        if (body.get("assigneeId") instanceof Number n) task.setAssigneeId(n.longValue());
        if (body.get("dueDate") != null) task.setDueDate(LocalDate.parse(body.get("dueDate").toString()));
        if (body.get("customerId") instanceof Number n) task.setCustomerId(n.longValue());
        if (body.get("sourceLogId") instanceof Number n) task.setSourceLogId(n.longValue());
        return create(task);
    }

    public void updateFromMap(Long id, Map<String, Object> body) {
        BizTask task = new BizTask();
        task.setId(id);
        if (body.get("title") != null) task.setTitle(body.get("title").toString());
        if (body.get("detail") != null) task.setDetail(body.get("detail").toString());
        if (body.get("dueDate") != null) task.setDueDate(LocalDate.parse(body.get("dueDate").toString()));
        update(task);
    }

    /** 创建任务(跟进日志中"下一步"承诺落地) */
    @Transactional
    public BizTask create(BizTask task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new BusinessException("任务标题不能为空");
        }
        if (task.getAssigneeId() == null) {
            throw new BusinessException("责任人不能为空");
        }
        SysUser assignee = userMapper.selectById(task.getAssigneeId());
        if (assignee == null || assignee.getStatus() == null || assignee.getStatus() != 1) {
            throw new BusinessException("责任人不存在或已停用");
        }
        // 【GPT复审 P1-04 修复 2026-09-21】分派目标须在操作者可见范围(与查询侧 assertAssigneeVisible 同口径)
        assertAssigneeVisible(task.getAssigneeId(), SecurityUtils.getCurrentUserId());
        // 【GPT复审 P1-05 修复 2026-09-21】关联对象守卫: 客户须存在未删, 跟进日志须归属该客户
        // 【GPT三轮 TASK-P1-003 2026-09-22】存在性只防跨租户; 同集团横向须再套客户对象 DataScope 守卫
        if (task.getCustomerId() != null) {
            var customer = customerMapper.selectById(task.getCustomerId());
            if (customer == null || (customer.getDeleted() != null && customer.getDeleted() == 1)) {
                throw new BusinessException("关联客户不存在");
            }
            customerAccessService.assertCanView(task.getCustomerId());
        }
        if (task.getSourceLogId() != null) {
            if (task.getCustomerId() == null) {
                throw new BusinessException("关联跟进日志必须同时指定客户");
            }
            var log = customerLogMapper.selectById(task.getSourceLogId());
            if (log == null || !task.getCustomerId().equals(log.getCustomerId())) {
                throw new BusinessException("跟进日志不存在或不属于该客户");
            }
        }
        task.setCreatedBy(SecurityUtils.getCurrentUserId());
        task.setStatus("pending");
        if (task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now())) {
            throw new BusinessException("计划时间不能早于今天");
        }
        taskMapper.insert(task);
        return task;
    }

    /** 完成任务(必须记录结果——总册:"任务完成必须保留结果") */
    @Transactional
    public void complete(Long id, String completionNote) {
        if (completionNote == null || completionNote.trim().isEmpty()) {
            throw new BusinessException("完成时必须记录结果(不是简单勾掉)");
        }
        BizTask task = getOwnOrManaged(id);
        if ("completed".equals(task.getStatus())) {
            throw new BusinessException("该任务已完成");
        }
        if ("cancelled".equals(task.getStatus())) {
            throw new BusinessException("该任务已取消");
        }
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletionNote(completionNote.trim());
        taskMapper.updateById(task);
    }

    /** 取消任务(记录原因) */
    @Transactional
    public void cancel(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("取消任务必须填写原因");
        }
        BizTask task = getOwnOrManaged(id);
        if ("completed".equals(task.getStatus())) {
            throw new BusinessException("已完成的任务不能取消");
        }
        task.setStatus("cancelled");
        task.setCompletionNote("取消原因: " + reason.trim());
        taskMapper.updateById(task);
    }

    /** 转交任务(责任人变更) */
    @Transactional
    public void transfer(Long id, Long newAssigneeId) {
        BizTask task = getOwnOrManaged(id);
        SysUser newAssignee = userMapper.selectById(newAssigneeId);
        if (newAssignee == null || newAssignee.getStatus() == null || newAssignee.getStatus() != 1) {
            throw new BusinessException("新责任人不存在或已停用");
        }
        // 【GPT复审 P1-04】转交目标同样须在可见范围
        assertAssigneeVisible(newAssigneeId, SecurityUtils.getCurrentUserId());
        task.setAssigneeId(newAssigneeId);
        taskMapper.updateById(task);
    }

    /** 更新任务(标题/详情/截止时间) */
    @Transactional
    public void update(BizTask update) {
        BizTask existing = getOwnOrManaged(update.getId());
        if ("completed".equals(existing.getStatus())) {
            throw new BusinessException("已完成的任务不可编辑");
        }
        if (update.getTitle() != null) existing.setTitle(update.getTitle());
        if (update.getDetail() != null) existing.setDetail(update.getDetail());
        if (update.getDueDate() != null) existing.setDueDate(update.getDueDate());
        taskMapper.updateById(existing);
    }

    /**
     * 【权限审计批次1 2026-09-21】显式查看他人任务/统计的前置校验:
     * admin 或本人直接放行; 其余须在 task 模块数据范围的可见人员集合内(resolveModuleUsers
     * null=全量不过滤, 空=仅本人语义)。越界抛 403, 杜绝传 assigneeId 绕过"默认只看自己"。
     */
    private void assertAssigneeVisible(Long targetUserId, Long currentUserId) {
        if (SecurityUtils.isAdmin() || targetUserId.equals(currentUserId)) return;
        // 【GPT三轮 ACL-P1-001 2026-09-22】sys_user 全局表: null(all) 须集团收口, 否则可分派范围外/他集团人员
        java.util.List<Long> visible = orgScopeResolver.resolveModuleUsersWithinGroup(SecurityUtils.getCurrentUser(), "task");
        if (!visible.contains(targetUserId)) {
            throw new BusinessException(403, "无权查看该负责人的任务");
        }
    }

    /** 分页查询(按状态/责任人/客户筛选;self 范围=仅本人的) */
    public IPage<BizTask> page(int current, int size, String status, Long assigneeId,
                                Long customerId, Boolean overdue) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<BizTask> wrapper = new LambdaQueryWrapper<>();
        // 非 admin 且未指定他人:默认只看自己的任务
        // 【权限审计批次1 2026-09-21】参数型 IDOR 收口: 显式查他人任务须在 task 模块可见人员集合内
        if (assigneeId != null) {
            assertAssigneeVisible(assigneeId, currentUserId);
            wrapper.eq(BizTask::getAssigneeId, assigneeId);
        } else if (!SecurityUtils.isAdmin()) {
            wrapper.eq(BizTask::getAssigneeId, currentUserId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(BizTask::getStatus, status);
        }
        if (customerId != null) {
            wrapper.eq(BizTask::getCustomerId, customerId);
        }
        if (Boolean.TRUE.equals(overdue)) {
            wrapper.eq(BizTask::getStatus, "pending")
                   .lt(BizTask::getDueDate, LocalDate.now());
        }
        wrapper.orderByAsc(BizTask::getDueDate).orderByDesc(BizTask::getId);

        IPage<BizTask> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizTask t : page.getRecords()) {
            fillNames(t);
        }
        return page;
    }

    /** 统计(工作台卡片用) */
    public Map<String, Long> statistics(Long userId) {
        Map<String, Long> stats = new HashMap<>();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = userId != null ? userId : currentUserId;
        // 【权限审计批次1】同 page(): 他人统计须可见
        if (userId != null) assertAssigneeVisible(userId, currentUserId);
        stats.put("pending", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, "pending")));
        stats.put("overdue", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, "pending")
                .lt(BizTask::getDueDate, LocalDate.now())));
        stats.put("todayDue", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, "pending")
                .eq(BizTask::getDueDate, LocalDate.now())));
        stats.put("completedThisMonth", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, "completed")
                .ge(BizTask::getCompletedAt, LocalDate.now().withDayOfMonth(1).atStartOfDay())));
        return stats;
    }

    // ===== 内部 =====

    private BizTask getOwnOrManaged(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (!SecurityUtils.isAdmin()
                && !task.getAssigneeId().equals(SecurityUtils.getCurrentUserId())
                && !task.getCreatedBy().equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "只能操作自己创建或负责的任务");
        }
        return task;
    }

    private void fillNames(BizTask task) {
        if (task.getAssigneeId() != null) {
            SysUser u = userMapper.selectById(task.getAssigneeId());
            if (u != null) task.setAssigneeName(u.getRealName());
        }
        if (task.getCustomerId() != null) {
            BizCustomer c = customerMapper.selectById(task.getCustomerId());
            if (c != null) task.setCustomerName(c.getCustomerName());
        }
        task.setOverdue("pending".equals(task.getStatus())
                && task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now()));
    }
}
