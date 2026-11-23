# B1 最小监控接线（S6.4 部分）

目标：不登录、不依赖应用账号，让外部监控看到「API 活着、前端活着」。
方式：cron/systemd timer 跑 `probe.sh`，输出 Prometheus textfile；Prometheus 通过
`node_exporter --collector.textfile.directory` 读取并告警。

## 1. 部署探针

```bash
sudo mkdir -p /var/lib/node_exporter/textfile /opt/aigicrm
sudo cp scripts/product/monitoring/probe.sh /opt/aigicrm/probe.sh
sudo chmod 755 /opt/aigicrm/probe.sh
```

crontab（每分钟；失败仍写 0 指标，便于告警）：

```cron
* * * * * AIGICRM_API_URL=http://127.0.0.1:18080 AIGICRM_WEB_URL=http://127.0.0.1:18082 \
  /opt/aigicrm/probe.sh --out /var/lib/node_exporter/textfile/aigicrm.prom >/dev/null 2>&1 || true
```

验证：

```bash
bash scripts/product/monitoring/probe.sh --print \
  --api-url http://127.0.0.1:18080 --web-url http://127.0.0.1:18082
```

退出码 0=API+Web 都正常；1=至少一项失败；2=参数错误。指标：

- `aigicrm_api_up`：`/actuator/health` 为 UP 且 JSON 含 `"status":"UP"`；
- `aigicrm_web_up`：前端首页 200 且含 `<div id="app">`；
- `aigicrm_probe_duration_ms`、`aigicrm_last_probe_timestamp_seconds`。

## 2. Prometheus

`node_exporter` 启动参数：

```text
--collector.textfile.directory=/var/lib/node_exporter/textfile
```

`prometheus.yml` 片段：

```yaml
scrape_configs:
  - job_name: aigicrm-node
    static_configs:
      - targets: ['127.0.0.1:9100']
```

告警规则见 `alerts.yml`（API 连续 2 分钟不可用、前端连续 2 分钟不可用、探针过旧）。

## 3. 边界

- 不采集业务指标、不抓 `/actuator/prometheus`（该端点需登录态，避免把长期 token 落到监控配置）。
- 不替代应用日志/错误告警；错误信号仍依赖日志平台或后续接入。
- 本目录是 S6.4 的**最小存活探针**，不是完整 Prometheus/Grafana 栈。
