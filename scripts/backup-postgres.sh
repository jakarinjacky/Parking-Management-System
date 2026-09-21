#!/usr/bin/env bash
set -euo pipefail
cd /opt/parking-platform
umask 077
mkdir -p backups
backup_file="backups/parking-$(date -u +%Y%m%dT%H%M%S)-$$.dump"
temporary_file="$backup_file.partial"
trap 'rm -f "$temporary_file"' EXIT
docker compose -f compose.aws.yml exec -T database \
  pg_dump -U parking_app -d parking_platform -Fc > "$temporary_file"
test -s "$temporary_file"
mv "$temporary_file" "$backup_file"
find backups -type f -name 'parking-*.dump' -mtime +7 -delete
