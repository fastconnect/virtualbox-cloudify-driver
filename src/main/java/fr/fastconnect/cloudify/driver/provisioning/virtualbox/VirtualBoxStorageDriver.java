package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.cloudifysource.domain.cloud.Cloud;
import org.cloudifysource.domain.cloud.storage.StorageTemplate;
import org.cloudifysource.esc.driver.provisioning.storage.BaseStorageDriver;
import org.cloudifysource.esc.driver.provisioning.storage.StorageProvisioningDriver;
import org.cloudifysource.esc.driver.provisioning.storage.StorageProvisioningException;
import org.cloudifysource.esc.driver.provisioning.storage.VolumeDetails;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxException;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxMachineInfo;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxService42;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxVolumeInfo;

public class VirtualBoxStorageDriver extends BaseStorageDriver implements StorageProvisioningDriver {

    private static final String VBOX_HARDDISK_TYPE = "vbox.hardDiskType";

    private Cloud cloud;

    private VirtualBoxDriverContext virtualBoxDriverContext;

    private Map<String, Integer> deviceNumbers = new HashMap<String, Integer>();

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualBoxService42.class.getName());

    public VirtualBoxStorageDriver() {
        char volumeLetter = 'b';
        final String volumePrefix = "/dev/sd";
        for (int cpt = 1; cpt < 30; cpt++) {
            deviceNumbers.put(volumePrefix + volumeLetter, cpt);
            volumeLetter = (char) (volumeLetter + 1);
        }
    }

    public void setConfig(Cloud cloud, String computeTemplateName) {
        this.cloud = cloud;
    }

    public VolumeDetails createVolume(String templateName, String location, long duration, TimeUnit timeUnit) throws TimeoutException,
            StorageProvisioningException {

        StorageTemplate storageTemplate = this.cloud.getCloudStorage().getTemplates().get(templateName);

        final long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

        try {
            VirtualBoxVolumeInfo info = this.virtualBoxDriverContext.getVirtualBoxService().createVolume(
                    storageTemplate.getNamePrefix(),
                    storageTemplate.getPath(),
                    storageTemplate.getSize(),
                    (String) storageTemplate.getCustom().get(VBOX_HARDDISK_TYPE),
                    endTime);

            VolumeDetails details = new VolumeDetails();

            details.setId(info.getGuid());
            details.setLocation(info.getLocation());
            details.setName(info.getName());
            details.setSize(info.getSize());

            return details;

        } catch (VirtualBoxException e) {
            throw new StorageProvisioningException(e);
        }
    }

    public void attachVolume(String volumeId, String device, String ip, long duration, TimeUnit timeUnit) throws TimeoutException, StorageProvisioningException {

        final long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

        VirtualBoxVolumeInfo volumeInfo = null;
        try {
            for (VirtualBoxVolumeInfo info : this.virtualBoxDriverContext.getVirtualBoxService().getAllVolumesInfo()) {

                if (info.getGuid().equals(volumeId)) {
                    volumeInfo = info;
                    break;
                }
            }
        } catch (VirtualBoxException e1) {
            throw new StorageProvisioningException(e1);
        }

        String machineName = ipToMachineName(ip);

        logger.log(Level.INFO, "Attaching volume " + volumeInfo.getName() + " to machine " + machineName);

        try {
            VirtualBoxMachineInfo info = this.virtualBoxDriverContext.getVirtualBoxService().getInfo(machineName);

            this.virtualBoxDriverContext.getVirtualBoxService().attachVolume(info.getGuid(), volumeInfo.getName(), deviceNumbers.get(device), endTime);

        } catch (IOException e) {
            throw new StorageProvisioningException(e);
        } catch (Exception e) {
            throw new StorageProvisioningException(e);
        }

    }

    public void detachVolume(String volumeId, String ip, long duration, TimeUnit timeUnit) throws TimeoutException, StorageProvisioningException {
        final long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

        VirtualBoxVolumeInfo volumeInfo = null;
        try {
            for (VirtualBoxVolumeInfo info : this.virtualBoxDriverContext.getVirtualBoxService().getAllVolumesInfo()) {

                if (info.getGuid().equals(volumeId)) {
                    volumeInfo = info;
                    break;
                }
            }
        } catch (VirtualBoxException e1) {
            throw new StorageProvisioningException(e1);
        }

        String machineName = ipToMachineName(ip);

        logger.log(Level.INFO, "Detaching volume " + volumeInfo.getName() + " to machine " + machineName);

        try {
            VirtualBoxMachineInfo info = this.virtualBoxDriverContext.getVirtualBoxService().getInfo(machineName);

            this.virtualBoxDriverContext.getVirtualBoxService().detachVolume(info.getGuid(), volumeInfo.getName(), endTime);

        } catch (IOException e) {
            throw new StorageProvisioningException(e);
        } catch (VirtualBoxException e) {
            throw new StorageProvisioningException(e);
        } catch (Exception e) {
            throw new StorageProvisioningException(e);
        }
    }

    public void deleteVolume(String location, String volumeId, long duration, TimeUnit timeUnit) throws TimeoutException, StorageProvisioningException {

        final long endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);

        try {
            this.virtualBoxDriverContext.getVirtualBoxService().deleteVolume(volumeId, endTime);
        } catch (VirtualBoxException e) {
            throw new StorageProvisioningException(e);
        }
    }

    public Set<VolumeDetails> listVolumes(String ip, long duration, TimeUnit timeUnit) throws TimeoutException, StorageProvisioningException {

        if (!ip.startsWith(this.virtualBoxDriverContext.getBaseIP())) {
            throw new StorageProvisioningException("Invalid IP: " + ip + ", should start with " + this.virtualBoxDriverContext.getBaseIP());
        }

        String machineName = ipToMachineName(ip);

        Set<VolumeDetails> result = new HashSet<VolumeDetails>();

        try {
            for (VirtualBoxVolumeInfo info : this.virtualBoxDriverContext.getVirtualBoxService().getVolumeInfoByMachine(machineName)) {
                VolumeDetails details = new VolumeDetails();

                details.setId(info.getGuid());
                details.setLocation(info.getLocation());
                details.setName(info.getName());
                details.setSize(info.getSize());

                result.add(details);

            }

            return result;
        } catch (VirtualBoxException ex) {
            throw new StorageProvisioningException(ex);
        }
    }

    public String getVolumeName(String volumeId) throws StorageProvisioningException {

        try {
            for (VirtualBoxVolumeInfo info : this.virtualBoxDriverContext.getVirtualBoxService().getAllVolumesInfo()) {

                if (info.getGuid().equals(volumeId)) {
                    return info.getName();
                }
            }
        } catch (VirtualBoxException e) {
            throw new StorageProvisioningException(e);
        }

        return null;
    }

    public void close() {

    }

    @Override
    public void setComputeContext(Object computeContext) throws StorageProvisioningException {
        this.virtualBoxDriverContext = (VirtualBoxDriverContext) computeContext;

        logger.info("Set Compute Context");
    }

    @Override
    public Set<VolumeDetails> listAllVolumes() throws StorageProvisioningException {
        Set<VolumeDetails> result = new HashSet<VolumeDetails>();
        try {
            for (VirtualBoxVolumeInfo info : this.virtualBoxDriverContext.getVirtualBoxService().getAllVolumesInfo()) {
                VolumeDetails details = new VolumeDetails();

                details.setId(info.getGuid());
                details.setLocation(info.getLocation());
                details.setName(info.getName());
                details.setSize(info.getSize());

                result.add(details);
            }
        } catch (VirtualBoxException e) {
            throw new StorageProvisioningException(e);
        }

        return result;
    }

    private String ipToMachineName(String ip) throws StorageProvisioningException {

        try {
            // get all machines to find the right one thanks to the IP
            for(VirtualBoxMachineInfo machineInfo : this.virtualBoxDriverContext.getVirtualBoxService().getAll()){
                String publicIP = this.virtualBoxDriverContext.getVirtualBoxService().getPublicAddressIP(machineInfo.getGuid());
                String privateIP = this.virtualBoxDriverContext.getVirtualBoxService().getPrivateAddressIP(machineInfo.getGuid());
                if(publicIP != null && publicIP.equals(ip)){
                    return machineInfo.getMachineName();
                }
                else if(privateIP != null && privateIP.equals(ip)){
                    return machineInfo.getMachineName();
                }
            }
        } catch (Exception e) {
            throw new StorageProvisioningException("Unable to retrieve name of the machine with IP: " + ip, e);
        }

        throw new StorageProvisioningException("Unable to find machine with IP: " + ip);
    }

}
