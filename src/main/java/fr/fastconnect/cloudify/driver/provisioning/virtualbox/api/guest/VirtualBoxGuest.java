package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

public interface VirtualBoxGuest {
    
    void updateHostname(String machineGuid, String login, String password, String hostname, long endTime) throws Exception;

    void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws Exception;
    
    void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gatewayIp, String eth0Mac, String eth1Mac, long endTime) throws Exception;
    
    void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception;
    
    void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception;
    
    void ping(String machineGuid, String login, String password, long endTime) throws Exception;
}
