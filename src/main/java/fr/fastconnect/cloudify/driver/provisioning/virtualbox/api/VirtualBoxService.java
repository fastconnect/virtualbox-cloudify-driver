package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.IOException;

public interface VirtualBoxService {

    public void connect(String url, String login, String password) throws VirtualBoxException;

    public VirtualBoxMachineInfo[] getAll() throws IOException;

    public VirtualBoxMachineInfo[] getAll(String prefix) throws IOException;

    public VirtualBoxMachineInfo getInfo(String name) throws IOException;

    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, String hostOnlyInterface, String hostSharedFolder)
            throws Exception;

    public void destroy(String machineGuid) throws Exception;

    public void start(String machineGuid, String login, String password, boolean headless) throws Exception;

    public void grantAccessToSharedFolder(String machineGuid, String login, String password) throws Exception;

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gateway) throws Exception;

    public void updateHosts(String machineGuid, String login, String password, String hosts) throws InterruptedException, Exception;

    public void stop(String machineGuid) throws Exception;

    public VirtualBoxHostOnlyInterface getHostOnlyInterface(String hostonlyifName);

}