# 功能受理

## 用户目标
防复发门禁:前端性能反模式扫描 + 生产性能检查,接入交付门禁,杜绝"重复上传/blob泄漏/进度无节流/全量加载/gzip未开/nginx安全头丢失"类小错误复发。

## 当前行为
- 前端无 lint/扫描门禁;交付只跑 API 冒烟(不覆盖性能/配置项)
- 本次 6 类问题全部靠人工发现

## 范围
1. scripts/check-frontend-quality.sh:扫描 ycmn-ui/src 反模式(size:9999/createObjectURL无revoke/progress无节流)+ nginx 配置检查(add_header继承/gzip)
2. scripts/check-prod-perf.sh:生产验证(gzip/安全头/no-cache/耗时)
3. pre-delivery-check.sh 接入(本地扫描必跑)

## 不在范围
- eslint 全量引入(现有代码无配置,会产生大量既有告警;扫描脚本零依赖更对症)
- 浏览器 E2E 自动化(现有 smoke-test.sh API 冒烟保留)

## 约束
- 零新依赖;脚本 bash 实现;当前代码应全过(证明门禁有效)

## 初始风险
- 扫描规则误报:规则取保守子集,只拦确凿反模式

## 来源与事实
- 用户质疑"为什么小错误这么多",授权执行防复发措施
- 已确证:前端零 lint、smoke 仅 API 级、6 类问题均曾复发
- 门禁首跑抓到 config.vue:600 / incentive/index.vue:633 现存 size:9999(已修)
