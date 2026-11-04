# Stage 1: build the web app (frontend/)
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# Public Clerk key, baked into the bundle (pk_live_… in production). The API URL is /api (frontend/.env.production).
ARG VITE_CLERK_PUBLISHABLE_KEY
RUN test -n "$VITE_CLERK_PUBLISHABLE_KEY" && npm run build

# Stage 2: build the jar with the web app inside (served from classpath:/static)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:25-jre
# curl for the health check (Coolify's health check also needs curl or wget inside the image)
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER ubuntu
EXPOSE 8088
HEALTHCHECK --interval=30s --start-period=40s CMD curl -fs http://localhost:8088/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
