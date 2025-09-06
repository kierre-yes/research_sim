FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy the entire project structure
COPY pom.xml .
COPY modules/ modules/

# Create a dummy MATLAB jar to satisfy the dependency (since we won't use MATLAB in Railway)
RUN mkdir -p /tmp/matlab && \
    echo "Manifest-Version: 1.0" > /tmp/matlab/manifest.txt && \
    jar cfm /tmp/matlab/engine.jar /tmp/matlab/manifest.txt

# Build with sufficient memory for Maven
ENV MAVEN_OPTS="-Xmx1024m -Xms512m"

# Build all modules in correct order with the dummy MATLAB jar
RUN mvn clean install -DskipTests -pl modules/cloudsim -am && \
    mvn clean install -DskipTests -pl modules/cloudsim-examples -am && \
    mvn clean install -DskipTests -pl modules/cloudsim-simulation-backend -am \
    -Dmatlab.engine.jar=/tmp/matlab/engine.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the built JAR
COPY --from=build /app/modules/cloudsim-simulation-backend/target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar app.jar

# Create non-root user for security
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser && \
    chown -R appuser:appuser /app

USER appuser

# Railway uses PORT environment variable
EXPOSE ${PORT:-8081}

# JVM optimization for 10K tasks on Railway (512MB limit)
# Based on your successful test showing ~326MB usage
ENV JAVA_OPTS="-Xmx400m -Xms256m \
    -XX:MaxMetaspaceSize=80m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dserver.port=${PORT:-8081} \
    -Dmatlab.enabled=false"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8081}/actuator/health || exit 1

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
