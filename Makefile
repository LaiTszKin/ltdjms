.PHONY: build test clean run docker-build docker-up docker-down docker-logs db-up db-down docker-dev

# Maven commands
build:
	mvn clean package -DskipTests

test:
	mvn test

test-integration:
	mvn verify

clean:
	mvn clean

# Run locally (requires PostgreSQL running)
run:
	java -jar target/*.jar

# Docker commands
docker-build:
	docker compose build

docker-up:
	docker compose up -d

docker-dev:
	docker compose up --build -d

docker-down:
	docker compose down

docker-logs:
	docker compose logs -f

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
	@echo "  build           - Build the project (skip tests)"
	@echo "  test            - Run unit tests"
	@echo "  test-integration - Run all tests including integration"
	@echo "  clean           - Clean build artifacts"
	@echo "  run             - Run the bot locally"
	@echo "  docker-build    - Build Docker image"
	@echo "  docker-up       - Start all services with Docker Compose (no rebuild)"
	@echo "  docker-dev      - Build (using layer cache) and start all services"
	@echo "  docker-down     - Stop all Docker services"
	@echo "  docker-logs     - Follow Docker logs"
	@echo "  db-up           - Start PostgreSQL only"
	@echo "  db-down         - Stop PostgreSQL"
	@echo "  dev             - Start development environment"
