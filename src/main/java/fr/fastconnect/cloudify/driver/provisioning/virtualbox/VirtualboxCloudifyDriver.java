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

import org.apache.commons.lang.StringUtils;
import org.cloudifysource.dsl.cloud.Cloud;
import org.cloudifysource.esc.driver.provisioning.CloudDriverSupport;
import org.cloudifysource.esc.driver.provisioning.CloudProvisioningException;
import org.cloudifysource.esc.driver.provisioning.MachineDetails;
import org.cloudifysource.esc.driver.provisioning.ProvisioningDriver;
import org.cloudifysource.esc.driver.provisioning.context.ProvisioningDriverClassContext;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxHostOnlyInterface;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxMachineInfo;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxService;

public class VirtualboxCloudifyDriver extends CloudDriverSupport implements ProvisioningDriver
{
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualboxCloudifyDriver.class.getName());

    private static final String VBOX_BOXES_PATH = "vbox.boxes.path";
    private static final String VBOX_HOSTONLYIF = "vbox.hostonlyinterface";
    private static final String VBOX_HEADLESS = "vbox.headless";
    private static final String VBOX_URL = "vbox.serverUrl";
    private static final String VBOX_SHARED_FOLDER = "vbox.sharedFolder";

    private static final ReentrantLock mutex = new ReentrantLock();

    private String boxesPath;
    private String virtualBoxUrl;

    private String hostonlyifIP;
    private String hostonlyifMask;
    private String hostonlyifName;
    private String hostSharedFolder;

    private boolean headless;

    private String baseIp;
    private String hostsFile;

    private String serverNamePrefix;

    private VirtualBoxService virtualBoxService = new VirtualBoxService();

    private void checkHostOnlyInterface() throws Exception {

        if (this.hostonlyifIP != null) {
            return;
        }

        // connect using the default URL
        this.virtualBoxService.connect(this.virtualBoxUrl, this.cloud.getUser().getUser(), this.cloud.getUser().getApiKey());

        // get the HostOnly Interface
        VirtualBoxHostOnlyInterface hostonlyif = virtualBoxService.getHostOnlyInterface(this.hostonlyifName);

        if (hostonlyif == null) {
            throw new Exception("No HostOnlyInterface found: " + hostonlyif);
        }

        this.hostonlyifIP = hostonlyif.getIp();
        this.hostonlyifMask = hostonlyif.getMask();
        this.baseIp = this.hostonlyifIP.substring(0, this.hostonlyifIP.lastIndexOf('.'));

        // generate a hosts file with fixed IP
        hostsFile = "127.0.0.1  localhost\n";

        for (int cpt = 2; cpt < 10; cpt++) {
            hostsFile += this.baseIp + "." + cpt + "\t" + this.cloud.getProvider().getManagementGroup() + (cpt - 1) + "\n";
        }

        for (int cpt = 10; cpt < 30; cpt++) {
            hostsFile += this.baseIp + "." + cpt + "\t" + this.cloud.getProvider().getMachineNamePrefix() + (cpt - 9) + "\n";
        }
    }

    public void setProvisioningDriverClassContext(
            ProvisioningDriverClassContext provisioningDriverClassContext) {
    }

    public void close() {
    }

    @Override
    public void setConfig(Cloud cloud, String templateName, boolean management, String serviceName) {
        super.setConfig(cloud, templateName, management, serviceName);

        if (this.management) {
            this.serverNamePrefix = this.cloud.getProvider().getManagementGroup();
        } else {
            this.serverNamePrefix = this.cloud.getProvider().getMachineNamePrefix();
        }

        this.boxesPath = (String) this.cloud.getCustom().get(VBOX_BOXES_PATH);
        if (this.boxesPath == null) {
            throw new IllegalArgumentException("Custom field '" + VBOX_BOXES_PATH + "' must be set");
        }

        this.hostonlyifName = (String) this.cloud.getCustom().get(VBOX_HOSTONLYIF);
        if (this.hostonlyifName == null) {
            throw new IllegalArgumentException("Custom field '" + VBOX_HOSTONLYIF + "' must be set");
        }

        this.virtualBoxUrl = (String) this.cloud.getCustom().get(VBOX_URL);
        if (this.virtualBoxUrl == null) {
            throw new IllegalArgumentException("Custom field '" + VBOX_URL + "' must be set");
        }

        this.hostSharedFolder = (String) this.cloud.getCustom().get(VBOX_SHARED_FOLDER);

        String headlessString = (String) this.cloud.getCustom().get(VBOX_HEADLESS);
        if (headlessString == null) {
            this.headless = true;
        }
        else {
            this.headless = Boolean.parseBoolean(headlessString);
        }
    }

    public MachineDetails startMachine(String locationId, long duration, TimeUnit unit)
            throws TimeoutException, CloudProvisioningException {

        logger.info("Start VBox machine");

        try {
            checkHostOnlyInterface();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to create host interface", ex);
            throw new CloudProvisioningException("Unable to create host interface", ex);
        }

        MachineDetails md;
        String machineName = serverNamePrefix;
        String machineNamePrefix = (String) this.template.getCustom().get("machineNamePrefix");
        if (!StringUtils.isEmpty(machineNamePrefix)) {
            machineName = machineNamePrefix;
        }

        int numberOfCores = this.template.getNumberOfCores();
        int machineMemoryMB = this.template.getMachineMemoryMB();

        String machineTemplate = this.template.getImageId();

        File boxesPathFile = new File(this.boxesPath);
        File machineTemplateFolderFile = new File(boxesPathFile, machineTemplate);
        File machineTemplateOvfFile = new File(machineTemplateFolderFile, "box.ovf");

        mutex.lock();
        try {
            VirtualBoxMachineInfo vboxInfo;
            int id = 0;
            String addressIP;
            try {
                // Retrieve the existing VMs
                VirtualBoxMachineInfo[] infos = virtualBoxService.getAll();

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
                }
                else {
                    lastIP = 9 + id;
                }
                addressIP = this.baseIp + "." + lastIP;

                // TODO : run in a thread, so detect the Timeout

                // go to the folder with "boxes"
                vboxInfo = virtualBoxService.create(
                        machineTemplateOvfFile.toString(),
                        this.serverNamePrefix + id,
                        numberOfCores,
                        machineMemoryMB,
                        this.hostonlyifName,
                        this.hostSharedFolder);

                // we can release the mutex now, the VM is created
            } finally {
                mutex.unlock();
            }
            virtualBoxService.start(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    headless);

            virtualBoxService.updateNetworkingInterfaces(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    addressIP,
                    this.hostonlyifMask);

            virtualBoxService.updateHosts(
                    vboxInfo.getMachineName(),
                    this.template.getUsername(),
                    this.template.getPassword(),
                    this.hostsFile);

            if (this.hostSharedFolder != null && this.hostSharedFolder.length() > 0) {
                virtualBoxService.grantAccessToSharedFolder(
                        vboxInfo.getMachineName(),
                        this.template.getUsername(),
                        this.template.getPassword());
            }

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
            md.setPrivateAddress(addressIP);
            md.setPublicAddress(addressIP);

        } catch (final Exception e) {
            throw new CloudProvisioningException(e);
        }
        return md;
    }

    public MachineDetails[] startManagementMachines(long duration, TimeUnit timeout)
            throws TimeoutException, CloudProvisioningException {

        try {
            checkHostOnlyInterface();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to create host interface", ex);
            throw new CloudProvisioningException("Unable to create host interface", ex);
        }

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
            logger.warning("Provisioning of management machines failed, the following node will be shut down: "
                    + machines);
            for (final MachineDetails machineDetails : machines) {
                try {
                    // TODO : do it in a thread to detect TIMEOUT
                    virtualBoxService.stop(machineDetails.getMachineId());
                    virtualBoxService.destroy(machineDetails.getMachineId());
                } catch (final Exception e) {
                    logger.log(Level.SEVERE,
                            "While shutting down machine after provisioning of management machines failed, "
                                    + "shutdown of node: " + machineDetails.getMachineId()
                                    + " failed. This machine may be leaking. Error was: " + e.getMessage(), e);
                }
            }

            throw new CloudProvisioningException(
                    "Failed to launch management machines: " + firstException.getMessage(), firstException);
        }
    }

    public boolean stopMachine(String machineIp, long duration, TimeUnit timeout)
            throws InterruptedException, TimeoutException,
            CloudProvisioningException {

        logger.info("Stop VBox machine");

        try {

            if (!machineIp.startsWith(this.baseIp)) {
                throw new CloudProvisioningException("Invalid IP: " + machineIp + ", should start with " + this.baseIp);
            }

            checkHostOnlyInterface();

            String lastIpString = machineIp.substring(this.baseIp.length() + 1);
            Integer lastIp = Integer.parseInt(lastIpString);

            String machineName = this.serverNamePrefix + (lastIp - 1);

            VirtualBoxMachineInfo info = this.virtualBoxService.getInfo(machineName);
            this.virtualBoxService.stop(info.getGuid());
            this.virtualBoxService.destroy(info.getGuid());

            return true;
        } catch (Exception ex) {
            // TODO : logs
            return false;
        }
    }

    public void stopManagementMachines() throws TimeoutException,
            CloudProvisioningException {

        try {
            checkHostOnlyInterface();

            for (int cpt = 1; cpt <= this.cloud.getProvider().getNumberOfManagementMachines(); cpt++) {
                VirtualBoxMachineInfo info = this.virtualBoxService.getInfo(serverNamePrefix + cpt);
                this.virtualBoxService.stop(info.getGuid());
                this.virtualBoxService.destroy(info.getGuid());
            }
        } catch (final Exception e) {
            throw new CloudProvisioningException("Failed to shut down managememnt machines", e);
        }
    }

    public String getCloudName() {
        return this.cloud.getName();
    }
}