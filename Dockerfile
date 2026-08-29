# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/marketdata-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Docker's exec-form CMD does not expand environment variables. Use a shell so
# Render's PORT value is passed to Spring Boot at runtime.
CMD ["sh", "-c", "exec java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
