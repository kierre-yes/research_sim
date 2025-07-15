package com.thesis.cloudsim.configurator;

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

import java.util.ArrayList;
import java.util.List;

/**
 * DataCenterConfigurator is responsible for creating the Datacenter, Hosts and VMs
 * based on user-defined parameters. It encapsulates all configuration logic so
 * SimulationManager focuses only on orchestration.
 */
public class DataCenterConfigurator {

    private final int numHosts;
    private final int numPesPerHost;
    private final int peMips;
    private final int ramPerHost;
    private final int bwPerHost;
    private final int storagePerHost;

    private final int numVMs;
    private final int vmMips;
    private final int vmPes;
    private final int vmRam;
    private final int vmBw;
    private final int vmSize;

    public DataCenterConfigurator(int numHosts, int numPesPerHost, int peMips, int ramPerHost, int bwPerHost, int storagePerHost,
                                  int numVMs, int vmMips, int vmPes, int vmRam, int vmBw, int vmSize) {
        this.numHosts = numHosts;
        this.numPesPerHost = numPesPerHost;
        this.peMips = peMips;
        this.ramPerHost = ramPerHost;
        this.bwPerHost = bwPerHost;
        this.storagePerHost = storagePerHost;
        this.numVMs = numVMs;
        this.vmMips = vmMips;
        this.vmPes = vmPes;
        this.vmRam = vmRam;
        this.vmBw = vmBw;
        this.vmSize = vmSize;
    }

    /**
     * Creates a Datacenter pre-populated with Hosts configured per constructor parameters.
     */
    public Datacenter configureDatacenter(CloudSim sim) {
        return new DatacenterSimple((Simulation) sim, configureHosts());
    }

    /**
     * Creates hosts for the datacenter.
     */
    public List<Host> configureHosts() {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < numPesPerHost; p++) {
                peList.add(new PeSimple(peMips));
            }
            Host host = new HostSimple(ramPerHost, bwPerHost, storagePerHost, peList);
            host.setRamProvisioner(new ResourceProvisionerSimple());
            host.setBwProvisioner(new ResourceProvisionerSimple());
            host.setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelLinear(100, 0.7));
            hosts.add(host);
        }
        return hosts;
    }

    /**
     * Creates VMs configured per constructor parameters.
     */
    public List<Vm> configureVMs() {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < numVMs; i++) {
            Vm vm = new VmSimple(vmMips, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmSize);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vms.add(vm);
        }
        return vms;
    }
}

