package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

public class VirtualBoxVolumeInfo {
    private String guid;
    private String name;
    private int size;
    private String location;
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getSize() {
        return size;
    }
    public void setSize(int size) {
        this.size = size;
    }
    public String getLocation() {
        return location;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    
    
}
