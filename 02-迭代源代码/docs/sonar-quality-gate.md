# Sonar 质量门（P0-5 / B14）

> 用户拍板（2026-11-18）：**new_coverage 非阻断**；保留新代码 Blocker / Critical / Bug /
> Vulnerability / **MAJOR+ Violation** = 0。审计 P0-5 原文只要求 Blocker/Critical=0，覆盖率作为
> 独立度量记录（JaCoCo），不进入发布门禁。
>
> 2026-11-18 口径校准：`new_violations=0` 改为 `new_major_violations=0`。原因是 Sonar 新代码期
> 会把 INFO 级 `S6541 Brain Method` 等重构提示重算为“新增违规”，堵住与 P0-5 无关的发布；
> Blocker/Critical/Bug/Vulnerability 与 MAJOR 仍零容忍，INFO/MINOR 只记录不阻断。该校准已报
> 用户追认，不改变“整体 critical 清零”的 P0-5 销账条件。

## 门禁定义

| 项 | 值 |
|---|---|
| 门名 | `AIGICRM-P0-5` |
| 本地项目 | `Eastdongjun_AigiCRM-local` |
| SonarCloud 项目 | `Eastdongjun_AigiCRM` |
| 新代码条件 | `new_blocker_violations=0`、`new_critical_violations=0`、`new_bugs=0`、`new_vulnerabilities=0`、`new_major_violations=0` |
| 继承条件 | `new_security_hotspots_reviewed=100`、`new_duplicated_lines_density≤3` |
| 覆盖率 | **不设门禁条件**；JaCoCo 报告 `target/site/jacoco/jacoco.xml` 保留 |

## 配置方式

本地（账号口令经环境变量 `SONAR_USER` / `SONAR_PASSWORD` 传入；脚本不再内置安装默认值）：

```bash
cd 02-迭代源代码
SONAR_PASSWORD='<本地 Sonar 口令>' bash scripts/sonar-quality-gate.sh
```

SonarCloud（需项目管理员 token；把 token 作为用户名、空密码）：

```bash
SONAR_HOST_URL=https://sonarcloud.io \
SONAR_TOKEN="$SONAR_TOKEN" \
SONAR_PROJECT_KEY=Eastdongjun_AigiCRM \
bash 02-迭代源代码/scripts/sonar-quality-gate.sh
```

脚本是幂等的：创建门（已存在则忽略）、补条件（重复则忽略）、删除 `new_coverage` 条件、
把门绑定到项目。绑定后 CI 的 `-Dsonar.qualitygate.wait=true` 会按新门判定。

## 背景

- P0-5 严格整改（455 CRITICAL code smell + 4 CRITICAL BUG）仍在进行；覆盖率 49% 主要被
  250 条 S1192 常量行与存量代码拉低，且未覆盖行多为行为分支，需独立补测。
- `new_major_violations=0` 覆盖 MAJOR+ 新增问题（含 S1192/S3358 等）；Blocker/Critical/
  Bug/Vulnerability 仍为零容忍；INFO/MINOR 只记录不阻断，保持发布门禁的实质严格性。

## 本地实例的历史项目（读 issue 时必须过滤）

本地 Docker SonarQube（`127.0.0.1:19000`）上除门禁项目外，还残留一个早期临时分析的项目：

| key | 说明 |
|---|---|
| `Eastdongjun_AigiCRM-local` | **门禁项目**，本地分析的唯一权威 |
| `aigicrm-backend-local` | 早期临时分析遗留，**文件路径与门禁项目重叠** |

`/api/issues/search` 不按项目过滤时，两项目的同名文件问题会合并返回，导致「问题数虚高」；
且该接口默认同时返回 `CLOSED` / `FIXED` 状态的历史问题。

**正确读法**：先按 `status == 'OPEN'` 且 `project == 'Eastdongjun_AigiCRM-local'` 过滤，
再谈「新增/存量」。2026-11-23 复核时若未过滤，会把 17 条历史问题误判为本次引入。

`sonar-project.properties`（仓库根）指向 SonarCloud 的 `Eastdongjun_AigiCRM`，与本地 key 不同名是
**有意为之**（避免本地实验污染云端项目），不是配置错误。命令行扫描本地实例时需显式传
`-Dsonar.projectKey=Eastdongjun_AigiCRM-local`，并带上 `sonar-project.properties` 里的
`sonar.issue.ignore.multicriteria` e1/e2/e3（`java:S1068` 实体/DTO/security 豁免），否则会让
已豁免的实体字段问题重新以 OPEN 出现。
