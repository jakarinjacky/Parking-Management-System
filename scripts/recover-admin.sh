#!/usr/bin/env bash
set -euo pipefail
cd /opt/parking-platform
if [[ ! -t 0 ]]; then
  echo 'Run from an interactive EC2 terminal.' >&2
  exit 1
fi
echo 'This stops the app briefly. A successful backup is required before reset.'
bash scripts/backup-postgres.sh
docker compose -f compose.aws.yml stop app
trap 'docker compose -f compose.aws.yml start app' EXIT
docker compose -f compose.aws.yml run --rm --no-deps app \
  java -cp 'bin:lib/*' platform.AdminRecovery
