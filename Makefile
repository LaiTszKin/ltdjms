.PHONY: build test clean docker-build docker-up docker-down docker-logs db-up db-down docker-dev format format-check lint setup-env update-env db-create db-create-test mvn-build mvn-test mvn-format mvn-format-check mvn-lint mvn-clean mvn-coverage mvn-verify mvn-coverage-check mvn-test-integration

# TypeScript commands (default)
build:
	npx tsc --project packages/shared && \
	npx tsc --project packages/economy && \
	npx tsc --project packages/shop && \
	npx tsc --project packages/dispatch && \
	npx tsc --project packages/ai && \
	npx tsc --project packages/admin && \
	npx tsc apps/bot/src/main.ts --outDir apps/bot/dist --declaration --sourceMap --skipLibCheck \
		--strict --moduleResolution NodeNext --module NodeNext --target ES2022 --esModuleInterop

test:
	pnpm vitest run --project @ltdjms/shared && \
	pnpm vitest run --project @ltdjms/admin && \
	pnpm vitest run --project @ltdjms/ai && \
	pnpm vitest run --project @ltdjms/dispatch && \
	pnpm vitest run --project @ltdjms/economy --exclude '**/*.pbt.test.ts' && \
	for f in packages/economy/src/__tests__/*.pbt.test.ts; do \
	  pnpm vitest run --project @ltdjms/economy "$$f" || exit 1; \
	done && \
	pnpm vitest run --project @ltdjms/shop --exclude '**/*.pbt.test.ts' --exclude '**/*-e2e.test.ts' && \
	for f in packages/shop/src/__tests__/*.pbt.test.ts; do \
	  pnpm vitest run --project @ltdjms/shop "$$f" || exit 1; \
	done

format:
	pnpm prettier --write "packages/*/src/**/*.ts" "*.ts" "*.mjs" "tsconfig.json" ".prettierrc"

format-check:
	pnpm prettier --check "packages/*/src/**/*.ts"

lint:
	pnpm eslint --ignore-pattern 'dist/' 'packages/*/src/'

# Maven commands
mvn-build:
	mvn clean package -DskipTests

mvn-format:
	mvn spotless:apply

mvn-format-check:
	mvn spotless:check

mvn-test:
	mvn test

mvn-test-integration:
	mvn verify

mvn-verify:
	mvn clean verify

mvn-coverage-check:
	mvn clean verify -DskipTests=false

mvn-clean:
	mvn clean

mvn-coverage:
	mvn clean test jacoco:report
	open target/site/jacoco/index.html

# TypeScript commands (pnpm monorepo)
ts-build:
	tsc -b

ts-test:
	pnpm vitest run

ts-format:
	pnpm prettier --write "packages/*/src/**/*.ts" "*.ts" "*.mjs" "tsconfig.json" ".prettierrc"

ts-format-check:
	pnpm prettier --check "packages/*/src/**/*.ts"

ts-lint:
	pnpm eslint packages/*/src/
	
# Docker commands
update:
	git pull origin main
	make update-env
	docker compose build

start:
	docker compose up -d

start-dev:
	docker compose up --build -d

stop:
	docker compose down

logs:
	docker compose logs -f

restart:
	docker compose down
	docker compose up -d

# Database only (for local development)
db-up:
	docker compose up -d postgres

db-down:
	docker compose down postgres

db-create:
	@./scripts/db/create-db.sh

db-create-test:
	@./scripts/db/create-db.test.sh

# Full development setup
dev: db-up
	@echo "PostgreSQL is running on localhost:5432"
	@echo "Run 'java -jar target/ltdjms-*.jar' to start the bot after building"

# Environment setup
setup-env:
	@./scripts/setup-env.sh

update-env:
	@./scripts/sync-env.sh

# Help
help:
	@echo "Available targets:"
	@echo "  build            - Build the project (skip tests)"
	@echo "  format           - Format code with Spotless"
	@echo "  format-check     - Check code format with Spotless"
	@echo "  test             - Run unit tests"
	@echo "  test-integration - Run all tests including integration"
	@echo "  verify           - Clean build and run all tests with coverage check"
	@echo "  coverage-check   - Run tests and enforce 80% coverage threshold"
	@echo "  coverage         - Generate code coverage report"
	@echo "  clean            - Clean build artifacts"
	@echo "  setup-env        - Interactive .env setup assistant for deployment values"
	@echo "  update-env       - Sync .env with .env.example (backup to .env.bak)"
	@echo "  update           - Pull latest changes, sync env, and build Docker image"
	@echo "  start            - Start all services with Docker Compose (no rebuild)"
	@echo "  start-dev        - Build (using layer cache) and start all services"
	@echo "  restart          - Restart all Docker services"
	@echo "  stop             - Stop all Docker services"
	@echo "  logs             - Follow Docker logs"
	@echo "  db-up            - Start PostgreSQL only"
	@echo "  db-down          - Stop PostgreSQL"
	@echo "  db-create        - Create database from .env if missing"
	@echo "  db-create-test   - Run edge-case tests for scripts/db/create-db.sh"
	@echo "  dev              - Start development environment"
