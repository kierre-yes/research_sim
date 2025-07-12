package com.thesis.cloudsim.simulation;

import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.broker.CustomSchedulingBroker;
import com.thesis.cloudsim.metrics.MetricsCalculator;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.utils.DatasetUtils;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimulationManager {

    private final ISchedulingAlgorithm algorithm;
    private final String workloadPath;

    public SimulationManager(ISchedulingAlgorithm algorithm, String workloadPath) {
        this.algorithm = algorithm;
        this.workloadPath = workloadPath;
    }

    public SimulationResults run() throws IOException {
        CloudSim simulation = new CloudSim();

        Datacenter datacenter = createDatacenter(2, simulation); // default 2 hosts
        List<Vm> vmList = createVms(5);                          // default 5 VMs

        List<Cloudlet> cloudlets = new DatasetUtils().loadWorkload(workloadPath);

        CustomSchedulingBroker broker = new CustomSchedulingBroker(simulation,
                algorithm.getAlgorithmName(), new AlgorithmParameters());

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudlets);

        simulation.startSimulation();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        MetricsCalculator calculator = new MetricsCalculator(simulation, vmList, datacenter, finished);
        return calculator.buildResults();
    }

    private Datacenter createDatacenter(int hostCount, CloudSim simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = List.of(new PeSimple(1000)); // 1 core, 1000 MIPS each
            Host host = new HostSimple(2048, 10000, 1_000_000, peList);
            host.setRamProvisioner(new ResourceProvisionerSimple());
            host.setBwProvisioner(new ResourceProvisionerSimple());
            host.setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelLinear(100, 0.7));
            hostList.add(host);
        }
        // Cast CloudSim to Simulation interface for DatacenterSimple constructor
        return new DatacenterSimple((Simulation) simulation, hostList);
    }

    private List<Vm> createVms(int count) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new VmSimple(1000, 1);
            vm.setRam(512).setBw(1000).setSize(10_000);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vms.add(vm);
        }
        return vms;
    }
}
