FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy the entire project structure
COPY pom.xml .
COPY modules/ modules/

RUN mkdir -p /tmp/matlab/src/com/mathworks/engine && \
    echo 'package com.mathworks.engine;' > /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo 'import java.util.concurrent.Future;' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo 'import java.util.concurrent.CancellationException;' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo 'import java.util.concurrent.ExecutionException;' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo 'public class MatlabEngine implements AutoCloseable {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public static Future<MatlabEngine> startMatlabAsync() throws Exception {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public static MatlabEngine startMatlab() throws Exception {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public static MatlabEngine startMatlab(String[] options) throws Exception {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public Future<Void> evalAsync(String command) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public void eval(String command) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public <T> Future<T> getVariableAsync(String name) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public <T> T getVariable(String name) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public Future<Void> putVariableAsync(String name, Object value) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public void putVariable(String name, Object value) {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '        throw new UnsupportedOperationException("MATLAB not available in this environment");' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public void close() {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public void disconnect() {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    public void quit() {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '    }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo '}' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngine.java && \
    echo 'package com.mathworks.engine;' > /tmp/matlab/src/com/mathworks/engine/MatlabEngineException.java && \
    echo 'public class MatlabEngineException extends Exception {' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngineException.java && \
    echo '    public MatlabEngineException(String message) { super(message); }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngineException.java && \
    echo '    public MatlabEngineException(String message, Throwable cause) { super(message, cause); }' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngineException.java && \
    echo '}' >> /tmp/matlab/src/com/mathworks/engine/MatlabEngineException.java && \
    javac -d /tmp/matlab/classes /tmp/matlab/src/com/mathworks/engine/*.java && \
    jar cf /tmp/matlab/engine.jar -C /tmp/matlab/classes .

RUN sed -i 's|<matlab.engine.jar>.*</matlab.engine.jar>|<matlab.engine.jar>/tmp/matlab/engine.jar</matlab.engine.jar>|' \
    modules/cloudsim-simulation-backend/pom.xml

# Build with sufficient memory for Maven
ENV MAVEN_OPTS="-Xmx1024m -Xms512m"

# Build all modules in correct order
RUN mvn clean install -DskipTests -pl modules/cloudsim -am && \
    mvn clean install -DskipTests -pl modules/cloudsim-examples -am && \
    mvn clean install -DskipTests -pl modules/cloudsim-simulation-backend -am

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
