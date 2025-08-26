package com.thesis.cloudsim.configurator;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;

import java.util.ArrayList;
import java.util.List;

// Handles creation of Datacenter, Hosts, and VMs
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

    // Creates a Datacenter with Hosts
    public Datacenter configureDatacenter(String name) {
        try {
            // Set datacenter characteristics
            String arch = "x86";
            String os = "Linux";
            String vmm = "Xen";
            double timeZone = 10.0;
            double cost = 3.0;
            double costPerMem = 0.05;
            double costPerStorage = 0.001;
            double costPerBw = 0.0;
            
            List<Host> hostList = buildHosts();
            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw);
            
            // Create datacenter
            List<Storage> storageList = new ArrayList<>();
            Datacenter datacenter = new Datacenter(name, characteristics, 
                new VmAllocationPolicySimple(hostList), storageList, 1.0);
            
            return datacenter;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Creates hosts for the datacenter
private List<Host> buildHosts() {
        List<Host> hosts = new ArrayList<>();

        // Create hosts
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < numPesPerHost; p++) {
                peList.add(new Pe(p, new PeProvisionerSimple(peMips)));
            }
            // Create host
            Host host = new Host(
                i,
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
        String vmm = "Xen";
        for (int i = 0; i < numVMs; i++) {
            // Create VM
            Vm vm = new Vm(
                i,
                -1,
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

    // Returns list of hosts
    public List<Host> createHosts() {
        return buildHosts();
    }

    // Returns list of VMs
    public List<Vm> createVms() {
        return buildVms();
    }
    
    // Creates VMs with specific broker ID
    public List<Vm> createVms(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        String vmm = "Xen";
        for (int i = 0; i < numVMs; i++) {
            Vm vm = new Vm(
                i,
                brokerId,
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

