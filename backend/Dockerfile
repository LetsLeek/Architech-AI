# syntax=docker/dockerfile:1
#
# Multi-stage build (AIW-68): the build stage has the full JDK + Maven wrapper and never
# ships - only the runtime stage's JRE-only image is what actually gets pushed/deployed. Build
# context is this backend/ directory itself (see docker-compose.yml / CI for exact invocation);
# nothing outside it (in particular the repo root's .env) is ever visible to either stage.

# --- Build stage ---------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies resolve in their own layer, cached as long as the wrapper/pom.xml don't change -
# an unrelated source edit doesn't force re-downloading the world on the next local build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests already run as their own CI job (backend-ci.yml) against a real Postgres service this
# build stage doesn't have - skipped here to avoid a redundant, Postgres-less second run.
RUN ./mvnw -B -q package -DskipTests

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# AIW-96: the base image's own OS packages (openssl, expat, ...) lag behind Alpine's current
# security advisories at whatever point eclipse-temurin last published this tag - pulling
# current patched versions at build time is what actually closes those CVEs, not just waiting
# for the next upstream base-image refresh. --no-cache skips a persisted apk index, keeping
# the image the same size it'd otherwise be.
RUN apk update && apk upgrade --no-cache

# Passed as --build-arg GIT_SHA=$(git rev-parse HEAD) - makes the exact commit an image was
# built from inspectable (`docker inspect`) independently of whatever tag it later carries,
# which can be reassigned or lost. Defaults to "unknown" so a plain `docker build .` with no
# build-arg still succeeds locally.
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.source="https://github.com/LetsLeek/Architech-AI"

# Runs as a dedicated, unprivileged user - never root.
RUN addgroup -S backend && adduser -S backend -G backend
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown backend:backend app.jar
USER backend

EXPOSE 8080

# Alpine's busybox wget stands in for curl, which this base image doesn't carry. Matches the
# same /actuator/health this platform's own docs/README already point at for a running backend.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
