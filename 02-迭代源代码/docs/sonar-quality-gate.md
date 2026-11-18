# Sonar 质量门（P0-5 / B14）

> 用户拍板（2026-11-18）：**new_coverage 非阻断**；保留新代码 Blocker / Critical / Bug /
> Vulnerability / Violation = 0。审计 P0-5 原文只要求 Blocker/Critical=0，覆盖率作为
> 独立度量记录（JaCoCo），不进入发布门禁。

## 门禁定义

| 项 | 值 |
|---|---|
| 门名 | `AIGICRM-P0-5` |
| 本地项目 | `Eastdongjun_AigiCRM-local` |
| SonarCloud 项目 | `Eastdongjun_AigiCRM` |
| 新代码条件 | `new_blocker_violations=0`、`new_critical_violations=0`、`new_bugs=0`、`new_vulnerabilities=0`、`new_violations=0` |
| 继承条件 | `new_security_hotspots_reviewed=100`、`new_duplicated_lines_density≤3` |
| 覆盖率 | **不设门禁条件**；JaCoCo 报告 `target/site/jacoco/jacoco.xml` 保留 |

## 配置方式

本地（默认 `admin:admin`）：

```bash
cd 02-迭代源代码
bash scripts/sonar-quality-gate.sh
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
- `new_violations=0` 已覆盖 S1192/S3358 等新增问题；Blocker/Critical/Bug/Vulnerability
  仍为零容忍，保持发布门禁的实质严格性。
