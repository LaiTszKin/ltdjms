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
#   DATABASE_URL      - PostgreSQL connection URL (default: from .env or localhost)
#   DATABASE_USERNAME - Database username (default: from .env or postgres)
#   DATABASE_PASSWORD - Database password (default: from .env or postgres)
#
# Examples:
#   ./scripts/db/migrate.sh              # Apply migrations
#   ./scripts/db/migrate.sh info         # Show migration status
#   DATABASE_URL=jdbc:postgresql://prod-host:5432/mydb ./scripts/db/migrate.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load environment variables from .env if it exists
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

# Database configuration with defaults
: "${DATABASE_URL:=jdbc:postgresql://localhost:5432/ltdjms}"
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
echo "URL:      $DATABASE_URL"
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
