package org.cloudifysource.sec.driver.provisioning.virtualbox.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.virtualbox_4_1.IMachine;
import org.virtualbox_4_1.VirtualBoxManager;

public class VirtualBoxService {
    
    private VirtualBoxManager virtualBoxManager;
    
    private Runtime runtime;

    public VirtualBoxService() {
        this.runtime = Runtime.getRuntime();
        
        this.virtualBoxManager = VirtualBoxManager.createInstance(null);
    }

    public void connect(String url, String login, String password){
        this.virtualBoxManager.connect(url, login, password);
    }
    
    public VirtualBoxMachineInfo[] getAll() throws IOException {
        return this.getAll(null);
    }

    public VirtualBoxMachineInfo[] getAll(String prefix) throws IOException {

        ArrayList<VirtualBoxMachineInfo> result = new ArrayList<VirtualBoxMachineInfo>();

        for(IMachine m : this.virtualBoxManager.getVBox().getMachines()){
            VirtualBoxMachineInfo vboxInfo = new VirtualBoxMachineInfo();
            vboxInfo.setGuid(m.getId());
            vboxInfo.setMachineName(m.getName());

            if (prefix != null) {
                if (vboxInfo.getMachineName().startsWith(prefix)) {
                    result.add(vboxInfo);
                }
            }
            else {
                result.add(vboxInfo);
            }
        }
        
        return result.toArray(new VirtualBoxMachineInfo[0]);
    }

    public VirtualBoxMachineInfo getInfo(String name) throws IOException {
        for (VirtualBoxMachineInfo info : getAll()) {
            if (info.getMachineName().equals(name)) {
                return info;
            }
        }

        return null;
    }

    public VirtualBoxMachineInfo create(String boxPath, String vmname, int cpus, int memory, String hostOnlyInterface) throws Exception {

        File boxPathFile = new File(boxPath);

        if (boxPath.startsWith("~")) {
            boxPath = boxPath.substring(1);
            File home = new File(System.getProperty("user.home"));
            boxPathFile = new File(home, boxPath);
        }

        ArrayList<String> options = new ArrayList<String>();
        options.add("VBoxManage");
        options.add("import");
        options.add(boxPathFile.getAbsolutePath());
        options.add("--vsys");
        options.add("0");
        options.add("--vmname");
        options.add(vmname);
        if (cpus > 0) {
            options.add("--cpus");
            options.add(Integer.toString(cpus));
        }
        if (memory > 0) {
            options.add("--memory");
            options.add(Integer.toString(memory));
        }

        Process process = runtime.exec(options.toArray(new String[0]));

        // read output to detect "Successfully imported the appliance"
        String[] outputs = getOutputs(process);

        if (process.waitFor() > 0) {
            throw new Exception("Unable to import VM:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        VirtualBoxMachineInfo info = getInfo(vmname);

        // configure the VM to use NAT for the first network interface
        process = runtime.exec(new String[] {
                "VBoxManage",
                "modifyvm",
                info.getGuid(),
                "--nic1",
                "nat"
        });

        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set the network interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        process = runtime.exec(new String[] {
                "VBoxManage",
                "modifyvm",
                info.getGuid(),
                "--natnet1",
                "default"
        });

        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set the network interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
        
        // configure the VM to use HostOnly for the second network interface
        process = runtime.exec(new String[] {
                "VBoxManage",
                "modifyvm",
                info.getGuid(),
                "--nic2",
                "hostonly"
        });

        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set the network interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        process = runtime.exec(new String[] {
                "VBoxManage",
                "modifyvm",
                info.getGuid(),
                "--hostonlyadapter2",
                hostOnlyInterface
        });

        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set the network interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        
        return info;
    }

    public void destroy(String machineGuid) throws Exception {
        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "unregistervm",
                machineGuid,
                "--delete"
        });

        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set stop machine:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
    }

    public void start(String machineGuid, String login, String password, String host, boolean headless) throws Exception {
        
        ArrayList<String> startOptions = new ArrayList<String>();
        startOptions.add("VBoxManage");
        startOptions.add("startvm");
        startOptions.add(machineGuid);
        
        if(headless){
            startOptions.add("--type");
            startOptions.add("headless");
        }
        
        Process process = runtime.exec(startOptions.toArray(new String[0]));

        // read output to detect error
        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set start machine:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
        
        boolean ready = false;
        int nbTry = 0;
        do{
            nbTry++;
            
            // wait for guest to be ready
            process = runtime.exec(new String[] {
                    "VBoxManage",
                    "guestcontrol",
                    machineGuid,
                    "exec",
                    "/bin/ls",
                    "--username",
                    login,
                    "--password",
                    password,
                    "--wait-exit",
                    "--wait-stdout",
                    "--wait-stderr"
            });
    
            outputs = getOutputs(process);
            ready = process.waitFor() == 0;
            
            if(!ready){
                Thread.sleep(5000);
            }
            
        } while (!ready && nbTry <= 10);
        
        if(!ready){
            throw new Exception("Unable to execute script on machine "+machineGuid+" because it's not yet ready:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n"+
                "sudo sed -i s/.*$/" + host + "/ /etc/hostname\n" +
                "sudo service hostname start";

        this.executeScript(machineGuid, login, password, "updatehostname.sh", "/tmp/updatehostname.sh", updatehostnameContent);
    }
    
    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask) throws Exception {
        
        // create the new /etc/network/interfaces file, and copy to guest
        String interfacesContent = "auto lo\n"+
                "iface lo inet loopback\n\n"+
                "auto eth0\n"+
                "iface eth0 inet dhcp\n\n"+
                "auto eth1\n"+
                "iface eth1 inet static\n"+
                "address "+ip+"\n"+
                "netmask "+mask+"\n";
        
        this.copyFile(machineGuid, login, password, "interfaces", "/tmp/interfaces", interfacesContent);
        
        // create the script to update the interfaces file, and copy it to the guest        
        String updateinterfacesContent = "#!/bin/bash\n"+
                "cat /tmp/interfaces | sudo tee /etc/network/interfaces\n"+
                "sudo /etc/init.d/networking restart";  
        
        this.executeScript(machineGuid, login, password, "updateinterfaces.sh", "/tmp/updateinterfaces.sh", updateinterfacesContent);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts) throws InterruptedException, Exception {

        // create the new /etc/hosts file, and copy to guest
        this.copyFile(machineGuid, login, password, "hosts", "/tmp/hosts", hosts);
        
        // create the script to update the interfaces file, and copy it to the guest
        String updatehostsScript = "#!/bin/bash\n"+
                "cat /tmp/hosts | sudo tee /etc/hosts\n";
        
        this.executeScript(machineGuid, login, password, "updatehosts.sh", "/tmp/updatehosts.sh", updatehostsScript);
    }

    public void stop(String machineGuid) throws Exception {
        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "controlvm",
                machineGuid,
                "poweroff"
        });

        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to stop the machine "+machineGuid+":\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
        
        Pattern pattern = Pattern.compile("VMState=\"(.*)\"");
        boolean off = false;
        int nbTry = 0;
        do{
            nbTry++;
            
            // wait for guest to be ready
            process = runtime.exec(new String[] {
                "VBoxManage",
                "showvminfo",
                machineGuid,
                "--machinereadable"
            });
    
            outputs = getOutputs(process);
            if(process.waitFor() > 0){
                throw new Exception("Unable to execute check status of machine "+machineGuid+" :\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
            }
            
            String[] lines = outputs[0].split("\n");
            for(String line : lines){
                Matcher matcher = pattern.matcher(line);
                if(matcher.find()){
                    String status = matcher.group(1);
                    off = status.equals("poweroff");
                    break;
                }
            }
            
            if(!off){
                Thread.sleep(5000);
            }
            
        } while (!off && nbTry <= 10);
        
        if(!off){
            throw new Exception("Unable to execute script on machine "+machineGuid+" because it's not yet ready:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
    }

    public String existsHostOnlyInterface(
            String hostonlyifIP,
            String hostonlyifMask) throws InterruptedException, Exception {

        // Retrieve the existing VMs
        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "list",
                "hostonlyifs"
        });
        
        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to create Host Only Interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
        
        Pattern patternName = Pattern.compile("Name: *(.*)");
        Pattern patternIP = Pattern.compile("IPAddress: *(.*)");
        Pattern patternMask = Pattern.compile("NetworkMask: *(.*)");

        String stdout = outputs[0];
        String[] stdoutLines = stdout.split("\n");
        for(int cpt = 0; cpt < stdoutLines.length; cpt++) {
            String line = stdoutLines[cpt];
            
            if (line.startsWith("Name:")) {
                Matcher matcher = patternName.matcher(line);
                matcher.find();
                String name = matcher.group(1);
                
                cpt += 3;
                line = stdoutLines[cpt];
                
                matcher = patternIP.matcher(line);
                matcher.find();
                String ip = matcher.group(1);
                
                cpt += 1;
                line = stdoutLines[cpt];
                
                matcher = patternMask.matcher(line);
                matcher.find();
                String mask = matcher.group(1);
                
                if (ip.equals(hostonlyifIP) && mask.equals(hostonlyifMask)) {
                    return name;
                }
            }
        }

        return null;
    }

    public String createHostOnlyInterface(String hostonlyifIP, String hostonlyifMask) throws Exception {

        String interfaceName = "";

        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "hostonlyif",
                "create"
        });
        
        String[] outputs = getOutputs(process);
        
        if (process.waitFor() > 0) {
            throw new Exception("Unable to create Host Only Interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        String stdout = outputs[0];
        String[] stdoutSplit = stdout.split("\n");
        String lastLine = stdoutSplit[stdoutSplit.length-1];
        
        Pattern pattern = Pattern.compile("Interface '([^']*)' was successfully created");
        Matcher matcher = pattern.matcher(lastLine);

        if(!matcher.find()){
            throw new Exception("Unable to analyze output of creation of the Host Only Intereface");
        }
        
        interfaceName = matcher.group(1);
        
        process = runtime.exec(new String[] {
                "VBoxManage",
                "hostonlyif",
                "ipconfig",
                interfaceName,
                "--ip",
                hostonlyifIP,
                "--netmask",
                hostonlyifMask
        });
        
        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to configure Host Only Interface:\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
        
        return interfaceName;
    }

    public String[] getOutputs(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String allLines = "";
        String line = null;

        while ((line = reader.readLine()) != null) {
            allLines += line + "\n";
        }

        String errorLines = "";
        reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = reader.readLine()) != null) {
            errorLines += line + "\n";
        }

        return new String[] { allLines, errorLines };
    }
    
    private void copyFile(String machineGuid, String login, String password, String filename, String destination, String content) throws Exception{
        // create the new /etc/hosts file, and copy to guest
        File file = new File(new File(System.getProperty("java.io.tmpdir")), filename);

        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(content);
        writer.close();

        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "guestcontrol",
                machineGuid,
                "copyto",
                file.getAbsolutePath(),
                destination,
                "--username",
                login,
                "--password",
                password,
        });
        
        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to copy file "+filename+" to "+destination+":\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
    }
    
    private void executeScript(String machineGuid, String login, String password, String filename, String destination, String content) throws Exception {
        
        // copy the file
        this.copyFile(machineGuid, login, password, filename, destination, content);
        
        // chmod
        Process process = runtime.exec(new String[] {
                "VBoxManage",
                "guestcontrol",
                machineGuid,
                "exec",
                "/bin/chmod",
                "--username",
                login,
                "--password",
                password,
                "--wait-exit",
                "--wait-stdout",
                "--wait-stderr",
                "--",
                "a+x",
                destination
        });
        
        String[] outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to set chmod +x for file "+destination+":\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }

        // execute it
        process = runtime.exec(new String[] {
                "VBoxManage",
                "guestcontrol",
                machineGuid,
                "exec",
                destination,
                "--username",
                login,
                "--password",
                password,
                "--wait-exit",
                "--wait-stdout",
                "--wait-stderr"
        });
        
        outputs = getOutputs(process);
        if (process.waitFor() > 0) {
            throw new Exception("Unable to execute "+destination+":\nstdout:\n" + outputs[0] + "\nstderr:\n" + outputs[1]);
        }
    }
}
