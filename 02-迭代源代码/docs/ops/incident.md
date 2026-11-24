# 生产故障处理手册

> 故障不可怕,可怕的是没有流程。先恢复业务,再查根因。

## 一、故障等级

| 等级 | 示例 | 响应要求 | 处理人 |
|---|---|---|---|
| **P0** | 系统完全不可用/数据丢失/安全事件 | 立即 | 用户 + AI(本环境唯一处理资源) |
| **P1** | 核心功能不可用(登录/客户/激励大面积失败) | 30 分钟内 | 用户 + AI |
| **P2** | 部分功能异常(单页面/单接口失败) | 当天 | 用户 + AI |
| **P3** | 一般缺陷/文案/边缘场景 | 排期 | 记录到待办 |

## 二、处理流程(顺序执行)

```text
1. 确认故障(来源: 健康日报/用户反馈/冒烟失败)
2. 定级(P0/P1/P2/P3)
3. 停止扩大(如有发布在途立即停止)
4. 止损/回滚(rollback.sh —— 优先恢复业务)
5. 恢复验证(健康检查 + 核心链路冒烟)
6. 根因分析(见复盘七问)
7. 整改任务(记录到 evolution,建立防复发机制)
```

## 三、关键工具(故障时)

| 动作 | 命令/路径 |
|---|---|
| 应用回滚(只换 jar 或前端, 不动数据) | `scripts/rollback.sh backend` / `scripts/rollback.sh frontend` |
| 数据库回滚(**会丢掉备份之后写入的数据**) | `scripts/rollback.sh db`。仅在确认要回到那份备份时使用; 用之前先看第五节 backup_* 各项的含义 |
| ⚠️ 不要随手跑 `rollback.sh` | 它不带参数等同于 `all`(库 + 前端 + 后端一起回滚), 一次误跑就会丢掉当天备份之后写入的数据 |
| 冒烟验证 | `scripts/smoke-test.sh prod` |
| 交付门禁 | `scripts/verify-delivery.sh` |
| 恢复备份 | NAS `/Aigicrm/files/backup/daily/` 或 `20260801`/`20260802-pre-deploy` |
| 查看日志 | 生产 `docker logs ycmn-backend --tail 200`(容器名是 `ycmn-backend`; 旧名 `aigicrm-backend` 已不用) |
| 健康日报 | NAS `backup/daily/<日期>/report.md` |
| 告警自检(服务器本机) | `scripts/ops-alert-check.sh`, 见第五、六节 |

## 四、复盘七问(事故后必须回答)

1. **为什么发生?**(根因,不是表象)
2. **为什么测试没发现?**(冒烟/回归缺口)
3. **为什么监控没报警?**(日报没覆盖/没看)
4. **为什么影响这么大?**(波及范围评估缺失?)
5. **为什么恢复这么慢?**(流程/工具/知识缺失?)
6. **如何避免再次发生?**(防复发机制)
7. **谁在什么时间完成整改?**(任务化,进 evolution)

复盘结果:有防复发价值的写入 evolution(受控进化);整改必须形成回归用例或门禁,不允许只写散文。

## 五、告警(GL-13 2026-11-24)

`scripts/ops-alert-check.sh` 在生产服务器本机由 cron 每 5 分钟跑一次, 全部检查都只读。
**当前状态: 脚本已就绪, 还没有接线**(装到服务器、配 webhook、加定时都需要单独授权, 步骤见第六节)。

每个告警有自己的键。同一个键首次出现时通知, 之后 60 分钟内不重复, 到点提醒一次"仍在告警", 恢复后再通知一次。
全部正常时不发任何通知。退出码: 0 正常; 1 有告警; 2 自检自身失效; 3 有告警但通知发不出去。

| 告警键 | 含义 | 等级 | 处置步骤 |
|---|---|---|---|
| `backend_down` | 后端容器不在运行 | P0 | ① `docker ps -a \| grep ycmn-backend` 看状态与退出码; ② `docker logs ycmn-backend --tail 200` 找原因(OOM、启动失败); ③ 刚做过部署就 `scripts/rollback.sh backend`; 不是部署引起就 `docker start ycmn-backend`, 起不来按启动日志处理 |
| `health_down` | 容器在跑但健康检查不是 UP | P0 | ① 看日志里数据库、Redis、NAS 的连接错误; ② 查 `ycmn-mysql` / `ycmn-redis` 容器是否在跑; ③ 库或缓存重启过, 后端一般会自行恢复, 10 分钟不恢复再重启后端 |
| `public_unreachable` | 公网入口 `/api/system/configs/public` 不通, 或返回的不是接口响应 | P0 | ① 本机 `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/system/configs/public` 区分是后端还是 nginx 的问题; ② 后端直连通、公网不通 → 查前端容器与 nginx 配置(刚同步过 nginx 配置就回退它); ③ 都不通 → 查 DNS、证书、安全组 |
| `error_log` | 近 5 分钟容器日志错误超过阈值 | P1 | ① `docker logs --since 10m ycmn-backend 2>&1 \| grep -E 'ERROR\|Duplicate entry\|OutOfMemoryError' \| tail -50`; ② 同一条堆栈反复出现才算故障, 偶发一条先观察; ③ `Duplicate entry` 多为重复提交, 查对应接口; `OutOfMemoryError` 需要看堆内存趋势 |
| `disk:<路径>` | 文件系统使用率达到 85% | P1 | ① `df -h` 与 `docker system df`; ② 清理旧镜像与停止的容器(`docker image prune` 先看清单再执行); ③ `/data` 满多半是日志或备份, 看 `du -sh /data/*`; ④ 清不下来就扩容, 不要删 NAS 上的备份 |
| `nas_unmounted:<挂载点>` | NAS 挂载点掉盘 | P0 | 上传、导出、备份都会失败。① `mount \| grep /Aigicrm`; ② 挂载没了按云厂商的挂载步骤重挂; ③ 重挂后确认目录属主与权限没变, 再看 `backup_*` 告警 |
| `cert_expiring` | 证书 21 天内到期 | P1 | ① `openssl s_client -servername www.aigicrm.com -connect www.aigicrm.com:443 \| openssl x509 -noout -enddate`; ② 续期后重载 nginx; ③ 用 `check-release-readiness.sh` 或浏览器确认链完整 |
| `backup_missing` | 找不到当天的数据库备份 | P1 | ① 看 NAS `/Aigicrm/files/backup/daily/` 最新目录; ② 服务器上的日备 cron 是否还在(`crontab -l` 里应有 `*-daily-backup`); ③ 修好当天先手工补一份 `mysqldump`, 再查 cron 停掉的原因 |
| `backup_stale` | 最新备份超过 26 小时 | P1 | 同 `backup_missing`。日备在凌晨 2 点半左右, 26 小时意味着已经漏了一整晚 |
| `backup_incomplete` | 最新备份是空文件或缺少结尾标记 | P1 | 备份过程被中断(磁盘满、容器被杀)。先清磁盘, 再手工补一份完整备份, 并用 `tail -n 1` 确认有 `Dump completed` |
| `selfcheck` | 自检自身不完整(缺工具、探针输出读不懂、状态目录不可写) | P1 | 检查器坏了等于没有监控。通知里会写明缺什么: ① 缺命令就装; ② 探针输出读不懂多半是系统命令变了, 手工跑一遍通知里那条; ③ 状态目录不可写就改权限, 否则冷却不生效会刷屏 |

处置的共同原则:

- 先在服务器上手工确认, 再动手; 通知里的主机名、时间、样例能帮你定位是哪一台、哪一轮。
- 涉及回滚的, 先看第三节关于 `rollback.sh` 的警告。应用层回滚和数据库回滚是两回事。
- 自愈重启默认关闭。`AIGI_ALERT_AUTOHEAL=1` 时, 探活失败会自动 `docker restart ycmn-backend`(30 分钟内只重启一次)。开启之前要确认重启不会掩盖需要保留的现场。

## 六、告警接线(需单独授权)

下面是接到生产服务器的步骤。每一步都是生产变更, **没有授权不要执行**。

1. 把 `scripts/ops-alert-check.sh` 放到服务器(例如 `/opt/aigicrm/ops-alert-check.sh`), `chmod 755`。
2. 写配置文件(例如 `/etc/aigicrm-alert.env`, `chmod 600`), 至少两项:
   ```sh
   AIGICRM_ALERT_WEBHOOK='<机器人 webhook 地址>'   # 不要写进任何文档或仓库
   AIGICRM_ALERT_CHANNEL=wecom                     # wecom | dingtalk | feishu | generic
   ```
   其余各项都有默认值, 含义见脚本头部注释。
3. 先手工跑一次, 确认输出与退出码:
   `AIGICRM_ALERT_CONF=/etc/aigicrm-alert.env /opt/aigicrm/ops-alert-check.sh; echo "rc=$?"`
   预期 `rc=0`, 日志(`/var/log/aigicrm-alert.log`)里有一行 `OK`。
4. 试触发(测试环境先做一遍, 见计划 GL-24): 停掉后端 → 5 分钟内收到 `backend_down`; 启回来 → 收到一条 `恢复`。再确认冷却: 故障持续时不会每 5 分钟响一次。
5. 加定时: `*/5 * * * * AIGICRM_ALERT_CONF=/etc/aigicrm-alert.env /opt/aigicrm/ops-alert-check.sh >> /var/log/aigicrm-alert.cron.log 2>&1`
6. 回填记录: webhook 的类型、负责接收的人、接线时间写进运行记录; 定时任务登记到 AGENTS.md 铁律第 5 条要求的清单里。
7. 撤销: 删掉 crontab 里那一行即可, 脚本与配置不碰任何服务。

已知边界:

- 定时任务自己停了, 没有人会知道(监控的监控)。日志里每 5 分钟会有一行 `OK`, 可以用外部的巡检去查这行的最后时间。
- 自检在服务器本机跑, 服务器整机宕机时它也跟着停, 这种情况只能靠外部拨测。
