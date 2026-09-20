#!/usr/bin/env python3
"""AIGICRM 按钮↔接口↔权限码 静态台账生成器(2026-09-20)

用途: 为「全系统逐按钮审计」建立静态事实源。
产出四张表并做四层 join:
  L0 sys_menu 权限字典(来自库)
  L1 后端端点  Controller -> (HTTP, path, @PreAuthorize, file, line)
  L2 前端 api 函数 api/*.js -> (module, func, HTTP, path, file, line)
  L3 前端按钮  *.vue -> (file, line, text, handler, guard, 调用的 api 函数)

只读: 不修改任何业务文件。
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "02-迭代源代码"
BACKEND = SRC / "aigicrm-backend/src/main/java"
FRONTEND = SRC / "aigicrm-ui/src"
OUT = Path(__file__).resolve().parent


# ---------------------------------------------------------------- L1 backend
def parse_backend():
    endpoints = []
    for path in BACKEND.rglob("*Controller.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        class_m = re.search(r'@RequestMapping\s*\(\s*"([^"]+)"', text)
        base = class_m.group(1).rstrip("/") if class_m else ""
        # 逐行扫描: 记录每个 HTTP 映射及其前后最近的 @PreAuthorize
        for i, line in enumerate(lines):
            mm = re.search(
                r'@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?',
                line)
            if not mm:
                continue
            verb = mm.group(1).upper()
            sub = mm.group(2) or ""
            full = base + ("/" + sub.lstrip("/") if sub else "")
            # @PreAuthorize 可能在映射注解前 1-4 行, 或后 1-4 行
            perm, perm_line = None, None
            for j in list(range(max(0, i - 5), i)) + list(range(i + 1, min(len(lines), i + 5))):
                pm = re.search(r'@PreAuthorize\("([^"]+)"\)', lines[j])
                if pm:
                    perm, perm_line = pm.group(1), j + 1
                    break
            endpoints.append({
                "http": verb,
                "path": full,
                "permission": perm,
                "file": str(path.relative_to(ROOT)),
                "line": i + 1,
                "perm_line": perm_line,
                "method": (re.search(r'public\s+[\w<>,\[\]\s\.]+\s+(\w+)\s*\(', lines[i + 1])
                           or re.search(r'public\s+[\w<>,\[\]\s\.]+\s+(\w+)\s*\(', line)),
            })
            if endpoints[-1]["method"]:
                endpoints[-1]["method"] = endpoints[-1]["method"].group(1)
    return endpoints


# ---------------------------------------------------------------- L2 frontend api
def parse_frontend_api():
    funcs = []
    for path in sorted((FRONTEND / "api").glob("*.js")):
        if path.name.endswith(".spec.js"):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        for i, line in enumerate(lines):
            # 两种风格: export function xxx(args) {...}   |   export const xxx = (args) => ...
            fm = (re.search(r'export\s+function\s+(\w+)\s*\(', line)
                  or re.search(r'export\s+const\s+(\w+)\s*=', line))
            if not fm:
                continue
            name = fm.group(1)
            # 在函数定义后 12 行内找 request.<verb>(
            chunk = "\n".join(lines[i:i + 12])
            cm = re.search(r'request\.(get|post|put|delete|patch)\(\s*[`\'"]([^`\'"]+)[`\'"]', chunk)
            if not cm:
                continue
            raw = cm.group(2)
            # 归一化模板占位 ${...} -> {param}
            norm = re.sub(r'\$\{[^}]+\}', '{param}', raw)
            funcs.append({
                "module": path.stem,
                "func": name,
                "http": cm.group(1).upper(),
                "path": norm,
                "raw_path": raw,
                "file": str(path.relative_to(ROOT)),
                "line": i + 1,
            })
    return funcs


# ---------------------------------------------------------------- L0 menu dict
def parse_menu_dict():
    sql = ("select id, parent_id, menu_name, permission, menu_type, visible "
           "from sys_menu order by id")
    cmd = ["docker", "exec", "aigicrm-local-mysql", "sh", "-c",
           'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "%s" aigicrm 2>/dev/null' % sql]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except Exception as exc:  # noqa: BLE001
        print("WARN menu dict unavailable: %s" % exc, file=sys.stderr)
        return []
    rows = []
    for line in out.stdout.splitlines():
        parts = line.split("\t")
        if len(parts) < 6:
            continue
        rows.append({
            "id": parts[0], "parent_id": parts[1], "menu_name": parts[2],
            "permission": parts[3], "menu_type": parts[4], "visible": parts[5],
        })
    return rows


# ---------------------------------------------------------------- L3 buttons
def scan_tags(text, tag):
    """手工扫描 <tag ...> 标签, 正确处理引号内的 '>'。返回 (attrs, start, inner_start_or_None, is_self_closing)。"""
    out = []
    needle = "<" + tag
    i = 0
    while True:
        s = text.find(needle, i)
        if s == -1:
            return out
        # 必须紧跟空白或 '>' 或 '/', 排除 <el-button-group
        after = text[s + len(needle):s + len(needle) + 1]
        if after not in (" ", "\n", "\t", "\r", ">", "/"):
            i = s + 1
            continue
        j = s + len(needle)
        quote = None
        while j < len(text):
            ch = text[j]
            if quote:
                if ch == quote:
                    quote = None
            elif ch in "\"'":
                quote = ch
            elif ch == ">":
                break
            j += 1
        attrs = text[s + len(needle):j]
        self_closing = attrs.rstrip().endswith("/")
        out.append((attrs, s, j + 1, self_closing))
        i = j + 1


def strip_tags(s):
    return re.sub(r'<[^>]+>', '', s).strip()


def find_tag_end(text, start):
    """从 <el-button ...> 之后找匹配的 </el-button>, 处理嵌套。"""
    depth = 1
    i = start
    while i < len(text):
        nxt_open = text.find("<el-button", i)
        nxt_close = text.find("</el-button>", i)
        if nxt_close == -1:
            return len(text)
        if nxt_open != -1 and nxt_open < nxt_close:
            depth += 1
            i = nxt_open + 1
        else:
            depth -= 1
            if depth == 0:
                return nxt_close
            i = nxt_close + 1
    return len(text)


def parse_buttons():
    """返回所有 .vue 里的 el-button 元素。"""
    buttons = []
    for path in sorted(FRONTEND.rglob("*.vue")):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = str(path.relative_to(ROOT))
        for attrs, start, content_start, self_closing in scan_tags(text, "el-button"):
            if self_closing:
                inner = ""
                end = content_start
            else:
                end = find_tag_end(text, content_start)
                inner = text[content_start:end]
            line = text[:start].count("\n") + 1
            click = None
            cm = re.search(r'(?:@click|:on-click|v-on:click)\s*=\s*"([^"]*)"', attrs)
            if cm:
                click = cm.group(1)
            guard = None
            gm = re.search(r"hasPermission\(\s*'([^']+)'\s*\)", attrs)
            if gm:
                guard = gm.group(1)
            gm2 = re.search(r'v-permission\s*=\s*"\[?\'?([^\]\'"]+)', attrs)
            if gm2 and not guard:
                guard = gm2.group(1)
            label = strip_tags(inner)
            label = re.sub(r'\s+', ' ', label)[:40]
            buttons.append({
                "file": rel,
                "line": line,
                "attrs": re.sub(r'\s+', ' ', attrs).strip()[:300],
                "label": label,
                "click": click,
                "guard": guard,
                "self_closing": self_closing,
            })
    return buttons


# ---------------------------------------------------------------- join
def parse_vue_imports(text):
    """local alias -> (module, func) for `import { a, b as c } from '@/api/x'`"""
    mapping = {}
    for m in re.finditer(r"import\s*\{([^}]+)\}\s*from\s*['\"]@/api/(\w+)['\"]", text):
        names = m.group(1)
        module = m.group(2)
        for part in names.split(","):
            part = part.strip()
            if not part:
                continue
            if " as " in part:
                orig, alias = [x.strip() for x in part.split(" as ")]
            else:
                orig = alias = part
            mapping[alias] = (module, orig)
    return mapping


def parse_handler_bodies(text):
    """粗粒度提取 `const name = ... => { ... }` / `function name(...) {...}` 的函数体文本。"""
    bodies = {}
    # const name = (args) => { ... }   或  const name = async (...) => {...}
    for m in re.finditer(r'(?:const|let)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{', text):
        name = m.group(1)
        start = m.end()
        # 花括号配平
        depth, i = 1, start
        while i < len(text) and depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        bodies[name] = text[start:i]
    # function name(...) { ... }
    for m in re.finditer(r'function\s+(\w+)\s*\([^)]*\)\s*\{', text):
        name = m.group(1)
        start = m.end()
        depth, i = 1, start
        while i < len(text) and depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        bodies[name] = text[start:i]
    # 表达式体箭头函数: const name = (...) => EXPR (无花括号, 到行尾)
    for m in re.finditer(r'(?:const|let)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*(?!\{)([^\n]+)', text):
        bodies.setdefault(m.group(1), m.group(2))
    return bodies


def resolve_handlers(buttons):
    """把按钮的 @click 处理器解析到 api 函数调用。"""
    by_file = {}
    for b in buttons:
        by_file.setdefault(b["file"], []).append(b)
    for rel, items in by_file.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8", errors="replace")
        imports = parse_vue_imports(text)
        bodies = parse_handler_bodies(text)
        for b in items:
            calls = []
            handler = b["click"] or ""
            # 处理器名(可能带参数/链式)
            names = re.findall(r'(\w+)\s*\(', handler)
            if not names:
                names = [x.strip() for x in handler.split(";") if x.strip().isidentifier()]
            body = ""
            for n in names:
                if n in bodies:
                    body += bodies[n] + "\n"
            # 直接内联调用
            target = body or handler
            for alias, (module, orig) in imports.items():
                if re.search(r'\b' + re.escape(alias) + r'\s*\(', target):
                    calls.append({"module": module, "func": orig, "alias": alias})
            b["api_calls"] = calls
    return buttons


def main():
    endpoints = parse_backend()
    apis = parse_frontend_api()
    menus = parse_menu_dict()
    buttons = resolve_handlers(parse_buttons())

    data = {
        "generated_from": str(ROOT),
        "endpoints": endpoints,
        "apis": apis,
        "menus": menus,
        "buttons": buttons,
    }
    (OUT / "static_inventory.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

    print("endpoints      = %d" % len(endpoints))
    print("  no @PreAuthorize = %d" % sum(1 for e in endpoints if not e["permission"]))
    print("apis           = %d" % len(apis))
    print("menus          = %d" % len(menus))
    print("  with permission= %d" % sum(1 for m in menus if m["permission"]))
    print("buttons        = %d" % len(buttons))
    print("  no @click      = %d" % sum(1 for b in buttons if not b["click"]))
    print("  with guard     = %d" % sum(1 for b in buttons if b["guard"]))
    print("  with api_calls = %d" % sum(1 for b in buttons if b.get("api_calls")))
    print("  no api_calls   = %d" % sum(1 for b in buttons if not b.get("api_calls")))
    print("vue files with buttons = %d" % len({b["file"] for b in buttons}))


if __name__ == "__main__":
    main()
