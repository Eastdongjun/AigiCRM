#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENTITY="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/entity/BizIncentiveRecord.java"
BASELINE="$ROOT/aigicrm-backend/src/main/resources/db/migration/V1__baseline.sql"
MIGRATION="$ROOT/sql/migration-incentive-install-files.sql"

fail() { echo "FAIL: $*" >&2; exit 1; }
for field in fileId filePreId fileMidId filePostId fileTrackId; do
  grep -Eq "private String ${field};" "$ENTITY" || fail "entity field ${field} is not String"
done
for column in file_id file_pre_id file_mid_id file_post_id file_track_id; do
  grep -Eq "\`${column}\` varchar\(500\)" "$BASELINE" || fail "baseline column ${column} is not varchar(500)"
done
for column in file_pre_id file_mid_id file_post_id file_track_id; do
  grep -Eq "ADD COLUMN ${column} VARCHAR\(500\)" "$MIGRATION" || fail "incremental migration adds ${column} with the wrong type"
done
! grep -Eq "ADD COLUMN (file_pre_id|file_mid_id|file_post_id|file_track_id) BIGINT" "$MIGRATION" || fail 'incremental migration still creates file IDs as BIGINT'
echo 'PASS: incentive file ID schema matches JSON-backed String fields.'
