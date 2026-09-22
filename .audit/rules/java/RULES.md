# Java / Spring Boot 规则

- JAVA-01 事务:多表写必须 @Transactional;子表清理+主表删除原子(deleteRecord 实证)
- JAVA-02 MyBatis-Plus LambdaQueryWrapper 禁止字符串列名拼接;selectCount 判空
- JAVA-03 异常:业务拒绝抛 BusinessException(code,msg);不得吞异常清零门禁
- JAVA-04 审计字段:updated_by 显式注入;DB ON UPDATE 维护 updated_at
- JAVA-05 空安全:Optional/Objects.equals;S6213 受限标识符命名(record)
(静态分析主通道=服务端 SonarQube:BLOCKER→CRITICAL 映射见 normalize_results.py)
