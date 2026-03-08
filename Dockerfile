# Build stage (use Debian-based image to avoid Gradle native crash on Alpine/ARM)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -r -u 1000 -m appuser

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown appuser /app/app.jar
USER appuser

EXPOSE 3001
ENV PORT=3001
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
