# ── Stage 1: dependency cache ────────────────────────────────────────────────
# Pulling dependencies in a separate layer means a pom.xml-only change still
# benefits from the cached layer; only a pom.xml change triggers a re-download.
FROM maven:3.9.6-eclipse-temurin-17 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q

# ── Stage 2: build ────────────────────────────────────────────────────────────
FROM deps AS build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 3: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# Run as a non-root user — required by most container security policies.
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JVM flags:
#   UseContainerSupport     — JVM reads cgroup memory/CPU limits (default on 11+, explicit here for clarity)
#   MaxRAMPercentage=75.0   — cap heap at 75% of the container's memory limit
#   urandom                 — avoid /dev/random blocking in containers during SecureRandom init
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
