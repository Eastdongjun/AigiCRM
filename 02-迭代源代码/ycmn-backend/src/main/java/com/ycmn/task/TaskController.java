package com.ycmn.task;

import com.ycmn.common.BusinessException;
import com.ycmn.common.Result;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/task")
@RequiredArgsConstructor
public class TaskController {

    private final CustomerExpireTask customerExpireTask;

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
