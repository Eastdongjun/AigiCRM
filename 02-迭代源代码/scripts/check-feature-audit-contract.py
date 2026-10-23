#!/usr/bin/env python3
"""Ensure the generated feature audit does not omit expected project contracts."""

import importlib.util
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MANIFEST_PAGE_PATTERN = re.compile(r"pages/[A-Za-z0-9_./:-]+(?:\?[^`|), ]+)?")
MANIFEST_PC_ROUTE_PATTERN = re.compile(r"`(/[^`]+)`")
MANIFEST_PERMISSION_TOKEN_PATTERN = re.compile(r"`([^`]+)`")
# 【2026-09-29 对齐 manifest 中文化 2b80b62e】状态词表扩为双语; 判定按"剥离注记后的规范化前缀"
# 规范化: 去 ` 反引号/⟦…⟧ 补录注记/首尾空白 后, 前缀匹配任一已知状态即合法
MANIFEST_MOBILE_STATUSES = {
    "implemented": "implemented",
    "已实现": "implemented",
    "planned": "planned",
    "规划中": "planned",
    "PC-only": "PC-only",
}
MANIFEST_MOBILE_STATUS_PREFIXES = tuple(
    sorted(MANIFEST_MOBILE_STATUSES.keys(), key=len, reverse=True)
)
MANIFEST_ANNOTATION_PATTERN = __import__("re").compile(r"⟦[^⟧]*⟧")
FEATURE_KEY_PATTERN = re.compile(r"^[a-z][a-z0-9]*(?:\.[a-z][A-Za-z0-9]*)+$")
MANIFEST_NON_PERMISSION_TOKENS = {"public", "authenticated"}


def iter_manifest_feature_rows(manifest_path: Path):
    for line_number, line in enumerate(
        manifest_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        if not line.startswith("| `"):
            continue
        columns = [column.strip() for column in line.strip().strip("|").split("|")]
        if len(columns) < 8:
            continue
        yield line_number, columns


def load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def extract_manifest_mobile_pages(manifest_path: Path) -> dict[str, set[str]]:
    """Extract Mini Program page references from the manifest table."""
    pages_by_feature: dict[str, set[str]] = {}
    for _line_number, columns in iter_manifest_feature_rows(manifest_path):
        feature_key = columns[0].strip("`")
        mini_program_column = columns[3]
        pages = {
            token.split("?", 1)[0].replace(":id", "").rstrip("/")
            for token in MANIFEST_PAGE_PATTERN.findall(mini_program_column)
        }
        if pages:
            pages_by_feature[feature_key] = pages
    return pages_by_feature


def assert_manifest_feature_keys() -> list[str]:
    errors: list[str] = []
    seen: dict[str, int] = {}
    for line_number, columns in iter_manifest_feature_rows(
        ROOT / "docs/unified-feature-manifest.md"
    ):
        feature_key = columns[0].strip("`")
        if len(columns) != 8:
            errors.append(
                f"line {line_number} {feature_key}: expected 8 columns, got {len(columns)}"
            )
        if not FEATURE_KEY_PATTERN.match(feature_key):
            errors.append(
                f"line {line_number} {feature_key}: feature key must use module.action form"
            )
        previous_line = seen.get(feature_key)
        if previous_line is not None:
            errors.append(
                f"line {line_number} {feature_key}: duplicate feature key also on line {previous_line}"
            )
        seen[feature_key] = line_number
    return errors


def normalize_pc_route(path: str) -> str:
    if path == "/":
        return "/"
    return path.strip("/")


def assert_manifest_pc_routes(routes) -> list[str]:
    pc_routes = {normalize_pc_route(route.path) for route in routes}
    errors: list[str] = []
    for line_number, columns in iter_manifest_feature_rows(
        ROOT / "docs/unified-feature-manifest.md"
    ):
        feature_key = columns[0].strip("`")
        pc_column = columns[2]
        for route in MANIFEST_PC_ROUTE_PATTERN.findall(pc_column):
            if route.startswith("/api/"):
                continue
            normalized = normalize_pc_route(route)
            if normalized not in pc_routes:
                errors.append(
                    f"line {line_number} {feature_key}: PC route {route!r} is not in router"
                )
    return errors


def expand_manifest_permission_token(token: str, known_permissions: set[str]) -> tuple[set[str], str | None]:
    if token in MANIFEST_NON_PERMISSION_TOKENS or ":" not in token:
        return set(), None
    if token.endswith(":*"):
        prefix = token[:-1]
        matches = {permission for permission in known_permissions if permission.startswith(prefix)}
        if not matches:
            return set(), f"{token!r} has no known permissions with prefix {prefix!r}"
        return matches, None
    prefix, _separator, suffix = token.rpartition(":")
    if "/" in suffix:
        expanded = {f"{prefix}:{part}" for part in suffix.split("/") if part}
        missing = sorted(expanded - known_permissions)
        if missing:
            return expanded, f"{token!r} expands to unknown permissions {missing}"
        return expanded, None
    if token not in known_permissions:
        return {token}, f"{token!r} is not in backend, permission dictionary, or migrations"
    return {token}, None


def assert_manifest_permissions(known_permissions: set[str]) -> list[str]:
    errors: list[str] = []
    for line_number, columns in iter_manifest_feature_rows(
        ROOT / "docs/unified-feature-manifest.md"
    ):
        feature_key = columns[0].strip("`")
        permission_column = columns[4]
        for token in MANIFEST_PERMISSION_TOKEN_PATTERN.findall(permission_column):
            _expanded, error = expand_manifest_permission_token(token, known_permissions)
            if error:
                errors.append(f"line {line_number} {feature_key}: {error}")
    return errors


def assert_manifest_mobile_statuses() -> list[str]:
    errors: list[str] = []
    for line_number, columns in iter_manifest_feature_rows(
        ROOT / "docs/unified-feature-manifest.md"
    ):
        feature_key = columns[0].strip("`")
        mini_program_column = columns[3]
        raw_status = columns[7]
        # 规范化: 反引号→原样剥离; ⟦补录注记⟧→剥离; 取已知状态前缀归一
        normalized_status = MANIFEST_ANNOTATION_PATTERN.sub(
            "", raw_status
        ).replace("`", "").strip()
        # PC-only 作为功能定性可在句中(如"发起 `PC-only`(审批展示已实现)"),按词元包含判定
        mobile_status = (
            "PC-only"
            if "PC-only" in normalized_status
            else next(
                (
                    canonical
                    for prefix, canonical in MANIFEST_MOBILE_STATUSES.items()
                    if normalized_status.startswith(prefix)
                ),
                None,
            )
        )
        pages = MANIFEST_PAGE_PATTERN.findall(mini_program_column)
        if mobile_status is None:
            errors.append(
                f"line {line_number} {feature_key}: unknown mobile status {raw_status!r}"
            )
        if mobile_status == "PC-only" and pages:
            errors.append(
                f"line {line_number} {feature_key}: PC-only row references Mini Program pages {pages}"
            )
        if mobile_status == "implemented" and mini_program_column in {"", "pending decision"}:
            errors.append(
                f"line {line_number} {feature_key}: implemented row lacks Mini Program ownership"
            )
    return errors


def load_registered_miniprogram_pages(app_json_path: Path) -> set[str]:
    app_config = json.loads(app_json_path.read_text(encoding="utf-8"))
    return set(app_config.get("pages", []))


def assert_manifest_pages_registered() -> list[tuple[str, str]]:
    manifest_pages = extract_manifest_mobile_pages(ROOT / "docs/unified-feature-manifest.md")
    registered_pages = load_registered_miniprogram_pages(ROOT / "aigicrm-miniprogram/app.json")
    missing_pages: list[tuple[str, str]] = []
    for feature_key, pages in sorted(manifest_pages.items()):
        for page in sorted(pages):
            if page not in registered_pages:
                missing_pages.append((feature_key, page))
    return missing_pages


def assert_registered_pages_in_manifest() -> list[str]:
    manifest_pages = extract_manifest_mobile_pages(ROOT / "docs/unified-feature-manifest.md")
    registered_pages = load_registered_miniprogram_pages(ROOT / "aigicrm-miniprogram/app.json")
    documented_pages = set().union(*manifest_pages.values()) if manifest_pages else set()
    return sorted(registered_pages - documented_pages)


def main() -> int:
    wiring = load(ROOT / "scripts/check_api_wiring.py", "aigicrm_check_api_wiring")
    audit = load(ROOT / "scripts/gen-unified-feature-audit.py", "aigicrm_feature_audit")
    api_list = load(ROOT / "scripts/gen-api.py", "aigicrm_gen_api")
    _, readable = wiring.parse_backend(ROOT / "aigicrm-backend/src/main/java")
    pc_routes = audit.extract_pc_routes(ROOT / "aigicrm-ui/src/router/index.js", ROOT)
    backend_endpoints = audit.extract_backend_endpoints(
        ROOT / "aigicrm-backend/src/main/java", ROOT
    )
    wiring_endpoints = {tuple(item.split(" ", 1)) for item in readable}
    generated_endpoints = {
        (endpoint.method, endpoint.path)
        for endpoint in backend_endpoints
    }
    missing = sorted(wiring_endpoints - generated_endpoints)
    extra = sorted(generated_endpoints - wiring_endpoints)
    if missing or extra:
        print(f"FAIL: feature audit endpoint drift; missing={missing}, extra={extra}")
        return 1
    generated_api_list = {
        (item["method"], item["path"])
        for item in api_list.extract_apis(str(ROOT / "aigicrm-backend/src/main/java"))
    }
    api_missing = sorted(wiring_endpoints - generated_api_list)
    api_extra = sorted(generated_api_list - wiring_endpoints)
    if api_missing or api_extra:
        print(f"FAIL: API list endpoint drift; missing={api_missing}, extra={api_extra}")
        return 1
    migration_permissions = audit.extract_migration_permissions(
        ROOT / "aigicrm-backend/src/main/resources/db/migration"
    )
    required_permissions = {
        "incentive:read-ranking:view": "nested INSERT ... SELECT permission registration",
    }
    missing_permissions = sorted(set(required_permissions) - set(migration_permissions))
    if missing_permissions:
        print(
            "FAIL: feature audit migration permission drift; "
            f"missing={missing_permissions}"
        )
        return 1
    read_ranking_source = migration_permissions["incentive:read-ranking:view"]
    if "V2026.09.13.4__add_incentive_read_ranking_menu.sql" not in read_ranking_source:
        print(
            "FAIL: feature audit migration permission source drift; "
            f"incentive:read-ranking:view source={read_ranking_source!r}"
        )
        return 1
    if "本月激励查询阅读排行榜" not in read_ranking_source:
        print(
            "FAIL: feature audit migration permission label drift; "
            f"incentive:read-ranking:view source={read_ranking_source!r}"
        )
        return 1
    dictionary_codes = ["sea:claim"]
    code_references = audit.extract_backend_permission_references(
        ROOT / "aigicrm-backend/src/main/java", ROOT, dictionary_codes
    )
    missing_code_references = [
        permission for permission in dictionary_codes
        if not code_references.get(permission)
    ]
    if missing_code_references:
        print(
            "FAIL: feature audit backend permission reference drift; "
            f"missing={missing_code_references}"
        )
        return 1
    permission_dict = audit.extract_permission_dict(ROOT / "docs/permission/permission-dict.md")
    runtime_dictionary_codes = {
        permission for permission, label in permission_dict.items()
        if audit.is_runtime_dictionary_permission(permission, label)
    }
    compatibility_codes = {
        "customer:share": "documented compatibility share permission",
        "sea:pool:delete": "documented pool-level delete compatibility permission",
    }
    unexpected_runtime_codes = sorted(set(compatibility_codes) & runtime_dictionary_codes)
    if unexpected_runtime_codes:
        print(
            "FAIL: compatibility permissions leaked into runtime drift set; "
            f"codes={unexpected_runtime_codes}"
        )
        return 1
    known_permissions = (
        set(permission_dict)
        | set(migration_permissions)
        | {
            permission
            for endpoint in backend_endpoints
            for permission in audit.permission_tokens(endpoint.permission)
        }
    )
    mobile_actions = audit.extract_wxml_actions(ROOT / "aigicrm-miniprogram/pages", ROOT)
    required_mobile_actions = {
        # 【钉随需求A 2026-10-16】行内分享按钮收敛进「···」面板; 分享可达入口=面板按钮,
        # shareFile 的权限显隐钉保留在 check-mobile-file-boundary.sh(if (showShare) sheetActions.push)
        ("···", "openActionSheet", "aigicrm-miniprogram/pages/files/index.wxml"),
        ("＋新客户", "openNewCustomer", "aigicrm-miniprogram/pages/home/index.wxml"),
        # 【2026-10-20 钉随代码】首页驾驶舱改版(d5d3c6a6): 快捷项=汉字图标块+标题, 原 ♜/✓ 字形图标退役
        ("激四项服务", "openIncentive", "aigicrm-miniprogram/pages/home/index.wxml"),
        ("审审批管理", "openApproval", "aigicrm-miniprogram/pages/home/index.wxml"),
        ("全部提醒 ›", "openReminder", "aigicrm-miniprogram/pages/home/index.wxml"),
        ("打开", "previewAttachment", "aigicrm-miniprogram/pages/incentive/detail/index.wxml"),
        ("下载", "downloadAttachment", "aigicrm-miniprogram/pages/incentive/detail/index.wxml"),
        ("预览", "previewSelectedFile", "aigicrm-miniprogram/pages/incentive/upload/index.wxml"),
        ("下载", "downloadSelectedFile", "aigicrm-miniprogram/pages/incentive/upload/index.wxml"),
        ("◉我的客户›", "openCustomers", "aigicrm-miniprogram/pages/profile/index.wxml"),
        ("★我的四项服务›", "openIncentive", "aigicrm-miniprogram/pages/profile/index.wxml"),
        ("↻我的捞取记录›", "openClaims", "aigicrm-miniprogram/pages/profile/index.wxml"),
    }
    discovered_mobile_actions = {
        (action.label, action.handler, action.source.split(":", 1)[0])
        for action in mobile_actions
    }
    missing_mobile_actions = sorted(required_mobile_actions - discovered_mobile_actions)
    if missing_mobile_actions:
        print(
            "FAIL: feature audit mobile WXML action parsing drift; "
            f"missing={missing_mobile_actions}"
        )
        return 1
    manifest_page_missing = assert_manifest_pages_registered()
    if manifest_page_missing:
        print(
            "FAIL: unified feature manifest references unregistered Mini Program pages; "
            f"missing={manifest_page_missing}"
        )
        return 1
    manifest_feature_key_errors = assert_manifest_feature_keys()
    if manifest_feature_key_errors:
        print(
            "FAIL: unified feature manifest feature-key contract drift; "
            f"errors={manifest_feature_key_errors}"
        )
        return 1
    manifest_pc_route_errors = assert_manifest_pc_routes(pc_routes)
    if manifest_pc_route_errors:
        print(
            "FAIL: unified feature manifest PC route contract drift; "
            f"errors={manifest_pc_route_errors}"
        )
        return 1
    manifest_permission_errors = assert_manifest_permissions(known_permissions)
    if manifest_permission_errors:
        print(
            "FAIL: unified feature manifest permission contract drift; "
            f"errors={manifest_permission_errors}"
        )
        return 1
    manifest_status_errors = assert_manifest_mobile_statuses()
    if manifest_status_errors:
        print(
            "FAIL: unified feature manifest mobile status contract drift; "
            f"errors={manifest_status_errors}"
        )
        return 1
    undocumented_registered_pages = assert_registered_pages_in_manifest()
    if undocumented_registered_pages:
        print(
            "FAIL: registered Mini Program pages missing unified feature manifest ownership; "
            f"missing={undocumented_registered_pages}"
        )
        return 1
    manifest_mobile_pages = extract_manifest_mobile_pages(ROOT / "docs/unified-feature-manifest.md")
    required_manifest_pages = {
        "auth.passwordRecovery": {"pages/login/forgot/index"},
        "incentive.index": {
            "pages/incentive/index",
            "pages/incentive/detail/index",
            "pages/incentive/upload/index",
        },
        # 【钉随代码 2026-10-07】cases 重定向壳随 1b3c865b 删除, 功能由激励页承接
        "incentive.legacyCasesAlias": {
            "pages/incentive/index",
        },
        "incentive.upload": {"pages/incentive/upload/index"},
        "customer.finance": {
            "pages/customer/detail/index",
            "pages/customer/finance/index",
            "pages/customer/finance/form/index",
        },
        "customer.contact": {
            "pages/customer/detail/index",
            "pages/customer/contact/index",
            "pages/customer/contact/form/index",
            "pages/customer/contact/detail/index",
        },
        "customer.filter": {"pages/customer/filter/index"},
        "customer.log": {"pages/customer/detail/index", "pages/customer/log/index"},
        "customer.timeline": {"pages/customer/detail/index", "pages/customer/timeline/index"},
        "customer.indicator": {"pages/customer/detail/index", "pages/customer/indicator/index"},
        "customer.relationship": {"pages/customer/detail/index"},
        "app.launcher": {"pages/app/index"},
        "file.aclView": {"pages/files/index"},
        "sea.edit": {"pages/sea/index", "pages/customer/form/index"},
        "sea.claim": {"pages/sea/index", "pages/sea/claim-detail/index"},
        "approval.detail": {"pages/approval/index", "pages/approval/detail/index"},
        "approval.approve": {"pages/approval/index", "pages/approval/detail/index"},
        "approval.reject": {"pages/approval/index", "pages/approval/detail/index"},
        "approval.submit": {"pages/customer/detail/index", "pages/sea/index"},
        # 【钉随代码 2026-10-07】notification 两页随 1b3c865b 删除, 提醒由 reminder 模块承载
        "reminder.notification": {
            "pages/reminder/index",
        },
        "score.ranking": {
            "pages/ranking/index",
            "pages/ranking/detail/index",
            "pages/customer/detail/index",
        },
        "auth.profile": {
            "pages/profile/index",
            "pages/profile/password/index",
            "pages/profile/scope/index",
        },
        "auth.feedback": {"pages/profile/index", "pages/profile/feedback/index"},
        "auth.wechatBinding": {"pages/profile/index", "pages/profile/wechat/index"},
        "auth.legalDocs": {
            "pages/profile/about/index",
            "pages/profile/agreement/index",
            "pages/profile/privacy/index",
        },
    }
    missing_manifest_pages = {
        feature_key: sorted(required_pages - manifest_mobile_pages.get(feature_key, set()))
        for feature_key, required_pages in required_manifest_pages.items()
        if required_pages - manifest_mobile_pages.get(feature_key, set())
    }
    if missing_manifest_pages:
        print(
            "FAIL: unified feature manifest missing required Mini Program page coverage; "
            f"missing={missing_manifest_pages}"
        )
        return 1
    print(f"PASS: feature audit and API list cover all {len(wiring_endpoints)} backend endpoints.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
