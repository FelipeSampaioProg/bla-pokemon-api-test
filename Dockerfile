# syntax=docker/dockerfile:1
#
# Pokedex BFF — containerized build and execution.
#
# Three stages: the React front-end is built with Node, the Spring Boot back-end (which serves that build) with
# Maven, and the final image holds only a Java runtime and the application jar.
#
#   docker build -t pokedex-bff .
#   docker compose up --build        # application + MySQL, with the demonstration data (see docker-compose.yml)

# ---- 1. Front-end (React + Vite) ----
FROM node:24-bookworm-slim AS frontend
WORKDIR /build/WebAppClient
COPY WebAppClient/package.json WebAppClient/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY WebAppClient/ ./
RUN npm run build

# ---- 2. Back-end (Spring Boot), with the front-end build as its static resources ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
# Dependencies in their own layer: rebuilt only when the pom changes.
RUN mvn -B -q dependency:go-offline
COPY src ./src
# The image always serves the front-end built above, never a stale copy.
RUN rm -rf src/main/resources/static
COPY --from=frontend /build/WebAppClient/dist ./src/main/resources/static
# Tests run in the pipeline (./mvnw verify), not on every image build.
RUN mvn -B -q package -DskipTests

# ---- 3. Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S pokedex && adduser -S pokedex -G pokedex && mkdir -p /app/logs && chown pokedex:pokedex /app/logs
COPY --from=backend /build/target/bla-pokemon-technical-test-*.jar /app/app.jar
USER pokedex

# Connection to MySQL (required); every other setting has a default (see the README's configuration table).
ENV DB_URL="jdbc:mysql://db:3306/pokemon?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
    DB_USERNAME="pokemon" \
    DB_PASSWORD="pokebola" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
