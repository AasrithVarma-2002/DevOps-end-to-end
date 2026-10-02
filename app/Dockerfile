# ---- build stage: compile and package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# dependencies first, so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre-alpine
# Fixed numeric UID/GID so Kubernetes can verify runAsNonRoot (the Helm chart runs as 10001)
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app && apk add --no-cache wget
WORKDIR /app
COPY --from=build /src/target/hr-portal.jar app.jar
USER 10001:10001
EXPOSE 8080
# respect the container memory limit; restart cleanly instead of limping on OOM
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
