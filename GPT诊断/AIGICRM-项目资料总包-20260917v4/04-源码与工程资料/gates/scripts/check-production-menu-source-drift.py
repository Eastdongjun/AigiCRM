#!/usr/bin/env python3
"""Compare a production sys_menu dump with the frontend route/icon contract.

The checker intentionally reads only sys_menu DDL/DML from a SQL dump. It does
not print credentials, password hashes, tokens, or business table rows.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def parse_sql_tuple_fields(row_text: str) -> list[str | None]:
    fields: list[str | None] = []
    current = []
    quoted = False
    escaped = False

    for char in row_text:
        if quoted:
            if escaped:
                current.append(char)
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                quoted = False
            else:
                current.append(char)
            continue

        if char == "'":
            quoted = True
        elif char == ",":
            value = "".join(current).strip()
            fields.append(None if value.upper() == "NULL" else value)
            current = []
        else:
            current.append(char)

    value = "".join(current).strip()
    fields.append(None if value.upper() == "NULL" else value)
    return fields


def parse_insert_rows(sql: str, table: str) -> list[list[str | None]]:
    rows: list[list[str | None]] = []
    insert_re = re.compile(rf"INSERT\s+INTO\s+`?{re.escape(table)}`?\s+VALUES\s*(.*?);", re.S | re.I)

    for insert in insert_re.finditer(sql):
        values = insert.group(1)
        i = 0
        while i < len(values):
            if values[i] != "(":
                i += 1
                continue

            j = i + 1
            quoted = False
            escaped = False
            while j < len(values):
                char = values[j]
                if quoted:
                    if escaped:
                        escaped = False
                    elif char == "\\":
                        escaped = True
                    elif char == "'":
                        quoted = False
                elif char == "'":
                    quoted = True
                elif char == ")":
                    break
                j += 1

            rows.append(parse_sql_tuple_fields(values[i + 1:j]))
            i = j + 1

    return rows


def parse_sys_menu(sql: str) -> list[dict[str, object]]:
    rows = parse_insert_rows(sql, "sys_menu")
    menus = []
    for row in rows:
        if len(row) < 11:
            raise ValueError(f"sys_menu row has {len(row)} fields; expected at least 11")
        menus.append(
            {
                "id": int(row[0] or 0),
                "parent_id": int(row[1] or 0),
                "menu_name": row[2] or "",
                "menu_type": int(row[3] or 0),
                "path": row[4] or "",
                "permission": row[6] or "",
                "icon": row[7] or "",
                "sort_order": int(row[8] or 0),
                "visible": int(row[9] or 0),
                "status": int(row[10] or 0),
            }
        )
    return menus


def frontend_route_paths(router_source: str) -> set[str]:
    paths = set(re.findall(r"path:\s*'(/[^']*)'", router_source))
    paths.update(
        "/" + path
        for path in re.findall(r"path:\s*'([^'/][^']*)'", router_source)
        if not path.startswith(":")
    )
    return paths


def frontend_icon_names(main_source: str) -> set[str]:
    imported_or_object_shorthand = set(re.findall(r"^\s*([A-Za-z][A-Za-z0-9_]*),\s*$", main_source, re.M))
    aliases = set(re.findall(r"^\s*([a-z][A-Za-z0-9_]*):", main_source, re.M))
    return imported_or_object_shorthand | aliases


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sql", required=True, type=Path, help="Production SQL dump containing sys_menu rows")
    parser.add_argument("--router", default=Path("aigicrm-ui/src/router/index.js"), type=Path)
    parser.add_argument("--main-js", default=Path("aigicrm-ui/src/main.js"), type=Path)
    args = parser.parse_args()

    sql_text = args.sql.read_text(encoding="utf-8", errors="replace")
    menus = parse_sys_menu(sql_text)
    route_paths = frontend_route_paths(args.router.read_text(encoding="utf-8"))
    icon_names = frontend_icon_names(args.main_js.read_text(encoding="utf-8"))

    children: dict[int, list[dict[str, object]]] = {}
    for menu in menus:
        children.setdefault(int(menu["parent_id"]), []).append(menu)

    visible_pages_or_dirs = [
        menu for menu in menus
        if menu["status"] == 1 and menu["visible"] == 1 and menu["menu_type"] in (1, 2)
    ]

    missing_pages = []
    ignored_groups = []
    for menu in visible_pages_or_dirs:
        path = str(menu["path"])
        if not path or path == "#" or path in route_paths:
            continue
        has_visible_page_children = any(
            child["status"] == 1
            and child["visible"] == 1
            and child["menu_type"] == 2
            and bool(child["path"])
            for child in children.get(int(menu["id"]), [])
        )
        if path.endswith("-group") and has_visible_page_children:
            ignored_groups.append(menu)
        else:
            missing_pages.append(menu)

    missing_icons = [
        menu for menu in visible_pages_or_dirs
        if menu["icon"] and menu["icon"] != "None" and str(menu["icon"]) not in icon_names
    ]

    print(
        "Production menu rows: "
        f"{len(menus)} total, {len(visible_pages_or_dirs)} visible page/directory rows, "
        f"{len(route_paths)} frontend route paths"
    )
    if ignored_groups:
        names = ", ".join(f"{menu['menu_name']}({menu['path']})" for menu in ignored_groups)
        print(f"Ignored grouping placeholder paths: {names}")

    if missing_pages:
        print("\nERROR: production visible menu paths missing frontend routes:")
        for menu in missing_pages:
            print(f"- id={menu['id']} name={menu['menu_name']} path={menu['path']} permission={menu['permission']}")

    if missing_icons:
        print("\nERROR: production visible menu icons missing frontend registration:")
        for menu in missing_icons:
            print(f"- id={menu['id']} name={menu['menu_name']} icon={menu['icon']}")

    if missing_pages or missing_icons:
        return 1

    print("PASS: production menu page routes and icons are compatible with frontend source.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
