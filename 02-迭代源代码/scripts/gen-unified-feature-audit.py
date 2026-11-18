#!/usr/bin/env python3
"""Generate a local PC/mobile/backend permission and feature audit.

This script is intentionally read-only. It scans source files and existing
documentation, then writes a Markdown report that helps compare:

- PC routes and route permissions.
- PC API wrappers.
- Mini Program pages and API wrappers.
- Backend controller endpoints and @PreAuthorize permissions.
- Existing permission dictionary entries.
- Local Flyway sys_menu permission inserts.

It does not connect to production and does not read secrets.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path

# P0-5 batch C: duplicated string literals (Sonar python:S1192)
NONE_ROW = '- None'
MD_TABLE_SEPARATOR = '| --- | --- | --- | --- |'


@dataclass(frozen=True)
class Route:
    path: str
    title: str
    permission: str
    source: str


@dataclass(frozen=True)
class ApiFunction:
    name: str
    method: str
    path: str
    source: str


@dataclass(frozen=True)
class Endpoint:
    method: str
    path: str
    permission: str
    source: str


@dataclass(frozen=True)
class UiAction:
    label: str
    handler: str
    permission_hint: str
    source: str


@dataclass(frozen=True)
class PermissionReference:
    permission: str
    context: str
    source: str


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def extract_pc_routes(router: Path, root: Path) -> list[Route]:
    text = read(router)
    routes: list[Route] = []
    blocks = re.findall(r"\{\s*path:\s*'([^']+)'(.*?meta:\s*\{([^}]+)\}.*?)\n\s*\}", text, re.S)
    for path, block, meta in blocks:
        title = find_string(meta, "title")
        permission = find_string(meta, "permission")
        routes.append(Route(path=path, title=title, permission=permission, source=rel(router, root)))
    return routes


def extract_api_functions(api_dir: Path, root: Path) -> list[ApiFunction]:
    items: list[ApiFunction] = []
    fn_re = re.compile(
        r"export\s+function\s+(\w+)\s*\([^)]*\)\s*\{.*?request\.(get|post|put|delete)\((`[^`]+`|'[^']+'|\"[^\"]+\")",
        re.S,
    )
    for path in sorted(api_dir.glob("*.js")):
        text = read(path)
        for name, method, url in fn_re.findall(text):
            items.append(ApiFunction(name, method.upper(), strip_quote(url), rel(path, root)))
    return items


def extract_mobile_pages(app_json: Path, root: Path) -> list[str]:
    data = json.loads(read(app_json))
    return [str(p) for p in data.get("pages", [])]


def extract_mobile_apis(api_dir: Path, root: Path) -> list[ApiFunction]:
    items: list[ApiFunction] = []
    fn_re = re.compile(
        r"function\s+(\w+)\s*\([^)]*\)\s*\{.*?request\(\s*\{\s*url:\s*([^,\n}]+)(?:,\s*method:\s*'([^']+)')?",
        re.S,
    )
    for path in sorted(api_dir.glob("*.js")):
        text = read(path)
        for name, url, method in fn_re.findall(text):
            items.append(ApiFunction(name, (method or "GET").upper(), normalize_js_url(url), rel(path, root)))
    return items


CONTROLLER_CLASS_MAPPING_RE = re.compile(r"@RequestMapping\(\"([^\"]+)\"\)")
CONTROLLER_MAPPING_BARE_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping")
CONTROLLER_MAPPING_VALUE_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\(value\s*=\s*\"([^\"]*)\"\s*\)")
CONTROLLER_MAPPING_STRING_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\(\"([^\"]*)\"\s*\)")
CONTROLLER_AUTH_RE = re.compile(r"@PreAuthorize\(\"([^\"]+)\"\)")


def _controller_mapping(annotations: str):
    """返回 (method, suffix)；无映射注解返回 (None, "")。三条正则均无可选组，避免 S5852。"""
    mapping = CONTROLLER_MAPPING_VALUE_RE.search(annotations)
    if mapping:
        return mapping.group(1).upper(), mapping.group(2)
    mapping = CONTROLLER_MAPPING_STRING_RE.search(annotations)
    if mapping:
        return mapping.group(1).upper(), mapping.group(2)
    bare = CONTROLLER_MAPPING_BARE_RE.search(annotations)
    if not bare:
        return None, ""
    return bare.group(1).upper(), ""


def _endpoint_from_annotations(annotation_lines: list[str], class_mapping: str, path: Path, root: Path):
    annotations = "\n".join(annotation_lines)
    method, suffix = _controller_mapping(annotations)
    if method is None:
        return None
    auth = CONTROLLER_AUTH_RE.search(annotations)
    return Endpoint(method, join_paths(class_mapping, suffix), auth.group(1) if auth else "", rel(path, root))


def _resets_annotations(stripped: str) -> bool:
    return bool(stripped and not stripped.startswith("//") and not stripped.startswith("*") and not stripped.startswith("/*"))


def _looks_like_signature(stripped: str) -> bool:
    """public 后跟空白且含 '('（无正则，避免 S5852 启发式）。"""
    return stripped.startswith("public") and len(stripped) > 6 and stripped[6].isspace() and "(" in stripped


def _controller_endpoints(path: Path, root: Path) -> list[Endpoint]:
    endpoints: list[Endpoint] = []
    text = read(path)
    class_mapping = first(CONTROLLER_CLASS_MAPPING_RE.findall(text), "")
    annotation_lines: list[str] = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("@"):
            annotation_lines.append(stripped)
        elif _looks_like_signature(stripped):
            endpoint = _endpoint_from_annotations(annotation_lines, class_mapping, path, root)
            if endpoint is not None:
                endpoints.append(endpoint)
            annotation_lines = []
        elif _resets_annotations(stripped):
            annotation_lines = []
    return endpoints


def extract_backend_endpoints(java_root: Path, root: Path) -> list[Endpoint]:
    endpoints: list[Endpoint] = []
    for path in sorted(java_root.rglob("*Controller.java")):
        endpoints.extend(_controller_endpoints(path, root))
    return endpoints


def extract_vue_actions(views_dir: Path, root: Path) -> list[UiAction]:
    actions: list[UiAction] = []
    button_re = re.compile(r"<el-button([^>]*)>(.*?)</el-button>", re.S)
    click_re = re.compile(r"@click(?:\.stop)?=\"([^\"]+)\"")
    perm_re = re.compile(r"(?:v-if|v-show)=\"([^\"]*hasPermission[^\"]*|can[A-Z]\w*[^\"]*)\"")
    tag_re = re.compile(r"<[^>]+>")
    for path in sorted(views_dir.rglob("*.vue")):
        text = read(path)
        for match in button_re.finditer(text):
            attrs, body = match.groups()
            handler = first(click_re.findall(attrs), "")
            permission = first(perm_re.findall(attrs), "")
            label = clean_ui_label(tag_re.sub("", body))
            if label or handler:
                line = text.count("\n", 0, match.start()) + 1
                actions.append(UiAction(label or "(icon/button)", handler, permission, f"{rel(path, root)}:{line}"))
    return actions


def extract_wxml_actions(mini_dir: Path, root: Path) -> list[UiAction]:
    actions: list[UiAction] = []
    tap_node_re = re.compile(r"<(?P<tag>button|view|text)(?P<attrs>[^>]*)>", re.S)
    bind_re = re.compile(r"(?:bindtap|catchtap)=\"([^\"]+)\"")
    wxif_re = re.compile(r"wx:if=\"([^\"]+)\"")
    tag_re = re.compile(r"<[^>]+>")
    for path in sorted(mini_dir.rglob("*.wxml")):
        text = read(path)
        for match in tap_node_re.finditer(text):
            tag = match.group("tag")
            attrs = match.group("attrs")
            handler = first(bind_re.findall(attrs), "")
            if not handler:
                continue
            permission = first(wxif_re.findall(attrs), "")
            body = body_for_wxml_tap(text, match.end(), tag)
            label = clean_ui_label(tag_re.sub("", body))
            if label or handler:
                line = text.count("\n", 0, match.start()) + 1
                actions.append(UiAction(label or "(button)", handler, permission, f"{rel(path, root)}:{line}"))
    return actions


def body_for_wxml_tap(text: str, start: int, tag: str) -> str:
    token_re = re.compile(rf"</?{re.escape(tag)}(?:\s[^>]*)?>", re.S)
    depth = 1
    for match in token_re.finditer(text, start):
        token = match.group(0)
        if token.startswith("</"):
            depth -= 1
            if depth == 0:
                return text[start:match.start()]
        elif not token.endswith("/>"):
            depth += 1
    line_end = text.find("\n", start)
    if line_end >= 0:
        return text[start:line_end]
    return ""


def clean_ui_label(value: str) -> str:
    without_templates = re.sub(r"\{\{[^}]+\}\}", "", value)
    return re.sub(r"\s+", " ", without_templates).strip()


def extract_frontend_permission_tokens(source_dirs: list[Path]) -> set[str]:
    token_re = re.compile(r"(?:hasPermission|permission)\s*\(?\s*['\"]([a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+)['\"]")
    tokens: set[str] = set()
    for source_dir in source_dirs:
        if not source_dir.exists():
            continue
        for path in sorted(source_dir.rglob("*.vue")) + sorted(source_dir.rglob("*.js")):
            if path.name.endswith((".spec.js", ".test.js")) or "__tests__" in path.parts:
                continue
            for token in token_re.findall(read(path)):
                tokens.add(token)
    return tokens


def extract_permission_dict(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for line in read(path).splitlines():
        m = re.match(r"\|\s*`([^`]+)`\s*\|\s*([^|]+)\|", line)
        if m:
            label = m.group(2).strip()
            if label:
                out.setdefault(m.group(1), label)
    return out


def _permission_from_row(row: dict[str, str], path_name: str, permission_re: re.Pattern[str]):
    permission = row.get("permission", "")
    if not permission_re.match(permission):
        return None
    name = row.get("menu_name", "") or permission
    return permission, f"{name} ({path_name})"


def _permissions_from_quotes(statement: str, path_name: str, permission_re: re.Pattern[str], quoted_re: re.Pattern[str]):
    found = []
    name = ""
    for value in quoted_re.findall(statement):
        if not permission_re.match(value):
            name = value or name
            continue
        found.append((value, f"{name or value} ({path_name})"))
    return found


def extract_migration_permissions(migration_dir: Path) -> dict[str, str]:
    if not migration_dir.exists():
        return {}
    out: dict[str, str] = {}
    insert_re = re.compile(
        r"INSERT\s+INTO\s+`?sys_menu`?\s*\((?P<columns>.*?)\)\s*(?P<body>.*?);",
        re.S | re.I,
    )
    # 【法布施一期 2026-11-15 修复】[^']+ → [^']*: 值序列含连续空串 ''(按钮行 path/component 约定)
    # 时, 旧正则把空串两侧引号错位配对, 其后的权限码字符串被吞进跨串大匹配——码提取丢失
    # (manifest 契约报"码不在迁移"假红; 白名单 incentive:read-ranking:view 的"形态识别待增强"
    # 注记即同一缺陷的历史痕迹)。空串本身被 permission_re 拒绝, 对 name 兜底无影响。
    quoted_re = re.compile(r"'([^']*)'")
    permission_re = re.compile(r"^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+$")
    for path in sorted(migration_dir.glob("V*.sql")):
        text = read(path)
        for match in insert_re.finditer(text):
            columns = [normalize_sql_identifier(col) for col in split_top_level_commas(match.group("columns"))]
            row = extract_sys_menu_insert_row(columns, match.group("body"))
            found = _permission_from_row(row, path.name, permission_re) if row else None
            if found is not None:
                out.setdefault(found[0], found[1])
                continue
            for perm, label in _permissions_from_quotes(match.group(0), path.name, permission_re, quoted_re):
                out.setdefault(perm, label)
    return out


def normalize_sql_identifier(value: str) -> str:
    return value.strip().strip("`").lower()


def strip_sql_string(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == "'" and value[-1] == "'":
        return value[1:-1].replace("''", "'")
    return value


def split_top_level_commas(value: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    in_string = False
    i = 0
    while i < len(value):
        ch = value[i]
        if ch == "'":
            if in_string and i + 1 < len(value) and value[i + 1] == "'":
                i += 1  # 跳过 SQL 转义引号对（循环末尾再 +1）
            else:
                in_string = not in_string
        elif not in_string and ch == "(":
            depth += 1
        elif not in_string and ch == ")" and depth > 0:
            depth -= 1
        elif not in_string and ch == "," and depth == 0:
            parts.append(value[start:i].strip())
            start = i + 1
        i += 1
    tail = value[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def extract_sys_menu_insert_row(columns: list[str], body: str) -> dict[str, str]:
    if not columns:
        return {}
    select_match = re.search(r"\bSELECT\b(?P<select>.*?)\bFROM\s+DUAL\b", body, re.S | re.I)
    if not select_match:
        return {}
    values = split_top_level_commas(select_match.group("select"))
    if len(values) < len(columns):
        return {}
    row = {
        column: strip_sql_string(values[index])
        for index, column in enumerate(columns)
        if column in {"menu_name", "permission"}
    }
    return {key: value for key, value in row.items() if value}


def matched_permissions(line: str, permissions: list[str]) -> list[str]:
    return [
        permission for permission in permissions
        if quote_token(permission) in line or f'"{permission}"' in line
    ]


def extract_backend_permission_references(java_root: Path, root: Path, permissions: list[str]) -> dict[str, list[PermissionReference]]:
    refs: dict[str, list[PermissionReference]] = {permission: [] for permission in permissions}
    if not java_root.exists() or not permissions:
        return refs

    for path in sorted(java_root.rglob("*.java")):
        text = read(path)
        for line_no, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip()
            if not stripped or stripped.startswith(("//", "*")) or "@PreAuthorize" in stripped:
                continue
            for permission in matched_permissions(stripped, permissions):
                refs[permission].append(
                    PermissionReference(
                        permission=permission,
                        context=compact_line(stripped),
                        source=f"{rel(path, root)}:{line_no}",
                    )
                )
    return refs


def find_string(text: str, key: str) -> str:
    m = re.search(key + r":\s*'([^']+)'", text)
    return m.group(1) if m else ""


def strip_quote(value: str) -> str:
    return value.strip().strip("`'\"")


def normalize_js_url(expr: str) -> str:
    expr = expr.strip()
    if expr.startswith("'") or expr.startswith('"') or expr.startswith("`"):
        return strip_quote(expr)
    return expr


def join_paths(base: str, suffix: str) -> str:
    return ("/" + "/".join([base.strip("/"), suffix.strip("/")]).strip("/")).replace("//", "/")


def rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def first(values: list[str], default: str) -> str:
    return values[0] if values else default


def permission_tokens(expression: str) -> set[str]:
    return set(re.findall(r"'([^']+)'", expression or ""))


def ui_permission_tokens(actions: list[UiAction]) -> set[str]:
    tokens: set[str] = set()
    for action in actions:
        for token in permission_tokens(action.permission_hint):
            if re.fullmatch(r"[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+", token):
                tokens.add(token)
    return tokens


def quote_token(value: str) -> str:
    return f"'{value}'"


def compact_line(value: str, limit: int = 96) -> str:
    collapsed = re.sub(r"\s+", " ", value).replace("|", "\\|")
    return collapsed if len(collapsed) <= limit else collapsed[: limit - 1] + "..."


def permission_bullet(permission: str, labels: dict[str, str]) -> str:
    label = labels.get(permission, "").strip()
    return f"- `{permission}`: {label}" if label else f"- `{permission}`"


def is_runtime_dictionary_permission(permission: str, label: str) -> bool:
    """Return false for documented compatibility/deprecated dictionary rows."""
    text = f"{permission} {label}"
    inactive_markers = [
        "兼容历史项",
        "不作为独立运行时授权",
        "不作为运行时授权",
        "已废弃",
        "历史残留",
    ]
    return not any(marker in text for marker in inactive_markers)


def normalize_api_path(path: str, prefix: str) -> str:
    normalized = re.sub(r"\$\{[^}]+}", "{id}", path)
    normalized = re.sub(r"'\s*\+\s*\w+\s*(?:\+\s*')?", "{id}", normalized)
    if prefix and not normalized.startswith(prefix + "/"):
        normalized = prefix + normalized
    return normalized


def spring_path_regex(path: str) -> re.Pattern[str]:
    escaped = re.escape(path)
    escaped = re.sub(r"\\\{[^/]+?\\\}", "[^/]+", escaped)
    escaped = escaped.replace(r"\*\*", ".*")
    return re.compile("^" + escaped + "$")


def unmatched_apis(apis: list[ApiFunction], endpoints: list[Endpoint], prefix: str) -> list[ApiFunction]:
    compiled = [(endpoint.method, spring_path_regex(endpoint.path)) for endpoint in endpoints]
    unmatched: list[ApiFunction] = []
    for api in apis:
        path = normalize_api_path(api.path, prefix)
        if not any(api.method == method and pattern.match(path) for method, pattern in compiled):
            unmatched.append(api)
    return unmatched


def render(args, routes, pc_apis, mobile_pages, mobile_apis, endpoints, perm_dict, migration_perms, code_permission_refs, frontend_permission_tokens, pc_actions, mobile_actions) -> str:
    backend_perms = sorted({p for e in endpoints for p in permission_tokens(e.permission)})
    route_perms = sorted({r.permission for r in routes if r.permission})
    dict_perms = sorted(perm_dict)
    runtime_dict_perms = sorted(
        permission for permission, label in perm_dict.items()
        if is_runtime_dictionary_permission(permission, label)
    )
    compatibility_dict_perms = sorted(set(dict_perms) - set(runtime_dict_perms))
    local_permission_sources = {**migration_perms, **perm_dict}
    missing_in_dict = sorted(set(backend_perms) - set(local_permission_sources))
    dict_not_in_backend = sorted(set(runtime_dict_perms) - set(backend_perms))
    dict_with_code_refs = sorted(
        permission for permission in dict_not_in_backend
        if code_permission_refs.get(permission)
    )
    dict_not_seen_anywhere = sorted(set(dict_not_in_backend) - set(dict_with_code_refs))
    ui_perms = sorted(ui_permission_tokens(pc_actions + mobile_actions))
    ui_missing_in_sources = sorted(set(ui_perms) - set(backend_perms) - set(perm_dict) - set(migration_perms))
    frontend_missing_in_sources = sorted(frontend_permission_tokens - set(backend_perms) - set(perm_dict) - set(migration_perms))
    unmatched_pc_apis = unmatched_apis(pc_apis, endpoints, prefix="/api")
    unmatched_mobile_apis = unmatched_apis(mobile_apis, endpoints, prefix="")

    lines = [
        "# AIGICRM Unified Feature Audit",
        "",
        f"Generated from local source: `{args.root}`",
        "",
        "## Summary",
        "",
        f"- PC routes: {len(routes)}",
        f"- PC API functions: {len(pc_apis)}",
        f"- Mini Program pages: {len(mobile_pages)}",
        f"- Mini Program API functions: {len(mobile_apis)}",
        f"- Backend endpoints: {len(endpoints)}",
        f"- PC UI actions: {len(pc_actions)}",
        f"- Mini Program UI actions: {len(mobile_actions)}",
        f"- Backend permission tokens: {len(backend_perms)}",
        f"- Permission dictionary tokens: {len(dict_perms)}",
        f"- Runtime permission dictionary tokens: {len(runtime_dict_perms)}",
        f"- Compatibility/deprecated dictionary tokens: {len(compatibility_dict_perms)}",
        f"- Local migration permission tokens: {len(migration_perms)}",
        f"- Backend permissions missing in dictionary or local migrations: {len(missing_in_dict)}",
        f"- Runtime dictionary permissions not directly seen in backend annotations: {len(dict_not_in_backend)}",
        f"- Runtime dictionary permissions referenced outside backend annotations: {len(dict_with_code_refs)}",
        f"- Runtime dictionary permissions not seen in backend annotations or code references: {len(dict_not_seen_anywhere)}",
        f"- UI permission tokens missing in backend, dictionary, and local migrations: {len(ui_missing_in_sources)}",
        f"- Frontend code permission tokens: {len(frontend_permission_tokens)}",
        f"- Frontend code permission tokens missing in known sources: {len(frontend_missing_in_sources)}",
        f"- PC API wrappers without backend endpoint match: {len(unmatched_pc_apis)}",
        f"- Mini Program API wrappers without backend endpoint match: {len(unmatched_mobile_apis)}",
        "",
        "## PC Routes",
        "",
        "| Route | Title | Permission | Source |",
        MD_TABLE_SEPARATOR,
    ]
    for r in routes:
        lines.append(f"| `{r.path}` | {r.title} | `{r.permission}` | `{r.source}` |")

    lines += ["", "## Mini Program Pages", "", "| Page |", "| --- |"]
    for page in mobile_pages:
        lines.append(f"| `{page}` |")

    lines += ["", "## PC UI Actions", "", "| Label | Handler | Permission Hint | Source |", MD_TABLE_SEPARATOR]
    for action in pc_actions:
        lines.append(f"| {action.label} | `{action.handler}` | `{action.permission_hint}` | `{action.source}` |")

    lines += ["", "## Mini Program UI Actions", "", "| Label | Handler | Condition Hint | Source |", MD_TABLE_SEPARATOR]
    for action in mobile_actions:
        lines.append(f"| {action.label} | `{action.handler}` | `{action.permission_hint}` | `{action.source}` |")

    lines += ["", "## PC API Functions", "", "| Function | Method | Path | Source |", MD_TABLE_SEPARATOR]
    for api in pc_apis:
        lines.append(f"| `{api.name}` | `{api.method}` | `{api.path}` | `{api.source}` |")

    lines += ["", "## Mini Program API Functions", "", "| Function | Method | Path | Source |", MD_TABLE_SEPARATOR]
    for api in mobile_apis:
        lines.append(f"| `{api.name}` | `{api.method}` | `{api.path}` | `{api.source}` |")

    lines += ["", "## Backend Endpoints", "", "| Method | Path | Permission Expression | Source |", MD_TABLE_SEPARATOR]
    for e in endpoints:
        permission = e.permission.replace("|", "\\|") if e.permission else ""
        lines.append(f"| `{e.method}` | `{e.path}` | `{permission}` | `{e.source}` |")

    lines += ["", "## Local Migration Permission Sources", "", "| Permission | Source |", "| --- | --- |"]
    if migration_perms:
        for permission, source in sorted(migration_perms.items()):
            lines.append(f"| `{permission}` | {source} |")
    else:
        lines.append("| None |  |")

    lines += _render_permission_drift({
        'missing_in_dict': missing_in_dict,
        'frontend_missing_in_sources': frontend_missing_in_sources,
        'dict_not_in_backend': dict_not_in_backend,
        'dict_with_code_refs': dict_with_code_refs,
        'dict_not_seen_anywhere': dict_not_seen_anywhere,
        'compatibility_dict_perms': compatibility_dict_perms,
        'ui_missing_in_sources': ui_missing_in_sources,
    }, perm_dict, code_permission_refs)
    lines += _render_api_drift(unmatched_pc_apis, unmatched_mobile_apis)
    lines += _render_notes()
    return "\n".join(lines) + "\n"


def _render_permission_drift(drift, perm_dict, code_permission_refs):
    lines = ["", "## Permission Drift Candidates", ""]
    lines += ["### Backend Permissions Missing In Dictionary Or Local Migrations", ""]
    if drift['missing_in_dict']:
        lines.extend(f"- `{p}`" for p in drift['missing_in_dict'])
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Frontend Code Permission Tokens Missing In Known Sources", ""]
    if drift['frontend_missing_in_sources']:
        lines.extend(f"- `{permission}`" for permission in drift['frontend_missing_in_sources'])
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Runtime Dictionary Permissions Not Directly Seen In Backend Annotations", ""]
    if drift['dict_not_in_backend']:
        lines.extend(permission_bullet(p, perm_dict) for p in drift['dict_not_in_backend'])
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Runtime Dictionary Permissions Referenced Outside Backend Annotations", ""]
    if drift['dict_with_code_refs']:
        lines.extend(permission_bullet(p, perm_dict) for p in drift['dict_with_code_refs'])
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Runtime Dictionary Permissions Not Seen In Backend Annotations Or Code References", ""]
    if drift['dict_not_seen_anywhere']:
        lines.extend(permission_bullet(p, perm_dict) for p in drift['dict_not_seen_anywhere'])
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Compatibility Or Deprecated Dictionary Permissions Excluded From Runtime Drift", ""]
    if drift['compatibility_dict_perms']:
        lines.extend(permission_bullet(p, perm_dict) for p in drift['compatibility_dict_perms'])
    else:
        lines.append(NONE_ROW)

    lines += _render_code_refs(drift['dict_not_in_backend'], code_permission_refs)

    lines += ["", "### UI Permission Tokens Missing In Known Sources", ""]
    if drift['ui_missing_in_sources']:
        lines.extend(f"- `{permission}`" for permission in drift['ui_missing_in_sources'])
    else:
        lines.append(NONE_ROW)
    return lines


def _render_code_refs(dict_not_in_backend, code_permission_refs):
    lines = [
        "",
        "### Backend Code References For Dictionary-Only Permissions",
        "",
        "| Permission | Code Reference | Context |",
        "| --- | --- | --- |",
    ]
    rows = 0
    for permission in dict_not_in_backend:
        for ref in code_permission_refs.get(permission, []):
            rows += 1
            lines.append(f"| `{permission}` | `{ref.source}` | `{ref.context}` |")
    if rows == 0:
        lines.append("| None |  |  |")
    return lines


def _render_api_drift(unmatched_pc_apis, unmatched_mobile_apis):
    lines = ["", "## API Drift Candidates", ""]
    lines += ["### PC API Wrappers Without Backend Endpoint Match", ""]
    if unmatched_pc_apis:
        lines.extend(f"- `{api.method} {normalize_api_path(api.path, '/api')}` from `{api.source}` (`{api.name}`)" for api in unmatched_pc_apis)
    else:
        lines.append(NONE_ROW)

    lines += ["", "### Mini Program API Wrappers Without Backend Endpoint Match", ""]
    if unmatched_mobile_apis:
        lines.extend(f"- `{api.method} {normalize_api_path(api.path, '')}` from `{api.source}` (`{api.name}`)" for api in unmatched_mobile_apis)
    else:
        lines.append(NONE_ROW)
    return lines


def _render_notes():
    return [
        "",
        "## Notes",
        "",
        "- A dictionary permission can be valid even when it is not directly seen in annotations if enforcement happens in service-layer checks.",
        "- Compatibility/deprecated dictionary rows are kept visible but excluded from runtime drift alerts when the permission dictionary documents them as non-runtime authorization.",
        "- A backend permission missing in both the generated dictionary and local Flyway sys_menu migrations is high-risk because users may not be able to receive that authority through normal role-menu binding.",
        "- This report is local-source based. For production sign-off, regenerate from live database configuration.",
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("02-迭代源代码"))
    parser.add_argument("--out", type=Path, default=Path("02-迭代源代码/docs/unified-feature-audit.md"))
    args = parser.parse_args()

    root = args.root
    routes = extract_pc_routes(root / "aigicrm-ui/src/router/index.js", root)
    pc_apis = extract_api_functions(root / "aigicrm-ui/src/api", root)
    mobile_pages = extract_mobile_pages(root / "aigicrm-miniprogram/app.json", root)
    mobile_apis = extract_mobile_apis(root / "aigicrm-miniprogram/api", root)
    endpoints = extract_backend_endpoints(root / "aigicrm-backend/src/main/java", root)
    perm_dict = extract_permission_dict(root / "docs/permission/permission-dict.md")
    migration_perms = extract_migration_permissions(root / "aigicrm-backend/src/main/resources/db/migration")
    code_permission_refs = extract_backend_permission_references(
        root / "aigicrm-backend/src/main/java/com/aigicrm/module",
        root,
        sorted(set(perm_dict) - {p for e in endpoints for p in permission_tokens(e.permission)}),
    )
    pc_actions = extract_vue_actions(root / "aigicrm-ui/src/views", root)
    mobile_actions = extract_wxml_actions(root / "aigicrm-miniprogram/pages", root)
    frontend_permission_tokens = extract_frontend_permission_tokens([
        root / "aigicrm-ui/src",
        root / "aigicrm-miniprogram",
    ])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(render(args, routes, pc_apis, mobile_pages, mobile_apis, endpoints, perm_dict, migration_perms, code_permission_refs, frontend_permission_tokens, pc_actions, mobile_actions), encoding="utf-8")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
