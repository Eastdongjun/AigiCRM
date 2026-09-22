# Docker / Compose / Nginx 规则

- DOCKER-01 凭据经部署 env 注入(:?强校验),compose 文件零明文密钥
- DOCKER-02 后端 8080 绑 127.0.0.1;MySQL/Redis 仅容器网;公网仅 80/443
- DOCKER-03 Actuator 经 nginx 收敛:health 匿名无组件详情,metrics/prometheus 认证保护
- DOCKER-04 Clean Room:空库 Flyway 重放 48/48 零失败作为镜像/迁移准入
- DOCKER-05 备份脚本失败=硬退出(mysqldump 非零/空文件),不产生假备份
