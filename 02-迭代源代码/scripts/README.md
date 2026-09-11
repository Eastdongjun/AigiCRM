# 部署脚本

> 替代手工 ssh 部署步骤。所有脚本在 **Mac 本地**运行,通过 ssh 操作服务器。
> 凭据不写在脚本里,从 `~/.aigicrm-deploy.env` 读取(该文件不入 git,首次使用需创建)。

## 首次配置

```bash
cp scripts/aigicrm-deploy.env.example ~/.aigicrm-deploy.env
vim ~/.aigicrm-deploy.env   # 填入实际密码
chmod 600 ~/.aigicrm-deploy.env
```

## 使用

```bash
# 前端部署(Mac 构建 → 上传 → 切 NAS dist → 验证)
./scripts/deploy-frontend.sh

# 后端部署(测试 jar 提取 → 上传 → 重建镜像 → 重启 → 健康检查)
./scripts/deploy-backend.sh

# 数据库迁移(生产备份 → 执行迁移脚本 → 结构验证)
./scripts/deploy-db.sh <迁移SQL路径>

# 一键回滚(数据库 dump / dist / jar 从 NAS 备份恢复)
./scripts/rollback.sh
```

## 规范

- 每次部署后:同步 docker-compose.yml 与实况 → 提交 git
- 部署前:核对 `docs/deployment/config-matrix.md` 配置矩阵
- 所有脚本带校验,失败即停,不会静默半途而废
