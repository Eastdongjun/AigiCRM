# 自动确保 JAVA_HOME 指向真正的 JDK 21（pom.xml 要求）。
# 被 verify-and-commit.sh / auto-pipeline.sh source，不单独执行。

# 校验某个候选路径下的 java 是否确实是主版本 21（不信任任何工具的自述，实测为准）
_is_real_jdk21() {
  local cand="$1"
  [ -x "$cand/bin/java" ] || return 1
  local v
  v=$("$cand/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
  [ "$v" = "21" ]
}

ensure_java21() {
  # 1) 当前 PATH 上的 java 已经是 21
  if command -v java >/dev/null 2>&1; then
    CUR_VER=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
    if [ "$CUR_VER" = "21" ]; then return 0; fi
  fi

  # 2) macOS: 枚举所有已装 JVM，实测挑出真正的 21（不直接信 java_home -v 21 的返回，
  #    之前发现它在某些机器上会误配到 JDK 25）
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    while IFS= read -r cand; do
      [ -n "$cand" ] || continue
      if _is_real_jdk21 "$cand"; then
        export JAVA_HOME="$cand"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "✅ 找到已安装的 JDK 21: $JAVA_HOME"
        return 0
      fi
    done < <(/usr/libexec/java_home -V 2>&1 | grep -oE '/Library/Java/JavaVirtualMachines/[^ ]+/Contents/Home')
  fi

  # 3) 没有真正的 21：用 Homebrew 自动装
  if command -v brew >/dev/null 2>&1; then
    echo "未检测到真正的 JDK 21，正在用 Homebrew 自动安装 openjdk@21（首次可能需要几分钟）..."
    brew install openjdk@21 || { echo "❌ brew install openjdk@21 失败，请手动安装后重试"; return 1; }
    for cand in "$(brew --prefix openjdk@21 2>/dev/null)" /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21; do
      [ -n "$cand" ] || continue
      if _is_real_jdk21 "$cand"; then
        export JAVA_HOME="$cand"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "✅ 已安装并设置 JAVA_HOME: $JAVA_HOME"
        return 0
      fi
    done
    echo "❌ brew 装完后仍验证不到真正的 JDK 21，请手动检查: brew --prefix openjdk@21"
    return 1
  fi

  echo "❌ 未装 JDK 21，且本机没有 Homebrew，无法自动安装。"
  echo "   请手动装一次: 从 https://adoptium.net/temurin/releases/?version=21 下载安装，或先装 Homebrew (https://brew.sh) 再重跑本脚本。"
  return 1
}

# 运行 mvn，带多层 fallback（有些机器上 PATH 里的 mvn 是 jenv/sdkman 装的 shim，
# 会在内部自己重新计算 JAVA_HOME 把我们导出的值覆盖掉，导致即使 JAVA_HOME 是对的也报错）。
run_maven() {
  echo "── 诊断: JAVA_HOME=$JAVA_HOME"
  echo "── 诊断: mvn 解析为 $(command -v mvn 2>/dev/null)"
  "$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/── 诊断: /'
  for rc in "$HOME/.mavenrc" /etc/mavenrc; do
    if [ -f "$rc" ]; then
      echo "── 诊断: 发现 $rc，内容："
      sed 's/^/     /' "$rc"
    fi
  done

  # 尝试1: 直接跑（大多数情况下够用）
  if JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" mvn "$@"; then
    return 0
  fi
  echo "⚠️  直接调用 mvn 失败，尝试绕过可能存在的 mvn shim..."

  # 尝试2: 找 Homebrew 装的、未被 shim 包一层的真正 mvn
  local REAL_MVN=""
  if command -v brew >/dev/null 2>&1; then
    local MVN_PREFIX
    MVN_PREFIX=$(brew --prefix maven 2>/dev/null)
    if [ -n "$MVN_PREFIX" ] && [ -x "$MVN_PREFIX/bin/mvn" ]; then
      REAL_MVN="$MVN_PREFIX/bin/mvn"
    else
      echo "未检测到 Homebrew 装的 maven，正在自动安装..."
      brew install maven 2>&1 | tail -5
      MVN_PREFIX=$(brew --prefix maven 2>/dev/null)
      [ -n "$MVN_PREFIX" ] && [ -x "$MVN_PREFIX/bin/mvn" ] && REAL_MVN="$MVN_PREFIX/bin/mvn"
    fi
  fi
  [ -z "$REAL_MVN" ] && REAL_MVN="$(command -v mvn)"

  if [ -n "$REAL_MVN" ]; then
    echo "── 诊断: 改用 $REAL_MVN"
    if JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" "$REAL_MVN" "$@"; then
      return 0
    fi
  fi

  # 尝试3: mvn 启动脚本本身在开头无条件 source ~/.mavenrc 或 /etc/mavenrc，
  # 如果那里面重新 export 了一个坏的 JAVA_HOME，会在脚本内部把我们传入的值覆盖掉，
  # 不管从哪个 mvn 可执行文件调用、不管怎么传 JAVA_HOME 都没用。
  # 做一份去掉这两行 source 的临时副本来跑，不改动用户原文件。
  if [ -n "$REAL_MVN" ] && grep -qi 'mavenrc' "$REAL_MVN" 2>/dev/null; then
    echo "⚠️  mvn 启动脚本里发现会 source mavenrc，生成一份跳过它的临时副本再试..."
    local TMP_MVN
    TMP_MVN="/tmp/mvn-no-rc-$$"
    grep -vi 'mavenrc' "$REAL_MVN" > "$TMP_MVN"
    chmod +x "$TMP_MVN"
    if JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" "$TMP_MVN" "$@"; then
      rm -f "$TMP_MVN"
      return 0
    fi
    rm -f "$TMP_MVN"
  fi

  echo "❌ mvn 仍然失败。诊断信息（请把这几行结果连同上面 .mavenrc 内容一起发回来）:"
  type -a mvn 2>&1 | sed 's/^/   /'
  type -a java 2>&1 | sed 's/^/   /'
  echo "   MAVEN_OPTS=$MAVEN_OPTS"
  return 1
}
