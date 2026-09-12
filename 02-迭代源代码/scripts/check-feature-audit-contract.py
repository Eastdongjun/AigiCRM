#!/usr/bin/env python3
#!/usr/bin/env python3
"""Ensure the generated feature audit does not omit backend controller endpoints."""

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def main() -> int:
    wiring = load(ROOT / "scripts/check_api_wiring.py", "aigicrm_check_api_wiring")
    audit = load(ROOT / "scripts/gen-unified-feature-audit.py", "aigicrm_feature_audit")
    api_list = load(ROOT / "scripts/gen-api.py", "aigicrm_gen_api")
    _, readable = wiring.parse_backend(ROOT / "aigicrm-backend/src/main/java")
    wiring_endpoints = {tuple(item.split(" ", 1)) for item in readable}
    generated_endpoints = {
        (endpoint.method, endpoint.path)
        for endpoint in audit.extract_backend_endpoints(
            ROOT / "aigicrm-backend/src/main/java", ROOT
        )
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
    print(f"PASS: feature audit and API list cover all {len(wiring_endpoints)} backend endpoints.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
