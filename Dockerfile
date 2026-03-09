FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Install unzip; install Gradle (wrapper jar is not in repo due to .gitignore *.jar)
RUN apt-get update && apt-get install -y --no-install-recommends unzip ca-certificates && rm -rf /var/lib/apt/lists/*
RUN set -eux; \
  v="8.7"; \
  curl -sSL "https://services.gradle.org/distributions/gradle-${v}-bin.zip" -o /tmp/gradle.zip; \
  unzip -q /tmp/gradle.zip -d /opt; \
  ln -sf "/opt/gradle-${v}/bin/gradle" /usr/local/bin/gradle; \
  rm /tmp/gradle.zip
COPY . .
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
EXPOSE 10000
# Use Render profile so app picks up DATABASE_JDBC_URL, DATABASE_USERNAME, DATABASE_PASSWORD at runtime
ENV SPRING_PROFILES_ACTIVE=render
ENV PORT=10000
# Copy the runnable boot JAR (version from build.gradle.kts)
ARG JAR_VERSION=1.0.0
COPY --from=build /app/build/libs/ai-crm-api-${JAR_VERSION}.jar app.jar
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
