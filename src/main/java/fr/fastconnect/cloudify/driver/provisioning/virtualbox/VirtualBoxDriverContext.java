package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxService;

public class VirtualBoxDriverContext {

    private String serverNamePrefix;
    
    private String baseIP;
    
    private boolean management;
    
    private VirtualBoxService virtualBoxService;
    
    public String getServerNamePrefix() {
        return serverNamePrefix;
    }
    
    public void setServerNamePrefix(String serverNamePrefix) {
        this.serverNamePrefix = serverNamePrefix;
    }
    
    public String getBaseIP() {
        return baseIP;
    }
    
    public void setBaseIP(String baseIP) {
        this.baseIP = baseIP;
    }
    
    public VirtualBoxService getVirtualBoxService() {
        return virtualBoxService;
    }
    
    public void setVirtualBoxService(VirtualBoxService virtualBoxService) {
        this.virtualBoxService = virtualBoxService;
    }
    
    public boolean isManagement() {
        return management;
    }
    
    public void setManagement(boolean management) {
        this.management = management;
    }
}
