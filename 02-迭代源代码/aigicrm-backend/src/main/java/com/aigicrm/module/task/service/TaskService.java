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
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_TITLE = "title";
    private static final String LIT_COMPLETED = "completed";
    private static final String LIT_CANCELLED = "cancelled";
    private static final String LIT_DETAIL = "detail";
    private static final String LIT_DUEDATE = "dueDate";
    private static final String LIT_PENDING = "pending";


    private final BizTaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final BizCustomerMapper customerMapper;
    private final com.aigicrm.module.customer.mapper.BizCustomerLogMapper customerLogMapper;
    private final com.aigicrm.common.scope.OrgScopeResolver orgScopeResolver;
    private final com.aigicrm.common.engine.CustomerAccessService customerAccessService;

    /** 【2026-11-17 阶段三】统一回收站台账；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.system.service.RecycleBinService recycleBinService;

    /**
     * 控制器 Map → 实体转换(控制器零 entity 依赖)。
     * 【S2229 2026-11-23】此处 this.create(task) 是同类自调用，Spring 代理被绕过——
     * create() 上的 @Transactional 实际不生效（历史上所有走 Map 入口的建单都不在事务里）。
     * 事务边界上移到本方法：控制器入口即开事务，内部自调用不再需要代理。
     */
    /** 创建任务(跟进日志中"下一步"承诺落地) */
    @Transactional
    public BizTask create(BizTask task) {
        requireTaskTitle(task);
        requireActiveAssignee(task);
        validateCustomerLink(task.getCustomerId());
        validateSourceLogLink(task.getCustomerId(), task.getSourceLogId());
        task.setCreatedBy(SecurityUtils.getCurrentUserId());
        task.setStatus(LIT_PENDING);
        if (task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now())) {
            throw new BusinessException("计划时间不能早于今天");
        }
        taskMapper.insert(task);
        return task;
    }

    private void requireTaskTitle(BizTask task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new BusinessException("任务标题不能为空");
        }
    }

    private void requireActiveAssignee(BizTask task) {
        if (task.getAssigneeId() == null) throw new BusinessException("责任人不能为空");
        SysUser assignee = userMapper.selectById(task.getAssigneeId());
        if (assignee == null || assignee.getStatus() == null || assignee.getStatus() != 1) {
            throw new BusinessException("责任人不存在或已停用");
        }
        // 分派目标须在操作者可见范围(与查询侧 assertAssigneeVisible 同口径)
        assertAssigneeVisible(task.getAssigneeId(), SecurityUtils.getCurrentUserId());
    }

    private void validateCustomerLink(Long customerId) {
        if (customerId == null) return;
        var customer = customerMapper.selectById(customerId);
        if (customer == null || (customer.getDeleted() != null && customer.getDeleted() == 1)) {
            throw new BusinessException("关联客户不存在");
        }
        customerAccessService.assertCanView(customerId);
    }

    private void validateSourceLogLink(Long customerId, Long sourceLogId) {
        if (sourceLogId == null) return;
        if (customerId == null) throw new BusinessException("关联跟进日志必须同时指定客户");
        var log = customerLogMapper.selectById(sourceLogId);
        if (log == null || !customerId.equals(log.getCustomerId())) {
            throw new BusinessException("跟进日志不存在或不属于该客户");
        }
    }

    /** 完成任务(必须记录结果——总册:"任务完成必须保留结果") */
    @Transactional
    public void complete(Long id, String completionNote) {
        if (completionNote == null || completionNote.trim().isEmpty()) {
            throw new BusinessException("完成时必须记录结果(不是简单勾掉)");
        }
        BizTask task = getOwnOrManaged(id);
        if (LIT_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("该任务已完成");
        }
        if (LIT_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("该任务已取消");
        }
        task.setStatus(LIT_COMPLETED);
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
        if (LIT_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("已完成的任务不能取消");
        }
        // 【P2 修复 2026-11-02】已取消任务不能重复取消(原漏挡, 重复取消会反复覆盖取消原因)
        if (LIT_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("该任务已取消");
        }
        task.setStatus(LIT_CANCELLED);
        task.setCompletionNote("取消原因: " + reason.trim());
        taskMapper.updateById(task);
    }

    /** 【2026-11-17 阶段三】删除任务进统一回收站：软删 + 删除审计，可在系统回收站恢复。 */
    @Transactional
    public void delete(Long id, String reason) {
        BizTask task = getOwnOrManaged(id);
        if (LIT_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("已完成的任务不能删除");
        }
        task.setDeletedAt(LocalDateTime.now());
        task.setDeletedBy(SecurityUtils.getCurrentUserId());
        task.setDeletedReason(reason == null || reason.isBlank() ? "用户删除" : reason.trim());
        taskMapper.updateById(task);
        taskMapper.deleteById(id);
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", id);
            snap.put(LIT_TITLE, task.getTitle());
            snap.put("assigneeId", task.getAssigneeId());
            snap.put("customerId", task.getCustomerId());
            recycleBinService.record("task", id, snap, task.getDeletedReason(), "user");
        }
    }

    /** 转交任务(责任人变更) */
    @Transactional
    public void transfer(Long id, Long newAssigneeId) {
        BizTask task = getOwnOrManaged(id);
        // 【P2 修复 2026-11-02】终态任务不可转交(原无守卫, 已完成/已取消仍可换人制造僵尸归属)
        if (LIT_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("已完成的任务不能转交");
        }
        if (LIT_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("已取消的任务不能转交");
        }
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
        if (LIT_COMPLETED.equals(existing.getStatus())) {
            throw new BusinessException("已完成的任务不可编辑");
        }
        if (update.getTitle() != null) existing.setTitle(update.getTitle());
        if (update.getDetail() != null) existing.setDetail(update.getDetail());
        // 【2026-11-01 审计修复】与 create 同口径守卫(原可把截止改成过去日期, 绕过录入约束)
        if (update.getDueDate() != null) {
            if (update.getDueDate().isBefore(java.time.LocalDate.now())) {
                throw new BusinessException("计划时间不能早于今天");
            }
            existing.setDueDate(update.getDueDate());
        }
        taskMapper.updateById(existing);
    }

    /**
     * 【权限审计批次1 2026-09-21】显式查看他人任务/统计的前置校验:
     * admin 或本人直接放行; 其余须在 task 模块数据范围的可见人员集合内(resolveModuleUsers
     * null=全量不过滤, 空=仅本人语义)。越界抛 403, 杜绝传 assigneeId 绕过"默认只看自己"。
     */
    private void assertAssigneeVisible(Long targetUserId, Long currentUserId) {
        if (targetUserId.equals(currentUserId)) return;
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
        } else {
            wrapper.eq(BizTask::getAssigneeId, currentUserId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(BizTask::getStatus, status);
        }
        if (customerId != null) {
            wrapper.eq(BizTask::getCustomerId, customerId);
        }
        if (Boolean.TRUE.equals(overdue)) {
            wrapper.eq(BizTask::getStatus, LIT_PENDING)
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
        stats.put(LIT_PENDING, taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, LIT_PENDING)));
        stats.put("overdue", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, LIT_PENDING)
                .lt(BizTask::getDueDate, LocalDate.now())));
        stats.put("todayDue", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, LIT_PENDING)
                .eq(BizTask::getDueDate, LocalDate.now())));
        stats.put("completedThisMonth", taskMapper.selectCount(new LambdaQueryWrapper<BizTask>()
                .eq(BizTask::getAssigneeId, targetUserId)
                .eq(BizTask::getStatus, LIT_COMPLETED)
                .ge(BizTask::getCompletedAt, LocalDate.now().withDayOfMonth(1).atStartOfDay())));
        return stats;
    }

    // ===== 内部 =====

    private BizTask getOwnOrManaged(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (!task.getAssigneeId().equals(SecurityUtils.getCurrentUserId())
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
        task.setOverdue(LIT_PENDING.equals(task.getStatus())
                && task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now()));
    }
}
