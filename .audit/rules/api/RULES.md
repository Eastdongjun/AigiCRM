# REST API 规则(注解推导型,无独立 OpenAPI 文件)

- API-01 每个端点必须声明 @PreAuthorize 权限码;例外必须进 PermissionMatrixTest 白名单并写明理由
- API-02 方法内对象守卫(归属/范围)与方法级功能码缺一不可;矩阵测试反向漂移清单必须为空
- API-03 统一响应 {code,data,message};业务异常 BusinessException(503) 必须映射 HTTP 503
- API-04 错误响应带支持编号 S-<ts>-<rand>,日志可按编号反查堆栈(真机UAT定位先例)
- API-05 分页/过滤参数服务端校验;导出与列表同权限同范围(导出随 view 收口实证)
- API-06 幂等:资金/审批类写接口带幂等键;fail-mode=strict 时重复键直接告警不吞
