# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Install Maven once so dependency download can be cached separately from source changes
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy Maven configuration and pre-fetch dependencies (stable layer)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Copy source code and build the application (skip tests for Docker build)
COPY src ./src
RUN mvn -q -B clean package -Dmaven.test.skip=true -Dmaven.test.failure.ignore=true && \
    # Copy the shaded application JAR (with dependencies) to a stable name
    cp target/ltdjms-*.jar app.jar

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/app.jar app.jar

# Create non-root user for security
RUN groupadd -r botuser 2>/dev/null || true && \
    useradd -r -u 1000 -g botuser botuser 2>/dev/null || \
    useradd -r -u 1001 -g botuser botuser
USER botuser

# Set default environment variables
ENV DISCORD_BOT_TOKEN=""
ENV DB_URL="jdbc:postgresql://postgres:5432/currency_bot"
ENV DB_USERNAME="postgres"
ENV DB_PASSWORD="postgres"

# Run the bot
ENTRYPOINT ["java", "-jar", "app.jar"]
