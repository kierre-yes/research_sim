<a href="#">
  <img width="1024" alt="Cloud Load Balancer Simulator preview" src="https://github.com/user-attachments/assets/f719a4d1-2d0b-4080-8203-032f951600d9" />
</a>

<p align="center">
  <a href="#about">About</a>
  ·
  <a href="#installation">Installation</a>
  ·
  <a href="#matlab-integration">MATLAB Integration</a>
  ·
  <a href="#api-endpoints">API</a>
  ·
  <a href="#troubleshooting">Troubleshooting</a>
  ·
  <a href="#publications">Publications</a>
</p>

<h1></h1>

## About

CloudSim Load Balancing Backend built on Spring Boot (Java 21) with Enhanced Particle Swarm Optimization (EPSO) and Enhanced Ant Colony Optimization (EACO) for cloud task scheduling. Includes optional MATLAB-powered visualization and statistical analysis.

Key capabilities:
* EPSO and EACO scheduling with multi-objective optimization
* RESTful API for simulations and chart generation
* MATLAB-driven plots and statistical tests (paired t-test) when enabled
* Metrics: makespan, response time, resource utilization, energy, load balance

## Installation

Prerequisites:
* Java 21, Maven 3.8+
* Windows 10/11, Linux, or macOS (Windows recommended for MATLAB Engine)
* MATLAB R2025a (only if using MATLAB Engine)

Clone and build:
```bash
git clone https://github.com/kierre-yes/research_sim.git
cd cloudsim-7.0/modules/cloudsim-simulation-backend
mvn clean package -DskipTests
```

Run (default port 8081):
```bash
java -jar target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar
```

## Features

- Enhanced Scheduling Algorithms: EPSO and EACO
- RESTful API (Spring Boot)
- MATLAB Visualization and statistics (optional)
- Real-time Metrics and scalable architecture

## MATLAB Integration

If you need MATLAB features, install MATLAB R2025a and the MATLAB Engine for Java on Windows. Ensure these are on PATH:
- `C:\\Program Files\\MATLAB\\R2025a\\bin\\win64`
- `C:\\Program Files\\MATLAB\\R2025a\\extern\\engines\\java\\win64`

Run with explicit library path:
```bash
java -Djava.library.path="C:\\Program Files\\MATLAB\\R2025a\\bin\\win64;C:\\Program Files\\MATLAB\\R2025a\\extern\\engines\\java\\win64" \
  -jar target/cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar
```

Place MATLAB .m scripts on disk and add them to MATLAB path at runtime. The app expects to call functions such as `generateComparisonPlots` and `pairedTTest`.

## API Endpoints

Run Simulation:
```http
POST /api/run
Content-Type: application/json
```

Run Simulation with MATLAB Plots:
```http
POST /api/simulation/with-plots
Content-Type: application/json
```

## Testing

```bash
mvn test
```

Sample simulation:
```bash
curl -X POST http://localhost:8081/api/run \
  -H "Content-Type: application/json" \
  -d '{"optimizationAlgorithm":"EPSO","numHosts":5,"numVMs":10,"numCloudlets":50}'
```

## Troubleshooting

Common issues:
1. MATLAB Engine connection failed → verify MATLAB install, PATH, and version.
2. Out of memory → set `MAVEN_OPTS=-Xmx4g -Xms2g` or adjust JVM memory.
3. Port already in use → set `server.port=8082` in properties.

Enable debug logs:
```properties
logging.level.com.thesis.cloudsim=DEBUG
```

## Publications

- Preferred: Remo Andreoli, Jie Zhao, Tommaso Cucinotta, and Rajkumar Buyya, CloudSim 7G: An Integrated Toolkit for Modeling and Simulation of Future Generation Cloud Computing Environments, Software: Practice and Experience, 2025.
- Rodrigo N. Calheiros et al., CloudSim: A Toolkit for Modeling and Simulation of Cloud Computing Environments, SPE 2011.
- Additional references listed in CloudBus publications.

<p align="center">
  <a href="http://cloudbus.org/"><img src="http://www.cloudbus.org/logo/cloudbuslogo-v5a.png" alt="Cloudbus" /></a>
</p>
