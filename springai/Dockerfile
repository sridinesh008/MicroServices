# Stage 1: build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: run — minimal JRE, non-root, no shell tooling beyond what Alpine's base carries
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 spring && adduser -D -u 10001 -G spring spring
WORKDIR /app
COPY --from=builder /app/target/springai-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R spring:spring /app

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
