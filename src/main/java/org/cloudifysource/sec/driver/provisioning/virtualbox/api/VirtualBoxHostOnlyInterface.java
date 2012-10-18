package org.cloudifysource.sec.driver.provisioning.virtualbox.api;

public class VirtualBoxHostOnlyInterface {

    private String ip;
    
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    private String mask;
    
}
