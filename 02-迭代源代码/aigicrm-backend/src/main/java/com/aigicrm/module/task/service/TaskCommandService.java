package com.aigicrm.module.task.service;

import com.aigicrm.module.task.entity.BizTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 任务请求 Map → 实体 → 写服务的编排层。
 *
 * <p>放在服务层而不是 Controller，避免控制器依赖实体；同时把 create/update
 * 转为对注入 TaskService 的调用，消除同类 @Transactional 自调用。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskCommandService {

    private final TaskService taskService;
    private final TaskViewService taskViewService;

    public Map<String, Object> createFromMap(Map<String, Object> body) {
        return TaskViewService.toRow(taskService.create(fromMap(body)));
    }

    public void updateFromMap(Long id, Map<String, Object> body) {
        BizTask task = fromMap(body);
        task.setId(id);
        taskService.update(task);
    }

    private BizTask fromMap(Map<String, Object> body) {
        BizTask task = new BizTask();
        if (body.get("title") != null) task.setTitle(body.get("title").toString());
        if (body.get("detail") != null) task.setDetail(body.get("detail").toString());
        if (body.get("assigneeId") instanceof Number n) task.setAssigneeId(n.longValue());
        if (body.get("dueDate") != null) task.setDueDate(LocalDate.parse(body.get("dueDate").toString()));
        if (body.get("customerId") instanceof Number n) task.setCustomerId(n.longValue());
        if (body.get("sourceLogId") instanceof Number n) task.setSourceLogId(n.longValue());
        return task;
    }
}
