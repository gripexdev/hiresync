# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Cache dependencies before copying source
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -q

# Build
COPY src src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Ubuntu (jammy) — required for Playwright's own Chromium binary (glibc)
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Playwright Chromium runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libdrm2 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libxshmfence1 \
    libx11-xcb1 \
    libxfixes3 \
    libxkbcommon0 \
    fonts-liberation \
    wget \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p uploads/cvs

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# Playwright downloads Chromium to ~/.cache/ms-playwright on first startup.
# Mount a named volume there (see docker-compose.yml) so it only downloads once.
ENTRYPOINT ["java", "-jar", "app.jar"]
