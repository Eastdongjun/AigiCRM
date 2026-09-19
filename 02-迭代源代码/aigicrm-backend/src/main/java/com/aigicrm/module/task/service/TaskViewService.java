package com.aigicrm.module.task.service;

import com.aigicrm.module.task.entity.BizTask;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 【PR-10】任务视图转换(控制器零 entity 依赖,分层约束)。 */
@Service
public class TaskViewService {

    public IPage<Map<String, Object>> toPage(IPage<BizTask> page) {
        return page.convert(TaskViewService::toRow);
    }

    public static Map<String, Object> toRow(BizTask t) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (t == null) return r;
        r.put("id", t.getId());
        r.put("customerId", t.getCustomerId());
        r.put("customerName", t.getCustomerName());
        r.put("title", t.getTitle());
        r.put("detail", t.getDetail());
        r.put("assigneeId", t.getAssigneeId());
        r.put("assigneeName", t.getAssigneeName());
        r.put("dueDate", t.getDueDate());
        r.put("status", t.getStatus());
        r.put("completedAt", t.getCompletedAt());
        r.put("completionNote", t.getCompletionNote());
        r.put("sourceLogId", t.getSourceLogId());
        r.put("overdue", t.getOverdue());
        r.put("createdAt", t.getCreatedAt());
        return r;
    }
}
