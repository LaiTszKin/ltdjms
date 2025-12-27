.PHONY: build test clean run docker-build docker-up docker-down docker-logs db-up db-down docker-dev format format-check

# Maven commands
build:
	mvn clean package -DskipTests

format:
	mvn spotless:apply

format-check:
	mvn spotless:check

test:
	mvn test

test-integration:
	mvn verify

verify:
	mvn clean verify

coverage-check:
	mvn clean verify -DskipTests=false

clean:
	mvn clean

coverage:
	mvn clean test jacoco:report
	open target/site/jacoco/index.html
	
# Docker commands
update:
	git pull origin main
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

# Full development setup
dev: db-up
	@echo "PostgreSQL is running on localhost:5432"
	@echo "Run 'make run' to start the bot after building"

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
	@echo "  run              - Run the bot locally"
	@echo "  update           - Build Docker image"
	@echo "  start            - Start all services with Docker Compose (no rebuild)"
	@echo "  start-dev        - Build (using layer cache) and start all services"
	@echo "  restart          - Restart all Docker services"
	@echo "  stop             - Stop all Docker services"
	@echo "  logs             - Follow Docker logs"
	@echo "  db-up            - Start PostgreSQL only"
	@echo "  db-down          - Stop PostgreSQL"
	@echo "  dev              - Start development environment"
