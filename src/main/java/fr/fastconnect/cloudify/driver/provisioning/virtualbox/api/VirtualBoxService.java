package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.util.concurrent.TimeoutException;

public interface VirtualBoxService {

    public void connect(String url, String login, String password) throws VirtualBoxException;

    public VirtualBoxMachineInfo[] getAll() throws VirtualBoxException;

    public VirtualBoxMachineInfo[] getAll(String prefix) throws VirtualBoxException;

    public VirtualBoxMachineInfo getInfo(String name) throws VirtualBoxException;

    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, String hostOnlyInterface, String hostSharedFolder, long endTime)
            throws Exception;

    public void destroy(String machineGuid, long endTime) throws Exception;

    public void start(String machineGuid, String login, String password, boolean headless, long endTime) throws Exception;

    public void stop(String machineGuid, long endTime) throws Exception;

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception;

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privIfName, String pubIfName, String ip, String mask,
            String gateway, long endTime) throws Exception;

    public void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception;

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception;

    public VirtualBoxHostOnlyInterface getHostOnlyInterface(String hostonlyifName) throws VirtualBoxException;

    public VirtualBoxVolumeInfo[] getAllVolumesInfo() throws VirtualBoxException;

    public VirtualBoxVolumeInfo[] getAllVolumesInfo(String prefix) throws VirtualBoxException;

    public VirtualBoxVolumeInfo getVolumeInfo(String name) throws VirtualBoxException;

    public VirtualBoxVolumeInfo[] getVolumeInfoByMachine(String machineName) throws VirtualBoxException;

    public VirtualBoxVolumeInfo createVolume(String prefix, String path, int size, String hardDiskType, long endTime) throws TimeoutException,
            VirtualBoxException;

    public void deleteVolume(String volumeId, long endTime) throws VirtualBoxException, TimeoutException;

    public void attachVolume(String machineGuid, String volumeName, int controllerPort, long endTime) throws Exception;

    public void detachVolume(String guid, String name, long endTime) throws Exception;

    public void setStorageControllerName(String storageControllerName);

}