#!/usr/bin/env bash
# Restores into a uniquely named temporary container. Never targets the production DB.
set -euo pipefail
if [[ $# != 1 || ! -s "$1" ]]; then echo 'Usage: bash scripts/restore-drill.sh /absolute/path/backup.dump' >&2; exit 1; fi
backup_path=$(realpath "$1")
cd "$(dirname "$0")/.."
drill_project="parking-restore-drill-$$"
export RESTORE_TEST_PASSWORD
RESTORE_TEST_PASSWORD=$(openssl rand -hex 24)
drill=(docker compose -p "$drill_project" -f compose.restore-test.yml)
trap '"${drill[@]}" down --volumes >/dev/null' EXIT
"${drill[@]}" up -d --wait --wait-timeout 90
"${drill[@]}" exec -T database pg_restore --no-owner --no-privileges --exit-on-error -U restore_check -d restore_check < "$backup_path"
"${drill[@]}" exec -T database psql -v ON_ERROR_STOP=1 -U restore_check -d restore_check -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM platform_state WHERE id=1 AND jsonb_typeof(state->'users')='array' AND jsonb_typeof(state->'sites')='array') THEN RAISE EXCEPTION 'Invalid restored platform state'; END IF; END \$\$;"
echo 'Restore drill passed. Production data was not modified.'
