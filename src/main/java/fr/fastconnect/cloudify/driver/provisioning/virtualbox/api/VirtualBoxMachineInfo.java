package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

public class VirtualBoxMachineInfo {
    private String machineName;
    
    private String guid;

    public String getMachineName() {
        return machineName;
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }
}
