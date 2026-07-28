package com.ycmn.task;

import com.ycmn.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/task")
@RequiredArgsConstructor
public class TaskController {

    private final CustomerExpireTask customerExpireTask;

    @PostMapping("/check-expire")
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<String> checkExpire() {
        customerExpireTask.checkCustomerExpire();
        return Result.ok("客户保护期检查完成");
    }
}
