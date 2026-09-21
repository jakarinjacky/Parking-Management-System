#!/usr/bin/env bash
set -euo pipefail
cd /opt/parking-platform
mkdir -p backups
docker compose -f compose.aws.yml exec -T database \
  pg_dump -U parking_app -d parking_platform -Fc > "backups/parking-$(date -u +%F).dump"
find backups -type f -name 'parking-*.dump' -mtime +7 -delete
