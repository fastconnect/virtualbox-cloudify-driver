package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.IOException;

public interface VirtualBoxService {

    public void connect(String url, String login, String password) throws VirtualBoxException;

    public VirtualBoxMachineInfo[] getAll() throws IOException;

    public VirtualBoxMachineInfo[] getAll(String prefix) throws IOException;

    public VirtualBoxMachineInfo getInfo(String name) throws IOException;

    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, String hostOnlyInterface, String hostSharedFolder, long endTime)
            throws Exception;

    public void destroy(String machineGuid, long endTime) throws Exception;

    public void start(String machineGuid, String login, String password, boolean headless, long endTime) throws Exception;

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception;

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gateway, long endTime) throws Exception;

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception;

    public void stop(String machineGuid, long endTime) throws Exception;

    public VirtualBoxHostOnlyInterface getHostOnlyInterface(String hostonlyifName);

}