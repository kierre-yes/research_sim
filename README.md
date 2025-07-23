# CloudSim Load Balancing Backend with EPSO & EACO Algorithms

This is an enhanced CloudSim 7.0 backend implementation featuring Enhanced Particle Swarm Optimization (EPSO) and Enhanced Ant Colony Optimization (EACO) algorithms for cloud task scheduling. The system includes MATLAB integration for advanced visualization and supports multi-objective optimization for makespan, cost, energy consumption, and load balancing.

## 🚀 Features

- **Enhanced Scheduling Algorithms**: EPSO and EACO implementations with multi-objective optimization
- **RESTful API**: Spring Boot backend for easy integration with frontend applications
- **MATLAB Visualization**: Advanced plots and analysis through MATLAB integration
- **Real-time Metrics**: Comprehensive performance metrics including makespan, cost, energy, and load balance
- **Scalable Architecture**: Supports various datacenter configurations and workload types

## 📋 Prerequisites

- Java JDK 21 or higher
- Maven 3.8+
- MATLAB R2021a or higher (optional, for advanced visualizations)
- 8GB RAM minimum (16GB recommended)
- Windows 10/11, Linux, or macOS

## 🛠️ Backend Setup

### Step 1: Clone the Repository
```bash
git clone https://github.com/kierre-yes/research_sim.git
cd cloudsim-7.0
```
    
### Step 2: Install Java JDK 21

**Windows:**
1. Download JDK 21 from [Oracle's official website](https://www.oracle.com/java/technologies/downloads/#java21)
2. Run the installer and follow the installation wizard
3. Add Java to your PATH environment variable
4. Verify installation:
   ```cmd
   java -version
   ```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo update-java-alternatives --set java-1.21.0-openjdk-amd64
java -version
```

### Step 3: Install Maven

**Windows:**
1. Download Maven from [Apache Maven website](https://maven.apache.org/download.cgi)
2. Extract to a directory (e.g., `C:\Program Files\Apache\maven`)
3. Add Maven's `bin` directory to your PATH
4. Verify installation:
   ```cmd
   mvn -version
   ```

**Linux:**
```bash
sudo apt install maven
mvn -version
```

### Step 4: Build the Backend
```bash
cd modules/cloudsim-simulation-backend
mvn clean install
```

### Step 5: Run the Backend Server
```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8081`

## 🧮 MATLAB Setup (Optional but Recommended)

### Step 1: Install MATLAB
1. Download MATLAB from [MathWorks website](https://www.mathworks.com/downloads/)
2. Install with the following toolboxes:
   - MATLAB Compiler
   - MATLAB Engine API for Java
   - Statistics and Machine Learning Toolbox (optional)

### Step 2: Configure MATLAB Engine for Java

**Windows:**
1. Open Command Prompt as Administrator
2. Navigate to MATLAB root directory:
   ```cmd
   cd "C:\Program Files\MATLAB\R2023b\extern\engines\java"
   ```
3. Install the MATLAB Engine:
   ```cmd
   java -cp .\engine.jar com.mathworks.engine.MatlabEngine
   ```

**Linux:**
```bash
cd /usr/local/MATLAB/R2023b/extern/engines/java
sudo java -cp ./engine.jar com.mathworks.engine.MatlabEngine
```

### Step 3: Set MATLAB Environment Variables

**Windows:**
1. Add to System Environment Variables:
   - Variable: `MATLAB_ROOT`
   - Value: `C:\Program Files\MATLAB\R2023b`
   
2. Add to PATH:
   - `%MATLAB_ROOT%\bin`
   - `%MATLAB_ROOT%\extern\engines\java\lib`

**Linux:**
Add to `~/.bashrc` or `~/.zshrc`:
```bash
export MATLAB_ROOT=/usr/local/MATLAB/R2023b
export PATH=$PATH:$MATLAB_ROOT/bin
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$MATLAB_ROOT/extern/engines/java/lib
```

### Step 4: Start MATLAB Shared Session

For better performance, start a shared MATLAB session before running the backend:

**Windows:**
```matlab
% In MATLAB Command Window
matlab.engine.shareEngine('thesisEngine')
```

**Linux:**
```bash
matlab -r "matlab.engine.shareEngine('thesisEngine')"
```

### Step 5: Configure Backend for MATLAB

The backend will automatically detect and use MATLAB if available. To disable MATLAB integration, set in `application.properties`:
```properties
matlab.enabled=false
```

## 📡 API Endpoints

### Run Simulation
```http
POST /api/run
Content-Type: application/json

{
  "optimizationAlgorithm": "EPSO",
  "numHosts": 10,
  "numVMs": 20,
  "numCloudlets": 100,
  "makespanWeight": 0.25,
  "costWeight": 0.25,
  "energyWeight": 0.25,
  "loadBalanceWeight": 0.25
}
```

### Run Simulation with MATLAB Plots
```http
POST /api/simulation/with-plots
Content-Type: application/json
```

## 🧪 Testing the Setup

### Test Basic Functionality
```bash
# Run unit tests
mvn test

# Run a simple simulation
curl -X POST http://localhost:8081/api/run \
  -H "Content-Type: application/json" \
  -d '{"optimizationAlgorithm": "EPSO", "numHosts": 5, "numVMs": 10, "numCloudlets": 50}'
```

### Test MATLAB Integration
```bash
curl -X POST http://localhost:8081/api/simulation/with-plots \
  -H "Content-Type: application/json" \
  -d '{"optimizationAlgorithm": "EACO", "numHosts": 10, "numVMs": 20, "numCloudlets": 100}'
```

## 🐛 Troubleshooting

### Common Issues

1. **MATLAB Engine Connection Failed**
   - Ensure MATLAB is installed and licensed
   - Check if MATLAB Engine for Java is properly installed
   - Verify environment variables are set correctly
   - Try starting a shared MATLAB session manually

2. **Out of Memory Errors**
   - Increase JVM heap size in Maven:
     ```bash
     export MAVEN_OPTS="-Xmx4g -Xms2g"
     ```

3. **Port Already in Use**
   - Change the port in `application.properties`:
     ```properties
     server.port=8082
     ```

4. **VM Allocation Failed**
   - Ensure numVMs ≤ numHosts × 2 (default configuration)
   - Adjust host resources in the simulation request

### Debug Mode

Enable debug logging by adding to `application.properties`:
```properties
logging.level.com.thesis.cloudsim=DEBUG
```

## 📊 Understanding the Metrics

- **Makespan**: Total time to complete all tasks
- **Response Time**: Average time from task submission to completion
- **Resource Utilization**: Percentage of available resources being used
- **Energy Consumption**: Total energy consumed (in Wh)
- **Load Balance**: Distribution of tasks across VMs (0-100%, higher is better)
- **Fitness Value**: Multi-objective optimization score

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

# Preferred Publication #
  * Remo Andreoli, Jie Zhao, Tommaso Cucinotta, and Rajkumar Buyya, [CloudSim 7G: An Integrated Toolkit for Modeling and Simulation of Future Generation Cloud Computing Environments](https://onlinelibrary.wiley.com/doi/10.1002/spe.3413), Software: Practice and Experience, 2025.
    
# Publications (Legacy) #

  * Anton Beloglazov, and Rajkumar Buyya, [Optimal Online Deterministic Algorithms and Adaptive Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in Cloud Data Centers](http://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf), Concurrency and Computation: Practice and Experience, Volume 24, Number 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012.
  * Saurabh Kumar Garg and Rajkumar Buyya, [NetworkCloudSim: Modelling Parallel Applications in Cloud Simulations](http://www.cloudbus.org/papers/NetworkCloudSim2011.pdf), Proceedings of the 4th IEEE/ACM International Conference on Utility and Cloud Computing (UCC 2011, IEEE CS Press, USA), Melbourne, Australia, December 5-7, 2011.
  * **Rodrigo N. Calheiros, Rajiv Ranjan, Anton Beloglazov, Cesar A. F. De Rose, and Rajkumar Buyya, [CloudSim: A Toolkit for Modeling and Simulation of Cloud Computing Environments and Evaluation of Resource Provisioning Algorithms](http://www.buyya.com/papers/CloudSim2010.pdf), Software: Practice and Experience (SPE), Volume 41, Number 1, Pages: 23-50, ISSN: 0038-0644, Wiley Press, New York, USA, January, 2011. (Seminal paper)**
  * Bhathiya Wickremasinghe, Rodrigo N. Calheiros, Rajkumar Buyya, [CloudAnalyst: A CloudSim-based Visual Modeller for Analysing Cloud Computing Environments and Applications](http://www.cloudbus.org/papers/CloudAnalyst-AINA2010.pdf), Proceedings of the 24th International Conference on Advanced Information Networking and Applications (AINA 2010), Perth, Australia, April 20-23, 2010.
  * Rajkumar Buyya, Rajiv Ranjan and Rodrigo N. Calheiros, [Modeling and Simulation of Scalable Cloud Computing Environments and the CloudSim Toolkit: Challenges and Opportunities](http://www.cloudbus.org/papers/CloudSim-HPCS2009.pdf), Proceedings of the 7th High Performance Computing and Simulation Conference (HPCS 2009, ISBN: 978-1-4244-4907-1, IEEE Press, New York, USA), Leipzig, Germany, June 21-24, 2009.




[![](http://www.cloudbus.org/logo/cloudbuslogo-v5a.png)](http://cloudbus.org/)
