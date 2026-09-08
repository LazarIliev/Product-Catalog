# ---- build stage -------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy the build definition first so the dependency layer is cached and only re-resolved when the
# build files actually change, not on every source edit.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
# Tests need a Docker daemon (Testcontainers) and are run in CI, not in the image build.
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
# Container-aware heap sizing; the rest of the config comes from environment variables.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
