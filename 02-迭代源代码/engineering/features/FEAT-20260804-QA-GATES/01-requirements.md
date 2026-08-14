# 需求规格

## 目标用户与场景
- 交付/部署前自动检查,拦截性能与配置反模式

## 功能需求
| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | scripts/check-frontend-quality.sh:扫描 ycmn-ui/src(size:9999/9999 全量、URL.createObjectURL 无配套 revoke、onUploadProgress 无节流)+ nginx-ssl.conf(add_header 无安全头的 location、缺 gzip) | 防复发 | Must |
| REQ-002 | scripts/check-prod-perf.sh <base_url>:验证 gzip 生效、index.html no-cache+4安全头、首页/登录耗时 | 防复发 | Must |
| REQ-003 | pre-delivery-check.sh 调用 REQ-001(本地);生产检查按需 | 防复发 | Should |

## 验收标准
| 验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given 当前已修复代码;When 运行 check-frontend-quality.sh;Then 退出 0 且 0 违规 |
| AC-002 | REQ-001 | Given 故意注入 size:9999;When 运行脚本;Then 非 0 退出并指出位置 |
| AC-003 | REQ-002 | Given 生产 URL;When 运行 check-prod-perf.sh;Then gzip/安全头/no-cache 全部 PASS |

## 需求确认
- [x] 用户已确认:是(用户指示"执行这几项防复发措施")

## 事实
- ycmn-ui 纯 JS(Vue3),无 eslint;smoke-test.sh 仅 API 冒烟;pre-delivery-check.sh 存在
- 6 类问题(重复上传/blob泄漏/进度无节流/全量加载/gzip未开/安全头丢失)均曾发生

## 假设
- 反模式扫描(零依赖)比 eslint 更对症且不阻塞现有代码

## 阻断问题
- 无

## 业务规则与权限
- 扫描脚本本地运行,无权限变更;生产检查需外网可达

## 异常与边界
- API 小响应(<1k)不触发 gzip → 用静态 JS 检测
- 脚本计数须在主 shell(子 shell 不累加)

## 非功能需求
- 零新依赖;运行 <5s;退出码语义明确

## 追踪关系
- REQ-001→AC-001/002;REQ-002→AC-003;REQ-003→AC-001(集成)
