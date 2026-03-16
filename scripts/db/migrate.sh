#!/usr/bin/env bash
#
# Database Migration Script
#
# This script executes Flyway database migrations using the application's migration files.
# It can be used in local, test, and production environments.
#
# Usage:
#   ./scripts/db/migrate.sh [command]
#
# Commands:
#   migrate  - Apply pending migrations (default)
#   info     - Show current schema version and pending migrations
#   validate - Validate applied migrations against available ones
#   repair   - Repair the Flyway schema history table
#
# Environment Variables:
#   DB_URL / DATABASE_URL           - PostgreSQL connection URL
#   DB_USERNAME / DATABASE_USERNAME - Database username
#   DB_PASSWORD / DATABASE_PASSWORD - Database password
#
# Examples:
#   ./scripts/db/migrate.sh              # Apply migrations
#   ./scripts/db/migrate.sh info         # Show migration status
#   DB_URL=jdbc:postgresql://prod-host:5432/mydb ./scripts/db/migrate.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"

read_env_value() {
    local key="$1"
    local file="$2"

    awk -v key="$key" '
      {
        line = $0
        sub(/\r$/, "", line)
        if (line ~ /^[[:space:]]*#/ || line ~ /^[[:space:]]*$/) next
        sub(/^[[:space:]]*export[[:space:]]+/, "", line)
        if (line !~ /=/) next

        raw_key = line
        sub(/=.*/, "", raw_key)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", raw_key)
        if (raw_key != key) next

        value = line
        sub(/^[^=]*=/, "", value)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)

        quoted = 0
        if (value ~ /^".*"$/ || value ~ /^'\''.*'\''$/) {
          value = substr(value, 2, length(value) - 2)
          quoted = 1
        }
        if (!quoted) {
          sub(/[[:space:]]+#.*/, "", value)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        }

        print value
        exit
      }
    ' "$file"
}

resolve_config_value() {
    local primary_key="$1"
    local secondary_key="$2"

    if [[ -n "${!primary_key:-}" ]]; then
        printf '%s' "${!primary_key}"
        return
    fi

    if [[ -n "$secondary_key" && -n "${!secondary_key:-}" ]]; then
        printf '%s' "${!secondary_key}"
        return
    fi

    if [[ -f "$ENV_FILE" ]]; then
        local value
        value="$(read_env_value "$primary_key" "$ENV_FILE" || true)"
        if [[ -n "$value" ]]; then
            printf '%s' "$value"
            return
        fi

        if [[ -n "$secondary_key" ]]; then
            value="$(read_env_value "$secondary_key" "$ENV_FILE" || true)"
            if [[ -n "$value" ]]; then
                printf '%s' "$value"
                return
            fi
        fi
    fi
}

redact_jdbc_url() {
    local jdbc_url="$1"

    if [[ -z "$jdbc_url" ]]; then
        printf '%s' "$jdbc_url"
        return
    fi

    python3 - "$jdbc_url" <<'PY'
import sys
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

jdbc_url = sys.argv[1]
prefix = "jdbc:"
if not jdbc_url.startswith(prefix):
    print(jdbc_url)
    raise SystemExit(0)

inner_url = jdbc_url[len(prefix):]
parts = urlsplit(inner_url)
hostname = parts.hostname or ""
userinfo = "***@" if parts.username is not None or parts.password is not None else ""
host = hostname
if ":" in hostname and not hostname.startswith("["):
    host = f"[{hostname}]"
port = f":{parts.port}" if parts.port is not None else ""
netloc = f"{userinfo}{host}{port}"

redacted_params = []
for key, value in parse_qsl(parts.query, keep_blank_values=True):
    if key.lower() in {"password", "sslpassword"}:
        redacted_params.append((key, "***"))
    else:
        redacted_params.append((key, value))

query = urlencode(redacted_params)
print(prefix + urlunsplit((parts.scheme, netloc, parts.path, query, parts.fragment)))
PY
}

# Database configuration with defaults
DATABASE_URL="$(resolve_config_value "DB_URL" "DATABASE_URL")"
DATABASE_USERNAME="$(resolve_config_value "DB_USERNAME" "DATABASE_USERNAME")"
DATABASE_PASSWORD="$(resolve_config_value "DB_PASSWORD" "DATABASE_PASSWORD")"

: "${DATABASE_URL:=jdbc:postgresql://localhost:5432/currency_bot}"
: "${DATABASE_USERNAME:=postgres}"
: "${DATABASE_PASSWORD:=postgres}"

# Migration location
MIGRATION_LOCATION="filesystem:$PROJECT_ROOT/src/main/resources/db/migration"

# Command to execute (default: migrate)
COMMAND="${1:-migrate}"

# Check if Flyway is available via Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH" >&2
    exit 1
fi

# Build the project first to ensure migration files are up-to-date
echo "Building project..."
mvn -q compile -DskipTests

# Execute Flyway command using Maven plugin
echo ""
echo "Database Migration"
echo "=================="
echo "URL:      $(redact_jdbc_url "$DATABASE_URL")"
echo "User:     $DATABASE_USERNAME"
echo "Location: $MIGRATION_LOCATION"
echo "Command:  $COMMAND"
echo ""

# Use flyway-maven-plugin for migration
case "$COMMAND" in
    migrate)
        echo "Applying pending migrations..."
        mvn -q flyway:migrate \
            -Dflyway.url="$DATABASE_URL" \
            -Dflyway.user="$DATABASE_USERNAME" \
            -Dflyway.password="$DATABASE_PASSWORD" \
            -Dflyway.locations="$MIGRATION_LOCATION" \
            -Dflyway.baselineOnMigrate=true \
            -Dflyway.baselineVersion=0
        echo ""
        echo "Migration completed successfully."
        ;;
    info)
        echo "Current migration status:"
        mvn -q flyway:info \
            -Dflyway.url="$DATABASE_URL" \
            -Dflyway.user="$DATABASE_USERNAME" \
            -Dflyway.password="$DATABASE_PASSWORD" \
            -Dflyway.locations="$MIGRATION_LOCATION"
        ;;
    validate)
        echo "Validating migrations..."
        mvn -q flyway:validate \
            -Dflyway.url="$DATABASE_URL" \
            -Dflyway.user="$DATABASE_USERNAME" \
            -Dflyway.password="$DATABASE_PASSWORD" \
            -Dflyway.locations="$MIGRATION_LOCATION"
        echo ""
        echo "Validation passed."
        ;;
    repair)
        echo "Repairing schema history..."
        mvn -q flyway:repair \
            -Dflyway.url="$DATABASE_URL" \
            -Dflyway.user="$DATABASE_USERNAME" \
            -Dflyway.password="$DATABASE_PASSWORD" \
            -Dflyway.locations="$MIGRATION_LOCATION"
        echo ""
        echo "Repair completed."
        ;;
    *)
        echo "Unknown command: $COMMAND" >&2
        echo "Valid commands: migrate, info, validate, repair" >&2
        exit 1
        ;;
esac

exit 0
