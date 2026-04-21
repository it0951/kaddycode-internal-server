# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S kaddycode && adduser -S kaddycode -G kaddycode
COPY --from=builder /build/target/kaddycode-internal-server-0.0.1-SNAPSHOT.jar app.jar
RUN chown kaddycode:kaddycode app.jar
USER kaddycode
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8081/api/v1/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
