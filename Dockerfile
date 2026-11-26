# Runtime image. The frontend is pre-built into src/main/resources/static, so no Node.js is needed here.
# For production, pin the base images by digest.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 cts
WORKDIR /app
COPY --from=build /src/target/crypto-trading-system-*.jar app.jar
USER cts
EXPOSE 8080
# All configuration and secrets come from environment variables (see .env.example).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
