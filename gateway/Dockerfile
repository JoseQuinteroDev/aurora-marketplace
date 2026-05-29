# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
# curl backs the container HEALTHCHECK used by docker compose for ordered startup.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Run as an unprivileged user (defense in depth: a compromised process is not root).
RUN groupadd --system --gid 1001 aurora \
    && useradd --system --uid 1001 --gid aurora --no-create-home aurora
WORKDIR /app
COPY --from=build --chown=aurora:aurora /workspace/target/gateway-0.0.1-SNAPSHOT.jar app.jar
USER aurora
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
