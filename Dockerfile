# Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# -ntp keeps the log readable but still shows errors; retries ride out flaky downloads.
RUN ./mvnw -B -ntp -Daether.connector.http.retryHandler.count=5 dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package && cp target/test-task-*.jar /app.jar

# Run
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
