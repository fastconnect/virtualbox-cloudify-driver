package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.cloudifysource.dsl.cloud.Cloud;
import org.cloudifysource.esc.driver.provisioning.CloudDriverSupport;
import org.cloudifysource.esc.driver.provisioning.CloudProvisioningException;
import org.cloudifysource.esc.driver.provisioning.MachineDetails;
import org.cloudifysource.esc.driver.provisioning.ProvisioningDriver;
import org.cloudifysource.esc.driver.provisioning.context.ProvisioningDriverClassContext;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxMachineInfo;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxService;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxService42;

public class VirtualboxCloudifyDriver extends CloudDriverSupport implements ProvisioningDriver {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualboxCloudifyDriver.class.getName());

    public static final String VBOX_BOXES_PATH = "vbox.boxes.path";
    public static final String VBOX_BOXES_PROVIDER = "vbox.boxes.provider";
    public static final String VBOX_STORAGE_CONTROLLER_NAME = "vbox.storageControllerName";
    public static final String VBOX_HOSTONLYIF = "vbox.hostOnlyInterface";
    public static final String VBOX_BRIDGEDIF = "vbox.bridgedInterface";
    public static final String VBOX_HEADLESS = "vbox.headless";
    public static final String VBOX_URL = "vbox.serverUrl";
    public static final String VBOX_SHARED_FOLDER = "vbox.sharedFolder";
    public static final String VBOX_DESTROY_MACHINES = "vbox.destroyMachines";

    private static final int DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 5 * 60 * 1000; // 5 minutes
    private static final String DEFAULT_BOXES_PROVIDER = "virtualbox";

    private static final ReentrantLock mutex = new ReentrantLock();

    private String boxesPath;
    private String boxesProvider;
    private String virtualBoxUrl;

    private PublicInterfaceConfig publicIfConfig;

    private String hostSharedFolder;
    private boolean destroyMachines;

    private boolean headless;

    private String serverNamePrefix;

    private VirtualBoxService virtualBoxService = new VirtualBoxService42();

    private void checkHostOnlyInterface() throws Exception {
        checkHostOnlyInterface(false);
    }

    private void checkHostOnlyInterface(boolean force) throws Exception {
        if (!virtualBoxService.isConnected() || force) {
            // connect using the default URL
            this.virtualBoxService.connect(this.virtualBoxUrl, this.cloud.getUser().getUser(), this.cloud.getUser().getApiKey());
        }
    }

    private String createHostsFileContent() {
        // generate a hosts file with fixed IP
        String hostsFile = "\r\n127.0.0.1\tlocalhost\r\n";
        for (int cpt = 2; cpt < 10; cpt++) {
            hostsFile += PrivateInterfaceConfig.PRIVATE_BASE_IP + "." + cpt + "\t" + this.cloud.getProvider().getManagementGroup() + (cpt - 1) + "\r\n";
        }
        for (int cpt = 10; cpt < 30; cpt++) {
            hostsFile += PrivateInterfaceConfig.PRIVATE_BASE_IP + "." + cpt + "\t" + this.cloud.getProvider().getMachineNamePrefix() + (cpt - 9) + "\r\n";
        }

        return hostsFile;
    }

    public void setProvisioningDriverClassContext(
            ProvisioningDriverClassContext provisioningDriverClassContext) {
    }

    public void close() {
    }

    @Override
    public void setConfig(Cloud cloud, String cloudTemplate, boolean management, String serviceName) {
        super.setConfig(cloud, cloudTemplate, management, serviceName);

        if (StringUtils.isEmpty(this.template.getUsername())) {
            logger.log(Level.WARNING, "Username is not set in the template " + cloudTemplate);
        }

        if (this.management) {
            this.serverNamePrefix = this.cloud.getProvider().getManagementGroup();
        } else {
            this.serverNamePrefix = this.cloud.getProvider().getMachineNamePrefix();
        }

        this.boxesPath = (String) this.cloud.getCustom().get(VBOX_BOXES_PATH);
        if (StringUtils.isEmpty(this.boxesPath)) {
            throw new IllegalArgumentException("Custom field '" + VBOX_BOXES_PATH + "' must be set");
        }
        String boxesProviderString = (String) this.cloud.getCustom().get(VBOX_BOXES_PROVIDER);
        this.boxesProvider = StringUtils.isEmpty(boxesProviderString) ? null : boxesProviderString;

        String hostonlyifName = (String) this.cloud.getCustom().get(VBOX_HOSTONLYIF);
        String bridgeifName = (String) this.cloud.getCustom().get(VBOX_BRIDGEDIF);
        if (StringUtils.isNotEmpty(hostonlyifName)) {
            publicIfConfig = new PublicInterfaceConfig();
            publicIfConfig.setHostOnlyInterface(hostonlyifName);
        } else if (StringUtils.isNotEmpty(bridgeifName)) {
            publicIfConfig = new PublicInterfaceConfig();
            publicIfConfig.setBridgeInterface(bridgeifName);
        } else {
            throw new IllegalArgumentException("One of the custom field '" + VBOX_HOSTONLYIF + "' or '" + VBOX_BRIDGEDIF + "' must be set");
        }

        this.virtualBoxUrl = (String) this.cloud.getCustom().get(VBOX_URL);
        if (this.virtualBoxUrl == null) {
            throw new IllegalArgumentException("Custom field '" + VBOX_URL + "' must be set");
        }

        this.hostSharedFolder = (String) this.cloud.getCustom().get(VBOX_SHARED_FOLDER);

        String headlessString = (String) this.cloud.getCustom().get(VBOX_HEADLESS);
        this.headless = headlessString == null ? true : BooleanUtils.toBoolean(headlessString);

        String destroyString = (String) this.cloud.getCustom().get(VBOX_DESTROY_MACHINES);
        this.destroyMachines = destroyString == null ? true : BooleanUtils.toBoolean(destroyString);

        String storageControllerName = StringUtils.defaultIfEmpty((String) this.cloud.getCustom().get(VBOX_STORAGE_CONTROLLER_NAME), "SATA Controller");
        storageControllerName = StringUtils.defaultIfEmpty((String) this.template.getCustom().get(VBOX_STORAGE_CONTROLLER_NAME), storageControllerName);
        virtualBoxService.setStorageControllerName(storageControllerName);
        logger.log(Level.INFO, "[VBOX] Storage Controller Name = " + storageControllerName);
    }

    public MachineDetails startMachine(String locationId, long duration, TimeUnit unit)
            throws TimeoutException, CloudProvisioningException {

        logger.info(String.format("Start VBox machine (timeout = %s ms)", unit.toMillis(duration)));

        final long endTime = System.currentTimeMillis() + unit.toMillis(duration);

        try {
            checkHostOnlyInterface(true);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to create host interface", ex);
            throw new CloudProvisioningException("Unable to create host interface", ex);
        }

        MachineDetails md;
        String machineName = this.serverNamePrefix;
        String machineNamePrefix = (String) this.template.getCustom().get("machineNamePrefix");
        if (!StringUtils.isEmpty(machineNamePrefix)) {
            machineName = machineNamePrefix;
        }

        int numberOfCores = this.template.getNumberOfCores();
        int machineMemoryMB = this.template.getMachineMemoryMB();
        String machineTemplate = this.template.getImageId();
        File machineTemplateOvfFile = this.getOvfFile(machineTemplate);

        mutex.lock();
        try {
            VirtualBoxMachineInfo vboxInfo;
            int id = 0;
            String privateAddrIP;
            try {
                // Retrieve the existing VMs
                VirtualBoxMachineInfo[] infos = this.virtualBoxService.getAll();

                for (VirtualBoxMachineInfo info : infos) {
                    if (info.getMachineName().startsWith(machineName)) {
                        // TODO : try catch parsing int
                        int currentId = Integer.parseInt(info.getMachineName().substring(machineName.length()));
                        if (currentId > id) {
                            id = currentId;
                        }
                    }
                }

                // increment the id
                id++;

                int lastIP = 0;
                if (this.management) {
                    lastIP = 1 + id;
                } else {
                    lastIP = 9 + id;
                }
                privateAddrIP = PrivateInterfaceConfig.PRIVATE_BASE_IP + "." + lastIP;

                // go to the folder with "boxes"
                vboxInfo = this.virtualBoxService.create(
                        machineTemplateOvfFile.toString(),
                        this.serverNamePrefix + id,
                        numberOfCores,
                        machineMemoryMB,
                        this.publicIfConfig,
                        this.hostSharedFolder,
                        endTime);

                // we can release the mutex now, the VM is created
            } finally {
                mutex.unlock();
            }

            this.virtualBoxService.start(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    headless,
                    endTime);

            this.virtualBoxService.updateHosts(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    this.createHostsFileContent(),
                    endTime);

            this.virtualBoxService.updateNetworkingInterfaces(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    privateAddrIP,
                    endTime);

            this.virtualBoxService.grantAccessToSharedFolder(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    endTime);

            this.virtualBoxService.runCommandsBeforeBootstrap(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    endTime);

            String publicAddrIP = this.virtualBoxService.getPublicAddressIP(vboxInfo.getGuid());
            String machinePrivateAddrIP = this.virtualBoxService.getPrivateAddressIP(vboxInfo.getGuid());

            md = new MachineDetails();
            md.setMachineId(vboxInfo.getGuid());
            md.setAgentRunning(false);
            md.setCloudifyInstalled(false);
            md.setInstallationDirectory(null);
            md.setRemoteDirectory(this.template.getRemoteDirectory());
            md.setRemoteUsername(this.template.getUsername());
            md.setRemotePassword(this.template.getPassword());
            md.setRemoteExecutionMode(this.template.getRemoteExecution());
            md.setFileTransferMode(this.template.getFileTransfer());
            md.setScriptLangeuage(this.template.getScriptLanguage());
            md.setPrivateAddress(machinePrivateAddrIP);
            md.setPublicAddress(publicAddrIP);

        } catch (final Exception e) {
            throw new CloudProvisioningException(e);
        }
        return md;
    }

    private File getOvfFile(String machineTemplate) throws CloudProvisioningException {
        File boxesPathFile = new File(this.boxesPath);
        File machineTemplateFolderFile = new File(boxesPathFile, machineTemplate);
        File machineTemplateOvfFile = null;
        if (this.boxesProvider != null) {
            machineTemplateFolderFile = new File(machineTemplateFolderFile, this.boxesProvider);
            machineTemplateOvfFile = new File(machineTemplateFolderFile, "box.ovf");
        } else {
            machineTemplateOvfFile = new File(machineTemplateFolderFile, "box.ovf");
            if (!machineTemplateOvfFile.isFile()) {
                machineTemplateFolderFile = new File(machineTemplateFolderFile, DEFAULT_BOXES_PROVIDER);
                machineTemplateOvfFile = new File(machineTemplateFolderFile, "box.ovf");
            }
        }
        if (!machineTemplateOvfFile.isFile()) {
            throw new CloudProvisioningException("Wrong path to 'box.ovf' file: " + machineTemplateOvfFile.getAbsolutePath());
        }
        return machineTemplateOvfFile;
    }

    public MachineDetails[] startManagementMachines(long duration, TimeUnit timeout)
            throws TimeoutException, CloudProvisioningException {

        final int numOfManagementMachines = cloud.getProvider().getNumberOfManagementMachines();

        final ExecutorService executor = Executors.newFixedThreadPool(cloud.getProvider().getNumberOfManagementMachines());

        // TODO : don't create Management machine if already exists

        try {
            return doStartManagement(duration, timeout, numOfManagementMachines, executor);
        } finally {
            executor.shutdown();
        }
    }

    private MachineDetails[] doStartManagement(final long duration, final TimeUnit timeout, int numOfManagementMachines,
            ExecutorService executor) throws CloudProvisioningException {

        final long endTime = System.currentTimeMillis() + timeout.toMillis(duration);

        // launch machine on a thread
        final List<Future<MachineDetails>> list = new ArrayList<Future<MachineDetails>>(numOfManagementMachines);
        for (int i = 0; i < numOfManagementMachines; i++) {
            final Future<MachineDetails> task = executor.submit(new Callable<MachineDetails>() {

                // @Override
                public MachineDetails call()
                        throws Exception {
                    return startMachine(null, duration, timeout);
                }

            });
            list.add(task);
        }

        // get the machines
        Exception firstException = null;
        final List<MachineDetails> machines = new ArrayList<MachineDetails>(numOfManagementMachines);
        for (final Future<MachineDetails> future : list) {
            try {
                machines.add(future.get());
            } catch (final Exception e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException == null) {
            return machines.toArray(new MachineDetails[machines.size()]);
        } else {
            // in case of an exception, clear the machines
            logger.warning("Provisioning of management machines failed, the following node will be shut down: " + machines);

            for (final MachineDetails machineDetails : machines) {
                try {
                    // TODO : do it in a thread to detect TIMEOUT
                    this.virtualBoxService.stop(machineDetails.getMachineId(), endTime);
                    if (this.destroyMachines) {
                        this.virtualBoxService.destroy(machineDetails.getMachineId(), endTime);
                    } else {
                        logger.warning(String.format("According to the configuration, the machine '%s' is not destroy", machineDetails.getMachineId()));
                    }
                } catch (final Exception e) {
                    logger.log(
                            Level.SEVERE,
                            "While shutting down machine after provisioning of management machines failed, "
                                    + "shutdown of node: " + machineDetails.getMachineId()
                                    + " failed. This machine may be leaking. Error was: " + e.getMessage(), e);
                }
            }

            throw new CloudProvisioningException("Failed to launch management machines: " + firstException.getMessage(), firstException);
        }
    }

    public boolean stopMachine(String machineIp, long duration, TimeUnit timeout)
            throws InterruptedException, TimeoutException,
            CloudProvisioningException {

        final long endTime = System.currentTimeMillis() + timeout.toMillis(duration);

        logger.info("Stop VBox machine");

        try {

            if (!machineIp.startsWith(PrivateInterfaceConfig.PRIVATE_BASE_IP)) {
                throw new CloudProvisioningException("Invalid IP: " + machineIp + ", should start with " + PrivateInterfaceConfig.PRIVATE_BASE_IP);
            }

            checkHostOnlyInterface();

            String lastIpString = machineIp.substring(PrivateInterfaceConfig.PRIVATE_BASE_IP.length() + 1);
            Integer lastIP = Integer.parseInt(lastIpString);

            if (this.management) {
                lastIP = lastIP - 1;
            } else {
                lastIP = lastIP - 9;
            }

            String machineName = this.serverNamePrefix + lastIP;

            logger.log(Level.INFO, "Stoping machine " + machineName);
            VirtualBoxMachineInfo info = this.virtualBoxService.getInfo(machineName);
            try {
                this.virtualBoxService.stop(info.getGuid(), endTime);
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        String.format("Couldn't stop the machine (%s,%s) properly, but it will be killed.", info.getMachineName(), info.getGuid()), e);
            }
            if (this.destroyMachines) {
                this.virtualBoxService.destroy(info.getGuid(), endTime);
            } else {
                logger.warning(String.format("According to the configuration, the machine '%s' is not destroy", info.getGuid()));
            }

            return true;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to stop machine " + machineIp, ex);
            return false;
        }
    }

    public void stopManagementMachines() throws TimeoutException,
            CloudProvisioningException {

        final long endTime = System.currentTimeMillis() + DEFAULT_SHUTDOWN_TIMEOUT_MILLIS;

        try {
            checkHostOnlyInterface();

            for (int cpt = 1; cpt <= this.cloud.getProvider().getNumberOfManagementMachines(); cpt++) {
                VirtualBoxMachineInfo info = this.virtualBoxService.getInfo(this.serverNamePrefix + cpt);
                if (info != null) {
                    try {
                        this.virtualBoxService.stop(info.getGuid(), endTime);
                    } catch (Exception e) {
                        logger.log(Level.WARNING,
                                String.format("Couldn't stop the machine (%s,%s) properly, but it will be killed.", info.getMachineName(), info.getGuid()), e);
                    }
                    if (this.destroyMachines) {
                        this.virtualBoxService.destroy(info.getGuid(), endTime);
                    } else {
                        logger.warning(String.format("According to the configuration, the machine '%s' is not destroy", info.getGuid()));
                    }
                }
            }
        } catch (final Exception e) {
            throw new CloudProvisioningException("Failed to shut down management machines", e);
        }
    }

    public String getCloudName() {
        return this.cloud.getName();
    }

    public Object getComputeContext() {
        try {
            this.checkHostOnlyInterface();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unable to check HostOnly Interface", e);
        }
        VirtualBoxDriverContext context = new VirtualBoxDriverContext();
        context.setBaseIP(PrivateInterfaceConfig.PRIVATE_BASE_IP);
        context.setServerNamePrefix(this.serverNamePrefix);
        context.setVirtualBoxService(this.virtualBoxService);
        context.setManagement(this.management);
        return context;
    }

    public void onServiceUninstalled(long duration, TimeUnit unit) throws InterruptedException, TimeoutException, CloudProvisioningException {
        this.virtualBoxService.disconnect();
    }
}