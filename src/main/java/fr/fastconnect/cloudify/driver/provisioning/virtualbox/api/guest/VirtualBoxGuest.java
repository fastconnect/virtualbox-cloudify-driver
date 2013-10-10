package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

public interface VirtualBoxGuest {

    void updateHostname(String machineGuid, String login, String password, String hostname, long endTime) throws Exception;

    void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws Exception;

    void updateNetworkingInterfaces(String machineGuid, String login, String password, String privateAddrIP, long endTime) throws Exception;

    void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception;

    void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception;

    void ping(String machineGuid, String login, String password, long endTime) throws Exception;

    void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception;

    void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception;

    String getPublicAddressIP(String machineNameOrId) throws Exception;

    String getPrivateAddressIP(String machineNameOrId) throws Exception;

}
