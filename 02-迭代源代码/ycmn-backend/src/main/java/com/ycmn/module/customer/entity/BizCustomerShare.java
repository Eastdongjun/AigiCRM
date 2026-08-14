package com.ycmn.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_customer_share")
public class BizCustomerShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long sharedToUserId;
    private Long sharedByUserId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String sharedToUserName;
}
