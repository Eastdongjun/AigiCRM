# A12 依赖供应链 + A14 前端组件覆盖（2026-11-18，静态）

## A12 依赖与 CVE

- Spring Boot **4.1.2**（parent）；mybatis-plus-spring-boot4-starter、flyway-core/mysql、jjwt（api/impl/jackson）、mysql-connector-j、aliyun-sdk-oss、micrometer-registry-prometheus；测试 h2。
- **CVE 扫描 = BLOCKED**：本地无 OWASP dependency-check/NVD 库，未做依赖漏洞扫描；Sonar 本地未开 vulnerability 规则集（仅 secrets）。
- 提示：Spring Boot 4.1.x 为较新线；需在 CI 加 dependency-check 或 SonarCloud 依赖分析（需用户授权 token）。密钥轮换状态未核（凭据在 `~/.aigicrm-deploy.env`，不在库）。

## A14 前端组件覆盖

| 指标 | 数值 |
|---|---|
| `.vue` 总数 | **111**（views 102 + components/其他 9） |
| `.spec.*` 总数 | **47**（views 20 + composables/utils/components 27） |
| **views 覆盖率** | **20/102 = 19.6%** |
| 无组件级 spec 的 views | **82** |

- 组件级 spec 集中在：文件（FolderPanel/file-table-grantee）、提醒（index/detail）、系统（PermCenter/PermScopeManager/user）、指标、仪表盘。
- 前端单测跑 340 用例 / 537 断言（0b 2026-11-18 复跑），主要覆盖 composables/utils（拆分后的 useCustomerList 等）与部分 views。
- **判定：A14 = 覆盖薄弱（P1）**——与 RTM gap「77 个 `.vue` 无组件 spec」一致（本次实测 82，RTM 10-31 口径 77）。UI 缺陷仍靠 API 测试+人工覆盖，属已知缺口，非新发现。
- 逐页实拍（A14 原判据）未做——需浏览器/真机，属 W3/W4 用户侧。

## 结论

- A12 = **BLOCKED**（无扫描工具/授权）。
- A14 = **覆盖薄弱已量化（19.6%），无回归**；实拍待 W3。
