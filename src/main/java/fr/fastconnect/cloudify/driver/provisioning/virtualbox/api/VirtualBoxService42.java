package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.apache.commons.lang.StringUtils;
import org.virtualbox_4_2.CleanupMode;
import org.virtualbox_4_2.DeviceType;
import org.virtualbox_4_2.Holder;
import org.virtualbox_4_2.IAppliance;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IMedium;
import org.virtualbox_4_2.IMediumAttachment;
import org.virtualbox_4_2.INetworkAdapter;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.IStorageController;
import org.virtualbox_4_2.IVirtualSystemDescription;
import org.virtualbox_4_2.ImportOptions;
import org.virtualbox_4_2.NetworkAttachmentType;
import org.virtualbox_4_2.VBoxException;
import org.virtualbox_4_2.VirtualBoxManager;
import org.virtualbox_4_2.VirtualSystemDescriptionType;
import org.virtualbox_4_2.jaxws.InvalidObjectFaultMsg;
import org.virtualbox_4_2.jaxws.RuntimeFaultMsg;
import org.virtualbox_4_2.jaxws.VboxPortType;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.PublicInterfaceConfig;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuest;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuestProvider;

public class VirtualBoxService42 implements VirtualBoxService {

    private static final int VERR_FILE_NOT_FOUND = -2135228412;

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(VirtualBoxService42.class.getName());

    private static final int SERVER_POLLING_INTERVAL_MILLIS = 10 * 1000; // 10 seconds
    private static final String CLOUDIFY_SHARED_FOLDER = "cloudify";
    private static final String DEFAULT_CONTROLLER = "SATA Controller";

    private static final ReentrantLock COMPUTE_MUTEX = new ReentrantLock();
    private static final ReentrantLock STORAGE_MUTEX = new ReentrantLock();
    private final ReentrantLock virtualboxManagerLock = new ReentrantLock();

    private String storageControllerName = DEFAULT_CONTROLLER;

    private VirtualBoxManager virtualBoxManager;
    private VirtualBoxGuestProvider virtualBoxGuestProvider;

    private String lastUrl;
    private String lastLogin;
    private String lastPassword;

    protected VirtualBoxManager getVirtualBoxManager() throws VirtualBoxException {

        virtualboxManagerLock.lock();
        try {
            String apiVersion = virtualBoxManager.getVBox().getAPIVersion();
            logger.log(Level.FINE, "VirtualBox version: " + apiVersion);
        } catch (VBoxException ex) {
            logger.log(Level.SEVERE, "Unable to communicate with VirtualBox WebService, maybe disconnected.");
            if (lastUrl != null) {
                this.connect(lastUrl, lastLogin, lastPassword);
            }
            else {
                throw new VirtualBoxException(ex);
            }
        } finally {
            virtualboxManagerLock.unlock();
        }

        return virtualBoxManager;
    }

    public VirtualBoxService42() {
        this.virtualBoxManager = VirtualBoxManager.createInstance(null);
        this.virtualBoxGuestProvider = new VirtualBoxGuestProvider(this.virtualBoxManager);
    }

    public void connect(String url, String login, String password) throws VirtualBoxException {

        this.lastLogin = login;
        this.lastPassword = password;
        this.lastUrl = url;

        try {
            logger.log(Level.INFO, "Trying to connect to " + url + " using login: " + login);
            this.virtualBoxManager.connect(url, login, password);
        } catch (Exception ex) {
            throw new VirtualBoxException("Unable to connect to " + url + " with login " + login, ex);
        }
    }

    public boolean isConnected() {
        try {
            if (this.virtualBoxManager.getVBox() == null) {
                return false;
            }
            this.getAll();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void disconnect() {
        logger.log(Level.FINE, "Disconnecting VirtualBoxManager.");
        virtualBoxManager.disconnect();
    }

    public VirtualBoxMachineInfo[] getAll() throws VirtualBoxException {
        return this.getAll(null);
    }

    public VirtualBoxMachineInfo[] getAll(String prefix) throws VirtualBoxException {

        ArrayList<VirtualBoxMachineInfo> result = new ArrayList<VirtualBoxMachineInfo>();

        VirtualBoxManager manager = this.getVirtualBoxManager();

        for (IMachine m : manager.getVBox().getMachines()) {
            VirtualBoxMachineInfo vboxInfo = new VirtualBoxMachineInfo();
            vboxInfo.setGuid(m.getId());
            vboxInfo.setMachineName(m.getName());

            if (prefix != null) {
                if (vboxInfo.getMachineName().startsWith(prefix)) {
                    result.add(vboxInfo);
                }
            }
            else {
                result.add(vboxInfo);
            }
        }

        return result.toArray(new VirtualBoxMachineInfo[0]);
    }

    public VirtualBoxMachineInfo getInfo(String name) throws VirtualBoxException {

        for (VirtualBoxMachineInfo info : getAll()) {
            if (info.getMachineName().equals(name)) {
                return info;
            }
        }

        return null;
    }

    @SuppressWarnings("restriction")
    private void getDescription(VboxPortType port, String obj, Holder<List<org.virtualbox_4_2.VirtualSystemDescriptionType>> aTypes,
            Holder<List<String>> aRefs, Holder<List<String>> aOvfValues, Holder<List<String>> aVBoxValues, Holder<List<String>> aExtraConfigValues) {
        try {
            javax.xml.ws.Holder<List<org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType>> tmp_aTypes = new javax.xml.ws.Holder<List<org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType>>();
            javax.xml.ws.Holder<List<String>> tmp_aRefs = new javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>> tmp_aOvfValues = new javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>> tmp_aVBoxValues = new javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>> tmp_aExtraConfigValues = new javax.xml.ws.Holder<List<String>>();
            port.iVirtualSystemDescriptionGetDescription(obj, tmp_aTypes, tmp_aRefs, tmp_aOvfValues, tmp_aVBoxValues, tmp_aExtraConfigValues);

            // this one is buggy
            // aTypes.value = Helper.convertEnums(org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType.class,
            // org.virtualbox_4_2.VirtualSystemDescriptionType.class, tmp_aTypes.value);
            aTypes.value = new ArrayList<VirtualSystemDescriptionType>();
            for (org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType tmp_t : tmp_aTypes.value) {
                VirtualSystemDescriptionType t = VirtualSystemDescriptionType.fromValue(tmp_t.value());
                aTypes.value.add(t);
            }
            aRefs.value = tmp_aRefs.value;
            aOvfValues.value = tmp_aOvfValues.value;
            aVBoxValues.value = tmp_aVBoxValues.value;
            aExtraConfigValues.value = tmp_aExtraConfigValues.value;
        } catch (InvalidObjectFaultMsg e) {
            throw new VBoxException(e, e.getMessage());
        } catch (RuntimeFaultMsg e) {
            throw new VBoxException(e, e.getMessage());
        }
    }

    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, PublicInterfaceConfig publicIfConfig,
            String hostSharedFolder, long endTime) throws Exception {

        logger.log(Level.INFO, "Trying to create VM '" + vmname + "' cpus:" + cpus + " memory:" + memory + " from template: " + boxPath);

        File boxPathFile = new File(boxPath);

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IAppliance appliance = manager.getVBox().createAppliance();

        long timeLeft = endTime - System.currentTimeMillis();

        IProgress progress = appliance.read(boxPathFile.getPath());
        progress.waitForCompletion((int) timeLeft);
        
        if (!progress.getCompleted()) {
            throw new TimeoutException("Unable to import VM: Timeout");
        }
        
        if (progress.getResultCode() != 0) {
            
            if(progress.getErrorInfo().getResultCode() == VERR_FILE_NOT_FOUND) {
                throw new VirtualBoxBoxNotFoundException("Box "+boxPath+" not found. CODE: "+progress.getErrorInfo().getResultCode()+" ERROR:" + progress.getErrorInfo().getText());
            }
            
            throw new VirtualBoxException("Unable to import VM. CODE: "+progress.getErrorInfo().getResultCode()+" ERROR:" + progress.getErrorInfo().getText());
        }
        appliance.interpret();

        IVirtualSystemDescription virtualSystemDescription = appliance.getVirtualSystemDescriptions().get(0);

        Holder<List<VirtualSystemDescriptionType>> types = new Holder<List<VirtualSystemDescriptionType>>();
        Holder<List<String>> refs = new Holder<List<String>>();
        Holder<List<String>> ovfValues = new Holder<List<String>>();
        Holder<List<String>> vBoxValues = new Holder<List<String>>();
        Holder<List<String>> extraConfigValues = new Holder<List<String>>();

        // there is a bug in the vboxws function: it's unable to convert the WS Enums, so do it manually
        // virtualSystemDescription.getDescription(types, refs, ovfValues, vBoxValues, extraConfigValues);
        getDescription(appliance.getRemoteWSPort(), virtualSystemDescription.getWrapped(), types, refs, ovfValues, vBoxValues, extraConfigValues);

        List<Boolean> enabled = new ArrayList<Boolean>();
        for (int cpt = 0; cpt < vBoxValues.value.size(); cpt++) {
            if (types.value.get(cpt) == VirtualSystemDescriptionType.Name) {
                vBoxValues.value.set(cpt, vmname);
            }
            enabled.add(true);
        }

        virtualSystemDescription.setFinalValues(enabled, vBoxValues.value, extraConfigValues.value);

        timeLeft = endTime - System.currentTimeMillis();
        progress = appliance.importMachines(Arrays.asList(new ImportOptions[0]));
        progress.waitForCompletion((int) timeLeft);

        if (!progress.getCompleted()) {
            throw new TimeoutException("Unable to import VM: Timeout");
        }

        if (progress.getResultCode() != 0) {
            throw new VirtualBoxException("Unable to import VM: " + progress.getErrorInfo().getText());
        }

        String machineName = appliance.getMachines().get(0);

        IMachine machine = manager.getVBox().findMachine(machineName);
        VirtualBoxMachineInfo result = new VirtualBoxMachineInfo();
        result.setGuid(machine.getId());
        result.setMachineName(vmname);

        COMPUTE_MUTEX.lock();
        try {
            ISession session = manager.openMachineSession(machine);
            machine = session.getMachine();

            try {
                machine.setName(vmname);
                machine.setCPUCount(cpus);
                machine.setMemorySize(memory);

                INetworkAdapter nic0 = machine.getNetworkAdapter(0l);
                nic0.setNATNetwork("default");
                nic0.setAttachmentType(NetworkAttachmentType.NAT);
                nic0.setEnabled(true);

                INetworkAdapter nic1 = machine.getNetworkAdapter(1l);
                nic1.setAttachmentType(NetworkAttachmentType.Internal);
                nic1.setEnabled(true);

                INetworkAdapter nic2 = machine.getNetworkAdapter(2l);
                nic2.setAttachmentType(publicIfConfig.getAttachmentType());
                if (NetworkAttachmentType.HostOnly.equals(publicIfConfig.getAttachmentType())) {
                    nic2.setHostOnlyInterface(publicIfConfig.getInterfaceName());
                } else {
                    nic2.setBridgedInterface(publicIfConfig.getInterfaceName());
                }
                nic2.setEnabled(true);

                if (StringUtils.isNotEmpty(hostSharedFolder)) {
                    machine.createSharedFolder(CLOUDIFY_SHARED_FOLDER, hostSharedFolder, true, true);
                }

                IStorageController storageController = machine.getStorageControllerByName(storageControllerName);
                storageController.setPortCount(storageController.getMaxPortCount());

                machine.saveSettings();
            } finally {
                session.unlockMachine();
            }
        } finally {
            COMPUTE_MUTEX.unlock();
        }

        return result;
    }

    public void destroy(String machineGuid, long endTime) throws Exception {
        logger.log(Level.INFO, "Trying to destroy VM '" + machineGuid + "'");

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMachine m = manager.getVBox().findMachine(machineGuid);

        COMPUTE_MUTEX.lock();

        try {

            List<IMedium> mediums = m.unregister(CleanupMode.Full);

            IProgress progress = m.delete(mediums);
            long timeLeft = endTime - System.currentTimeMillis();
            progress.waitForCompletion((int) timeLeft);

            if (!progress.getCompleted()) {
                throw new TimeoutException("Unable to delete VM: Timeout");
            }
            if (progress.getResultCode() != 0) {
                throw new VirtualBoxException("Unable to delete VM: " + progress.getErrorInfo().getText());
            }
        } finally {
            COMPUTE_MUTEX.unlock();
        }
    }

    public void start(String machineGuid, String login, String password, boolean headless, long endTime) throws Exception {

        logger.log(Level.INFO, "Trying to start and setup VM '" + machineGuid + "'");

        VirtualBoxManager manager = this.getVirtualBoxManager();

        long timeLeft = endTime - System.currentTimeMillis();

        // start the VM
        boolean started = manager.startVm(machineGuid, headless ? "headless" : "gui", (int) timeLeft);
        if (!started) {
            throw new VirtualBoxException("Unable to start VM:");
        }

        IMachine machine = manager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());

        // Wait for the guest OS to be ready
        boolean isReady = false;
        while (!isReady && System.currentTimeMillis() < endTime) {
            try {
                guest.ping(machineGuid, login, password, endTime);
                isReady = true;
            } catch (Exception ex) {
                logger.log(Level.FINE, "OS not ready yet", ex);

                if (System.currentTimeMillis() > endTime) {
                    throw new TimeoutException("timeout creating server.");
                }
                Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
            }
        }

        // Create a script to update the hostname
        guest.updateHostname(machineGuid, login, password, machine.getName(), endTime);
    }

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception {

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMachine machine = manager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.grantAccessToSharedFolder(machineGuid, login, password, endTime);
    }

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privateAddrIP, long endTime)
            throws Exception {

        logger.log(Level.INFO, "Trying to update network interfaces on VM '" + machineGuid + "'");

        VirtualBoxManager manager = this.getVirtualBoxManager();
        IMachine machine = manager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateNetworkingInterfaces(machineGuid, login, password, privateAddrIP, endTime);
    }

    public void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception {
        VirtualBoxManager manager = this.getVirtualBoxManager();
        IMachine machine = manager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.runCommandsBeforeBootstrap(machineGuid, login, password, endTime);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception {

        logger.log(Level.INFO, "Trying to update hosts on VM '" + machineGuid + "'");

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMachine machine = manager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateHosts(machineGuid, login, password, hosts, endTime);
    }

    public void stop(String machineGuid, long endTime) throws Exception {

        logger.log(Level.INFO, "Trying to stop VM '" + machineGuid + "'");

        VirtualBoxManager manager = this.getVirtualBoxManager();
        IMachine m = manager.getVBox().findMachine(machineGuid);

        COMPUTE_MUTEX.lock();
        try {

            ISession session = manager.openMachineSession(m);
            try {
                IConsole console = session.getConsole();

                IProgress progress = console.powerDown();

                long timeLeft = endTime - System.currentTimeMillis();
                progress.waitForCompletion((int) timeLeft);

                if (!progress.getCompleted()) {
                    throw new TimeoutException("Unable to shutdown vm " + machineGuid + ": Timeout");
                }

                if (progress.getResultCode() != 0) {
                    throw new VirtualBoxException("Unable to shutdown vm " + machineGuid + ": " + progress.getErrorInfo().getText());
                }
            } finally {
                try {
                    session.unlockMachine();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Could not unlock machine: " + e.getMessage());
                }
            }
        } finally {
            COMPUTE_MUTEX.unlock();
        }
    }

    public String getPublicAddressIP(String machineNameOrId) throws Exception {
        VirtualBoxManager manager = this.getVirtualBoxManager();
        IMachine machine = manager.getVBox().findMachine(machineNameOrId);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        return guest.getPublicAddressIP(machineNameOrId);
    }

    public String getPrivateAddressIP(String machineNameOrId) throws Exception {
        VirtualBoxManager manager = this.getVirtualBoxManager();
        IMachine machine = manager.getVBox().findMachine(machineNameOrId);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        return guest.getPrivateAddressIP(machineNameOrId);
    }

    public VirtualBoxVolumeInfo[] getAllVolumesInfo() throws VirtualBoxException {
        return this.getAllVolumesInfo(null);
    }

    public VirtualBoxVolumeInfo[] getAllVolumesInfo(String prefix) throws VirtualBoxException {

        ArrayList<VirtualBoxVolumeInfo> result = new ArrayList<VirtualBoxVolumeInfo>();

        VirtualBoxManager manager = this.getVirtualBoxManager();

        for (IMedium v : manager.getVBox().getHardDisks()) {

            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(v.getId());
            info.setName(v.getName());
            info.setLocation(v.getLocation());
            info.setSize((int) (v.getSize() / (1024 * 1024 * 1024)));

            if (prefix != null) {
                if (info.getName().startsWith(prefix)) {
                    result.add(info);
                }
            }
            else {
                result.add(info);
            }
        }

        return result.toArray(new VirtualBoxVolumeInfo[0]);
    }

    public VirtualBoxVolumeInfo getVolumeInfo(String name) throws VirtualBoxException {
        for (VirtualBoxVolumeInfo info : getAllVolumesInfo()) {
            if (info.getName().equals(name)) {
                return info;
            }
        }

        return null;
    }

    public VirtualBoxVolumeInfo createVolume(String prefix, String path, int size, String hardDiskType, long endTime) throws TimeoutException,
            VirtualBoxException {

        STORAGE_MUTEX.lock();

        VirtualBoxManager manager = this.getVirtualBoxManager();

        try {
            int lastId = -1;
            try {
                for (VirtualBoxVolumeInfo v : getAllVolumesInfo(prefix)) {

                    String idString = v.getName().substring(prefix.length());
                    int id = Integer.parseInt(idString);
                    if (id > lastId) {
                        lastId = id;
                    }
                }
            } catch (NumberFormatException e) {
                throw new VirtualBoxException("Unable to list exiting volumes", e);
            } catch (VirtualBoxException e) {
                throw e;
            }

            String name = prefix + (lastId + 1);

            String fullPath = name;
            if (path != null && path.length() > 0) {
                fullPath = new File(path, name).getAbsolutePath();
            }

            IMedium medium = manager.getVBox().createHardDisk(hardDiskType, fullPath);
            Long sizeInBytes = size * 1024l * 1024l * 1024l;
            IProgress progress = medium.createBaseStorage(sizeInBytes, (long) org.virtualbox_4_2.MediumVariant.Standard.value());

            long timeLeft = endTime - System.currentTimeMillis();
            progress.waitForCompletion((int) timeLeft);

            if (!progress.getCompleted()) {
                throw new TimeoutException("Unable to create volume " + fullPath + ": Timeout");
            }

            if (progress.getResultCode() != 0) {
                throw new VirtualBoxException("Unable to create volume " + fullPath + ": " + progress.getErrorInfo().getText());
            }

            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(medium.getId());
            info.setName(medium.getName());
            info.setLocation(medium.getLocation());
            info.setSize(size);

            return info;
        } finally {
            STORAGE_MUTEX.unlock();
        }
    }

    public void attachVolume(String machineGuid, String volumeName, int controllerPort, long endTime) throws Exception {

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMedium medium = null;
        for (IMedium m : manager.getVBox().getHardDisks()) {
            if (m.getName().equals(volumeName)) {
                medium = m;
                break;
            }
        }

        IMachine m = manager.getVBox().findMachine(machineGuid);
        ISession session = manager.openMachineSession(m);
        m = session.getMachine();

        try {
            m.attachDevice(storageControllerName, controllerPort, 0, DeviceType.HardDisk, medium);

            m.saveSettings();
        } finally {
            session.unlockMachine();
        }
    }

    public void detachVolume(String machineGuid, String volumeName, long endTime) throws Exception {
        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMachine m = manager.getVBox().findMachine(machineGuid);

        int controllerPort = -1;
        for (IMediumAttachment mediumAttachment : m.getMediumAttachmentsOfController(storageControllerName)) {
            if (mediumAttachment.getMedium().getName().equals(volumeName)) {
                controllerPort = mediumAttachment.getPort();
                break;
            }
        }

        if (controllerPort == -1) {
            throw new VirtualBoxException("Volume " + volumeName + " not attached to VM " + m.getId());
        }

        ISession session = manager.openMachineSession(m);
        m = session.getMachine();

        try {
            m.attachDevice(storageControllerName, controllerPort, 0, DeviceType.HardDisk, null);

            m.saveSettings();
        } finally {
            session.unlockMachine();
        }
    }

    public VirtualBoxVolumeInfo[] getVolumeInfoByMachine(String machineName) throws VirtualBoxException {

        VirtualBoxManager manager = this.getVirtualBoxManager();

        IMachine m = manager.getVBox().findMachine(machineName);

        ArrayList<VirtualBoxVolumeInfo> result = new ArrayList<VirtualBoxVolumeInfo>();

        for (IMediumAttachment mediumAttachment : m.getMediumAttachmentsOfController(storageControllerName)) {
            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(mediumAttachment.getMedium().getId());
            info.setName(mediumAttachment.getMedium().getName());
            info.setLocation(mediumAttachment.getMedium().getLocation());
            info.setSize((int) (mediumAttachment.getMedium().getSize() / (1024 * 1024 * 1024)));

            result.add(info);
        }

        return result.toArray(new VirtualBoxVolumeInfo[0]);
    }

    public void deleteVolume(String volumeId, long endTime) throws VirtualBoxException, TimeoutException {
        IMedium medium = null;

        VirtualBoxManager manager = this.getVirtualBoxManager();

        for (IMedium m : manager.getVBox().getHardDisks()) {
            if (m.getId().equals(volumeId)) {
                medium = m;
                break;
            }
        }

        if (medium == null) {
            throw new VirtualBoxException("No volume with ID " + volumeId);
        }

        IProgress progress = medium.deleteStorage();

        long timeLeft = endTime - System.currentTimeMillis();
        progress.waitForCompletion((int) timeLeft);

        if (!progress.getCompleted()) {
            throw new TimeoutException("Unable to delete volume " + volumeId + ": Timeout");
        }

        if (progress.getResultCode() != 0) {
            throw new VirtualBoxException("Unable to delete volume " + volumeId + ": " + progress.getErrorInfo().getText());
        }
    }

    public void setStorageControllerName(String storageControllerName) {
        this.storageControllerName = storageControllerName;
    }
}
