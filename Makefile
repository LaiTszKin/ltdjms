.PHONY: build test test-integration verify clean format format-check lint setup-env update-env db-up db-down db-create db-create-test start start-dev stop logs restart dev update help ts-build ts-test ts-format ts-format-check ts-lint coverage coverage-check mvn-build mvn-test mvn-test-integration mvn-verify mvn-format mvn-format-check mvn-clean mvn-coverage mvn-coverage-check

# Java / Maven commands (default)
build:
	mvn clean package -DskipTests

test:
	mvn -Punit-tests test

test-integration:
	mvn clean verify

verify: build lint test

format:
	mvn spotless:apply

format-check:
	mvn spotless:check

lint:
	mvn -Ptype-safety -DskipTests test-compile

clean:
	mvn clean

coverage:
	mvn clean test jacoco:report
	open target/site/jacoco/index.html

coverage-check:
	mvn clean verify -DskipTests=false

# Legacy Maven aliases
mvn-build: build

mvn-test: test

mvn-test-integration: test-integration

mvn-verify: verify

mvn-format: format

mvn-format-check: format-check

mvn-clean: clean

mvn-coverage: coverage

mvn-coverage-check: coverage-check

# TypeScript commands (legacy monorepo)
ts-build:
	npx tsc --project packages/shared && \
	npx tsc --project packages/economy && \
	npx tsc --project packages/games && \
	npx tsc --project packages/shop && \
	npx tsc --project packages/user-panel && \
	npx tsc --project packages/dispatch && \
	npx tsc --project packages/ai && \
	npx tsc --project packages/admin && \
	npx tsc --project apps/bot

ts-test:
	pnpm vitest run --project @ltdjms/shared && \
	pnpm vitest run --project @ltdjms/games --exclude '**/*.pbt.test.ts' && \
	for f in packages/games/src/__tests__/*.pbt.test.ts; do \
	  pnpm vitest run --project @ltdjms/games "$$f" || exit 1; \
	done && \
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
	done && \
	pnpm vitest run --project @ltdjms/user-panel

ts-format:
	pnpm prettier --write "packages/*/src/**/*.ts" "*.ts" "*.mjs" "tsconfig.json" ".prettierrc"

ts-format-check:
	pnpm prettier --check "packages/*/src/**/*.ts"

ts-lint:
	pnpm eslint .

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
	@echo "  build            - Build the Java project (skip tests)"
	@echo "  format           - Format Java code with Spotless"
	@echo "  format-check     - Check Java code format with Spotless"
	@echo "  lint             - Run Java type-safety compile checks"
	@echo "  test             - Run Java unit tests"
	@echo "  test-integration - Run full Maven verify (integration tests + coverage gate)"
	@echo "  verify           - Build, lint, and unit tests"
	@echo "  coverage-check   - Run tests and enforce coverage threshold"
	@echo "  coverage         - Generate Java code coverage report"
	@echo "  clean            - Clean Maven build artifacts"
	@echo "  ts-build         - Build legacy TypeScript packages"
	@echo "  ts-test          - Run legacy TypeScript tests"
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
