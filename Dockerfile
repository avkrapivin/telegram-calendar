# Builder stage
FROM maven:3.9-eclipse-temurin-17 AS builder
# Working directory inside the container
WORKDIR /app
# Copy only pom.xml to cache the dependencies layers
COPY pom.xml .
# Copy sources
COPY src ./src
# Build jar (equivalent to mvn clean package -DskipTests)
RUN mvn clean package -DskipTests

# Expected that the jar will be named telegram-calendar-1.0-SNAPSHOT.jar

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
# Working directory for the application
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /app/target/telegram-calendar-1.0-SNAPSHOT.jar app.jar
# Port (the bot itself does not listen to HTTP, but EXPOSE is not critical)
EXPOSE 8080
# Entry point: simply run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]