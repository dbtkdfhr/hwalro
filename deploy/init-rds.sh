#!/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/.env.rds-init}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "RDS initialization environment file not found: ${ENV_FILE}" >&2
    echo "Copy deploy/.env.rds-init.example to deploy/.env.rds-init and fill in the real values." >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required to run the RDS initializer." >&2
    exit 1
fi

docker run --rm --pull=missing \
    --env-file "$ENV_FILE" \
    --volume "${SCRIPT_DIR}/init-db:/init-db:ro" \
    mysql:8.4 \
    bash /init-db/run-rds-init.sh
