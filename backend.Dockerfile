# =============================================================================
# Multi-stage build for the ScanMeIn Spring Boot backend (Java 25).
#
# Stage 1 (build): uses the Temurin 25 JDK and the project's Maven wrapper to
#                  compile and package the application, skipping tests.
# Stage 2 (run):   a slim Temurin 25 JRE image that only contains the
#                  executable jar.
#
# Secrets are NOT baked into the image; they are injected at runtime via
# environment variables (see compose.yaml and application-docker.properties).
# The gitignored local application.properties is excluded via .dockerignore.
# =============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and pom first so that dependencies are downloaded in
# their own layer and cached between builds (they only re-download when the
# pom or wrapper change).
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline || true

# Copy the source and build the application (skipping tests).
COPY . .
RUN ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the executable jar produced by the build stage.
COPY --from=build /workspace/target/webapp-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
