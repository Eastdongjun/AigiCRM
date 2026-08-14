package com.ycmn.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_customer_log")
public class BizCustomerLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long userId;
    private String logType;
    private String content;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String userName;
}
