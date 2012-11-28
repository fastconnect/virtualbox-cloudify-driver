package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

public interface VirtualBoxGuest {
    
    void updateHostname(String machineGuid, String login, String password, String hostname) throws Exception;

    void updateHosts(String machineGuid, String login, String password, String hosts) throws Exception;
    
    void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gatewayIp, String eth0Mac, String eth1Mac) throws Exception;
    
    void executeScript(String machineGuid, String login, String password, String filename, String content) throws Exception;
    
    void createFile(String machineGuid, String login, String password, String destination, String content) throws Exception;
    
    void ping(String machineGuid, String login, String password) throws Exception;
}
