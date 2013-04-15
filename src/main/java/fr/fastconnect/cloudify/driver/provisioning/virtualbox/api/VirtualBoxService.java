package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.cloudifysource.esc.driver.provisioning.storage.VolumeDetails;

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

    public VirtualBoxVolumeInfo createVolume(String prefix, String path, int size, String hardDiskType, long endTime)  throws TimeoutException, VirtualBoxException;
    
    public VirtualBoxVolumeInfo[] getAllVolumesInfo() throws IOException;
    
    public VirtualBoxVolumeInfo[] getAllVolumesInfo(String prefix) throws IOException;
    
    public VirtualBoxVolumeInfo getVolumeInfo(String name) throws IOException;

    public void attachVolume(String machineGuid, String volumeName, int controllerPort, long endTime) throws Exception;

    public void detachVolume(String guid, String name, long endTime) throws Exception;

    public VirtualBoxVolumeInfo[] getVolumeInfoByMachine(String machineName);

    public void deleteVolume(String volumeId, long endTime) throws VirtualBoxException, TimeoutException;

}