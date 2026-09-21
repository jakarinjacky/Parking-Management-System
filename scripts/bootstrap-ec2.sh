#!/usr/bin/env bash
set -euo pipefail

# Amazon Linux 2023 user-data script for the first EC2 boot.
dnf update -y
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /opt/parking-platform
chown ec2-user:ec2-user /opt/parking-platform

# Amazon Linux package repositories do not always include the Compose plugin.
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/v2.39.2/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
