package com.thesis.cloudsim.configurator;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;

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
     * Creates a Datacenter pre-populated with Hosts.
     * We avoid advanced Streams – a simple loop builds each Host so the code is
     * 100 % traceable by beginners.
     */
    public Datacenter configureDatacenter(String name) {
        try {
            // Create DatacenterCharacteristics
            String arch = "x86";
            String os = "Linux";
            String vmm = "Xen";
            double time_zone = 10.0;
            double cost = 3.0;
            double costPerMem = 0.05;
            double costPerStorage = 0.001;
            double costPerBw = 0.0;
            
            List<Host> hostList = buildHosts();
            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);
            
            // Create Datacenter with VmAllocationPolicy and empty storage list
            List<Storage> storageList = new ArrayList<>();
            Datacenter datacenter = new Datacenter(name, characteristics, 
                new VmAllocationPolicySimple(hostList), storageList, 1.0);
            
            return datacenter;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Creates hosts for the datacenter.
     */
private List<Host> buildHosts() {
        List<Host> hosts = new ArrayList<>();

        // Build each Host one-by-one
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < numPesPerHost; p++) {
                peList.add(new Pe(p, new PeProvisionerSimple(peMips))); // Processing element with ID and provisioner
            }
            // Host with ID, provisioners, storage, PE list and VM scheduler
            Host host = new Host(
                i, // host ID
                new RamProvisionerSimple(ramPerHost),
                new BwProvisionerSimple(bwPerHost),
                storagePerHost,
                peList,
                new VmSchedulerTimeShared(peList)
            );
            hosts.add(host);
        }
        return hosts;
    }

private List<Vm> buildVms() {
        List<Vm> vms = new ArrayList<>();
        String vmm = "Xen"; // Virtual Machine Monitor
        for (int i = 0; i < numVMs; i++) {
            // Vm constructor: id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler
            Vm vm = new Vm(
                i, // VM ID
                -1, // User ID (will be set by broker)
                vmMips,
                vmPes,
                vmRam,
                vmBw,
                vmSize,
                vmm,
                new CloudletSchedulerTimeShared()
            );
            vms.add(vm);
        }
        return vms;
    }

    /**
     * Public factory that returns the list of Hosts for external callers.
     */
    public List<Host> createHosts() {
        return buildHosts();
    }

    /**
     * Public factory that returns the list of VMs for external callers.
     */
    public List<Vm> createVms() {
        return buildVms();
    }
    
    /**
     * Creates VMs with a specific broker ID
     */
    public List<Vm> createVms(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        String vmm = "Xen"; // Virtual Machine Monitor
        for (int i = 0; i < numVMs; i++) {
            Vm vm = new Vm(
                i, // VM ID
                brokerId, // User ID (broker ID)
                vmMips,
                vmPes,
                vmRam,
                vmBw,
                vmSize,
                vmm,
                new CloudletSchedulerTimeShared()
            );
            vms.add(vm);
        }
        return vms;
    }
}

