package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.broker.CustomSchedulingBroker;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.MetricsCalculator;
import com.thesis.cloudsim.configurator.DataCenterConfigurator;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.utils.DatasetUtils;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnhancedSimulationManager {

    private final ISchedulingAlgorithm algorithm;
    private final SimulationRequest request;

    public EnhancedSimulationManager(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        this.algorithm = algorithm;
        this.request = request;
    }

    public SimulationResults run() throws IOException {
        CloudSim simulation = new CloudSim();

        // Use DataCenterConfigurator to build infrastructure per class diagram
        DataCenterConfigurator configurator = new DataCenterConfigurator(
                request.getNumHosts(),
                request.getNumPesPerHost(),
                request.getPeMips(),
                request.getRamPerHost(),
                request.getBwPerHost(),
                request.getStoragePerHost(),
                request.getNumVMs(),
                request.getVmMips(),
                request.getVmPes(),
                request.getVmRam(),
                request.getVmBw(),
                request.getVmSize());

        Datacenter datacenter = configurator.configureDatacenter(simulation);
        List<Vm> vmList = configurator.configureVMs();

        // Load or generate cloudlets based on configuration
        List<Cloudlet> cloudlets = loadCloudlets();

        // Create broker with the selected algorithm
        CustomSchedulingBroker broker = new CustomSchedulingBroker(simulation,
                algorithm.getAlgorithmName(), new AlgorithmParameters());

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudlets);

        // Start simulation
        simulation.startSimulation();

        // Calculate and return results
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        MetricsCalculator calculator = new MetricsCalculator(simulation, vmList, datacenter, finished);
        return calculator.buildResults();
    }

    private List<Cloudlet> loadCloudlets() throws IOException {
        if (request.getWorkloadPath() != null && !request.isUseDefaultWorkload()) {
            // Load from uploaded CSV file
            return new DatasetUtils().loadWorkload(request.getWorkloadPath());
        } else {
            // Generate synthetic workload
            return generateSyntheticWorkload();
        }
    }

    private List<Cloudlet> generateSyntheticWorkload() {
        List<Cloudlet> cloudlets = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < request.getNumCloudlets(); i++) {
            // Generate random cloudlet parameters
            long length = 1000 + random.nextInt(9000); // 1000-10000 MI
            int pesNumber = 1 + random.nextInt(2); // 1-2 PEs
            
            CloudletSimple cloudlet = new CloudletSimple(length, pesNumber);
            cloudlet.setFileSize(300 + random.nextInt(200)); // 300-500 bytes
            cloudlet.setOutputSize(300 + random.nextInt(200)); // 300-500 bytes
            cloudlet.setUtilizationModel(new UtilizationModelDynamic());
            
            cloudlets.add(cloudlet);
        }
        
        return cloudlets;
    }
}
