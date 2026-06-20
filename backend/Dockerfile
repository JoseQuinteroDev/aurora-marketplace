# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:25-jre
# curl backs the container HEALTHCHECK used by docker compose for ordered startup.
# Pinning apt versions (DL3008) is impractical on Debian's rolling repo — old package
# versions are garbage-collected from the mirror, which would break reproducible builds.
# hadolint ignore=DL3008
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Run as an unprivileged user (defense in depth: a compromised process is not root).
RUN groupadd --system --gid 1001 aurora \
    && useradd --system --uid 1001 --gid aurora --no-create-home aurora
WORKDIR /app
COPY --from=build --chown=aurora:aurora /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER aurora
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
