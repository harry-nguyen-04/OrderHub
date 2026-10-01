# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom trước để tận dụng Docker cache
COPY pom.xml .

RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests


# ===== Runtime stage =====
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy file JAR từ build stage
COPY --from=builder /app/target/*.jar app.jar

# Port của Spring Boot
EXPOSE 8080

# Chạy application
ENTRYPOINT ["java", "-jar", "app.jar"]