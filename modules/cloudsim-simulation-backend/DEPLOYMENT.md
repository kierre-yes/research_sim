# CloudSim Simulation Backend Deployment Guide

This guide covers deployment options for the CloudSim simulation backend with MATLAB integration.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Docker Deployment](#docker-deployment)
4. [Cloud Deployment](#cloud-deployment)
5. [MATLAB Integration](#matlab-integration)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose (for containerized deployment)
- MATLAB R2025a (for visualization features)

### System Requirements
- Minimum 4GB RAM (8GB recommended for large simulations)
- 2+ CPU cores
- 10GB free disk space

## Local Development

### 1. Build the Application

```bash
# Clone the repository
git clone <your-repo-url>
cd cloudsim-7.0/modules/cloudsim-simulation-backend

# Build with Maven
mvn clean package

# Or build without tests for faster compilation
mvn clean package -DskipTests
```

### 2. Run Locally

```bash
# Run with default profile
java -jar target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar

# Run with production profile
java -jar target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod

# Run with custom memory settings
java -Xms1g -Xmx4g -jar target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar
```

### 3. Access the Application
- API endpoint: http://localhost:8081
- Health check: http://localhost:8081/actuator/health

## Docker Deployment

### 1. Build Docker Image

```bash
# Build the image
docker build -t cloudsim-backend:latest .

# Or use docker-compose
docker-compose build
```

### 2. Run with Docker Compose

```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f cloudsim-backend

# Stop the application
docker-compose down
```

### 3. Run with Docker (standalone)

```bash
# Run the container
docker run -d \
  --name cloudsim-backend \
  -p 8081:8081 \
  -v $(pwd)/plots:/app/plots \
  -v $(pwd)/logs:/app/logs \
  -e SPRING_PROFILES_ACTIVE=prod \
  cloudsim-backend:latest

# View logs
docker logs -f cloudsim-backend

# Stop and remove container
docker stop cloudsim-backend && docker rm cloudsim-backend
```

## Cloud Deployment

### Option 1: Deploy to AWS EC2

1. **Launch EC2 Instance**
   - Amazon Linux 2 or Ubuntu 22.04
   - Instance type: t3.large or better
   - Security group: Allow ports 22 (SSH), 8081 (API)

2. **Install Dependencies**
   ```bash
   # For Amazon Linux 2
   sudo yum update -y
   sudo yum install docker git -y
   sudo service docker start
   sudo usermod -a -G docker ec2-user

   # Install Docker Compose
   sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   ```

3. **Deploy Application**
   ```bash
   # Clone repository
   git clone <your-repo-url>
   cd cloudsim-7.0/modules/cloudsim-simulation-backend

   # Set environment variables
   export CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com

   # Run with docker-compose
   docker-compose up -d
   ```

### Option 2: Deploy to Azure Container Instances

1. **Build and Push to Azure Container Registry**
   ```bash
   # Login to Azure
   az login
   
   # Create resource group
   az group create --name cloudsim-rg --location eastus
   
   # Create container registry
   az acr create --resource-group cloudsim-rg --name cloudsimregistry --sku Basic
   
   # Build and push image
   az acr build --registry cloudsimregistry --image cloudsim-backend:v1 .
   ```

2. **Deploy to Container Instances**
   ```bash
   az container create \
     --resource-group cloudsim-rg \
     --name cloudsim-backend \
     --image cloudsimregistry.azurecr.io/cloudsim-backend:v1 \
     --cpu 2 \
     --memory 4 \
     --ports 8081 \
     --environment-variables SPRING_PROFILES_ACTIVE=prod
   ```

### Option 3: Deploy to Google Cloud Run

1. **Build and Push to Container Registry**
   ```bash
   # Configure gcloud
   gcloud auth login
   gcloud config set project YOUR_PROJECT_ID
   
   # Build and push
   gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/cloudsim-backend
   ```

2. **Deploy to Cloud Run**
   ```bash
   gcloud run deploy cloudsim-backend \
     --image gcr.io/YOUR_PROJECT_ID/cloudsim-backend \
     --platform managed \
     --port 8081 \
     --memory 4Gi \
     --cpu 2 \
     --set-env-vars SPRING_PROFILES_ACTIVE=prod
   ```

## MATLAB Integration

### Option 1: MATLAB Runtime (Without Full MATLAB)

For production deployments without MATLAB license:

1. **Install MATLAB Runtime**
   - Download from: https://www.mathworks.com/products/compiler/matlab-runtime.html
   - Version: R2025a (must match your MATLAB version)

2. **Compile MATLAB Functions**
   ```matlab
   % In MATLAB, compile your functions
   mcc -m generateComparisonPlots.m -d ./compiled
   ```

3. **Update Dockerfile for Runtime**
   ```dockerfile
   # Add to Dockerfile
   RUN apt-get update && apt-get install -y \
       wget \
       unzip \
       libxt6 \
       libxtst6
   
   # Download and install MATLAB Runtime
   RUN wget -q https://ssd.mathworks.com/supportfiles/downloads/R2025a/MCR_R2025a_glnxa64_installer.zip && \
       unzip -q MCR_R2025a_glnxa64_installer.zip && \
       ./install -mode silent -agreeToLicense yes && \
       rm -rf MCR_R2025a_glnxa64_installer.zip install
   ```

### Option 2: MATLAB Engine API (Development)

For development with full MATLAB:

1. **Start MATLAB Engine**
   ```matlab
   % In MATLAB Command Window
   matlab.engine.shareEngine('thesisEngine')
   ```

2. **Configure Java Application**
   - Ensure MATLAB engine.jar is in classpath
   - Set java.library.path to MATLAB bin directory

### Option 3: Disable MATLAB Features

To run without MATLAB:

1. **Create Configuration**
   ```properties
   # application.properties
   matlab.enabled=false
   ```

2. **Update Service**
   ```java
   @Value("${matlab.enabled:true}")
   private boolean matlabEnabled;
   
   public ProcessedResults processResults(SimulationResults results) {
       if (!matlabEnabled) {
           // Return results without MATLAB processing
           return new ProcessedResults(results, null);
       }
       // ... existing MATLAB code
   }
   ```

## Production Best Practices

### 1. Environment Variables

Create `.env` file for production:
```bash
# API Configuration
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=prod

# CORS Settings
CORS_ALLOWED_ORIGINS=https://your-frontend.com,https://www.your-frontend.com

# Memory Settings
JAVA_OPTS=-Xms1g -Xmx4g -XX:+UseG1GC

# Security (if implementing authentication)
JWT_SECRET=your-secret-key-here
```

### 2. Reverse Proxy with Nginx

```nginx
server {
    listen 80;
    server_name api.your-domain.com;
    
    location / {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 3. SSL/TLS with Let's Encrypt

```bash
# Install certbot
sudo apt-get install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d api.your-domain.com
```

### 4. Monitoring and Logging

1. **Application Metrics**
   - Use Spring Boot Actuator endpoints
   - Integrate with Prometheus/Grafana

2. **Log Aggregation**
   - Configure logback for JSON output
   - Use ELK stack or CloudWatch

### 5. Backup and Recovery

```bash
# Backup simulation results
docker exec cloudsim-backend tar -czf /tmp/backup.tar.gz /app/plots
docker cp cloudsim-backend:/tmp/backup.tar.gz ./backups/

# Backup database (if using)
docker exec cloudsim-db pg_dump -U cloudsim cloudsim > backup.sql
```

## Troubleshooting

### Common Issues

1. **MATLAB Engine Connection Failed**
   ```
   Solution: Ensure MATLAB is running with shared engine
   matlab.engine.shareEngine('thesisEngine')
   ```

2. **Out of Memory Errors**
   ```
   Solution: Increase JVM heap size
   java -Xms2g -Xmx8g -jar app.jar
   ```

3. **CORS Issues**
   ```
   Solution: Update allowed origins in application.properties
   spring.web.cors.allowed-origins=https://your-frontend.com
   ```

4. **Docker Build Fails**
   ```
   Solution: Ensure Docker daemon is running and has enough disk space
   docker system prune -a
   ```

### Health Checks

```bash
# Check application health
curl http://localhost:8081/actuator/health

# Check available endpoints
curl http://localhost:8081/api/simulation/algorithms

# Test simulation
curl -X POST http://localhost:8081/api/simulation/run \
  -H "Content-Type: application/json" \
  -d '{"cloudlets":100,"vms":10,"algorithm":"EPSO"}'
```

## Performance Tuning

### JVM Options for Large Simulations

```bash
java -jar app.jar \
  -Xms4g \
  -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -XX:+DisableExplicitGC \
  -Djava.awt.headless=true
```

### Database Connection Pool (if using database)

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

## Security Considerations

1. **API Authentication** (if needed)
   - Implement JWT authentication
   - Use Spring Security

2. **Rate Limiting**
   - Implement request throttling
   - Use Redis for distributed rate limiting

3. **Input Validation**
   - Validate simulation parameters
   - Limit maximum cloudlets/VMs

4. **Secrets Management**
   - Use environment variables
   - Consider AWS Secrets Manager or Azure Key Vault

## Support and Maintenance

- Monitor application logs regularly
- Set up alerts for errors and performance issues
- Keep dependencies updated
- Regular backup of simulation results

For additional support, check the project documentation or raise an issue in the repository.
