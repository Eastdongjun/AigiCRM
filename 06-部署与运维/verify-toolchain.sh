#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SONAR_BIN="/Users/dongjun/东来/Agent/03-工具链/SonarQube/sonarqube-cli/bin/sonar"
MAVEN_BIN="/Users/dongjun/东来/Agent/02-运行环境/Maven/bin/mvn"
NODE_BIN="/Users/dongjun/东来/Agent/02-运行环境/Node.js/bin/node"
export JAVA_HOME="/Users/dongjun/东来/Agent/02-运行环境/Java/Contents/Home"
MAVEN_DIR="$(dirname "$MAVEN_BIN")"
export PATH="$MAVEN_DIR:$JAVA_HOME/bin:$PATH"
docker info >/dev/null
"$MAVEN_BIN" -version
"$NODE_BIN" --version
test -f "$PROJECT_DIR/02-迭代源代码/aigicrm-backend/pom.xml"
test -f "$PROJECT_DIR/02-迭代源代码/aigicrm-ui/package.json"
"$SONAR_BIN" auth status
echo "AIGICRM toolchain prerequisites: OK"
