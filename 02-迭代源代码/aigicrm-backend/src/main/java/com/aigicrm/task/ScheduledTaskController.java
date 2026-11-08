package com.aigicrm.task;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统定时任务手动触发入口(URL /api/system/task 不变)。改名避开 PR-10 任务模块 TaskController 的同名 bean 冲突 */
@RestController
@RequestMapping("/api/system/task")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final CustomerExpireTask customerExpireTask;

    /**
     * 【运维直调入口 2026-11-08 注记】双端无调用（PC/MP/scripts 全库 grep 零命中，
     * 日常到期批由定时调度与 /api/reminder/ensure-expiring 页面兜底承担）。
     * 管理员手动触发到期批的运维通道，保留不删；仅限系统管理员（下方 isAdmin 守卫）。
     */
    @PostMapping("/check-expire")
    public Result<String> checkExpire() {
        // M9: 触发全库批处理仅限系统管理员(原 system:config:view 销售员/行政均可触发,存在滥用风险)
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可触发该任务");
        }
        customerExpireTask.checkCustomerExpire();
        return Result.ok("客户保护期检查完成");
    }
}
