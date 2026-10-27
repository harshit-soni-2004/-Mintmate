# Multi-stage build for SpendSense Spring Boot API
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spendsense && adduser -S spendsense -G spendsense

COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/data/documents && chown -R spendsense:spendsense /app

USER spendsense:spendsense

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
