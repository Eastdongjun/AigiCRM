# 审计规则库索引

按 stack-profile 动态启用;本仓库无对应技术栈的目录不参与扫描(规范§5:不识别就不扫)。

| 目录 | 适用 | 状态 |
|---|---|---|
| security/ | 全栈通用安全 | 启用(核心) |
| java/ spring/ | Java 21 + Spring Boot 3 | 启用 |
| miniprogram/ | 微信小程序(46页) | 启用(专项) |
| api/ | REST(注解推导) | 启用 |
| business/ | 业务完整性/追踪矩阵 | 启用(Agent 审查用) |
| database/ | MySQL/Flyway 50 迁移 | 启用 |
| docker/ | Compose×2 + Dockerfile | 启用 |
| common/ | 通用工程规则 | 启用 |
| javascript/ vue/ css/ | 前端细则 | 由 SonarQube 服务端覆盖(uisrc 在 sonar.sources) |
| typescript/ | 本项目 UI 为 JS | 不适用 |
| go/ | 本项目无 Go | 不适用 |
| kubernetes/ | 本项目无 K8s | 不适用 |
