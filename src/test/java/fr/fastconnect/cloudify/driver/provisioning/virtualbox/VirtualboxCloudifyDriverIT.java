package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.cloudifysource.dsl.cloud.Cloud;
import org.cloudifysource.dsl.cloud.CloudProvider;
import org.cloudifysource.dsl.cloud.CloudUser;
import org.cloudifysource.dsl.cloud.FileTransferModes;
import org.cloudifysource.dsl.cloud.RemoteExecutionModes;
import org.cloudifysource.dsl.cloud.ScriptLanguages;
import org.cloudifysource.dsl.cloud.compute.CloudCompute;
import org.cloudifysource.dsl.cloud.compute.ComputeTemplate;
import org.junit.Before;
import org.junit.Test;

public class VirtualboxCloudifyDriverIT {

    private static final String VBOX_STORAGE_CONTROLLER_NAME = "vbox.storageControllerName";
    private static final String VBOX_BOXES_PATH = "vbox.boxes.path";
    private static final String VBOX_HOSTONLYIF = "vbox.hostonlyinterface";
    private static final String VBOX_HEADLESS = "vbox.headless";
    private static final String VBOX_URL = "vbox.serverUrl";
    private static final String VBOX_SHARED_FOLDER = "vbox.sharedFolder";
    private static final String VBOX_PRIVATE_INTERFACE_NAME = "vbox.privateInterfaceName";
    private static final String VBOX_PUBLIC_INTERFACE_NAME = "vbox.publicInterfaceName";

    private VirtualboxCloudifyDriver driver = new VirtualboxCloudifyDriver();

    private String templateName = "myTemplate";
    private Cloud cloud;;

    @Before
    public void before() {
        cloud = this.createCloud(null);
        driver.setConfig(cloud, templateName, true, null, false);
    }

    @Test
    public void testStartMachine() throws Exception {
        driver.startMachine(null, 60, TimeUnit.MINUTES);
    }

    @Test
    public void testStopManagementMachines() throws Exception {
        driver.stopManagementMachines();

    }

    private Cloud createCloud(String serviceName) {
        CloudUser user = new CloudUser();
        user.setUser("");
        user.setApiKey("");

        CloudProvider provider = new CloudProvider();
        provider.setNumberOfManagementMachines(1);
        provider.setManagementGroup("test-management-");

        Map<String, Object> cloudCustom = new HashMap<String, Object>();
        cloudCustom.put(VBOX_BOXES_PATH, "C:\\Users\\victor\\.vagrant.d\\boxes");
        cloudCustom.put(VBOX_HOSTONLYIF, "VirtualBox Host-Only Ethernet Adapter #2");
        cloudCustom.put(VBOX_HEADLESS, "true");
        cloudCustom.put(VBOX_URL, "http://25.0.0.1:18083");
        cloudCustom.put(VBOX_SHARED_FOLDER, "C:\\Users\\victor\\.vagrant.d\\boxes");
        cloudCustom.put(VBOX_PRIVATE_INTERFACE_NAME, "Local Area Connection");
        cloudCustom.put(VBOX_PUBLIC_INTERFACE_NAME, "Local Area Connection 2");
        cloudCustom.put(VBOX_STORAGE_CONTROLLER_NAME, "SATA");

        Map<String, Object> templateCustom = new HashMap<String, Object>();
        templateCustom.put("machineNamePrefix", serviceName);
        ComputeTemplate template = new ComputeTemplate();
        template.setNumberOfCores(1);
        template.setMachineMemoryMB(1024);
        template.setUsername("vagrant");
        template.setPassword("vagrant");
        template.setImageId("win2008r2-sqlserver");
        template.setMachineMemoryMB(1024);
        template.setHardwareId("hardwareId");
        template.setRemoteDirectory("/C\\$/Users/Administrator/gs-files");
        template.setFileTransfer(FileTransferModes.CIFS);
        template.setRemoteExecution(RemoteExecutionModes.WINRM);
        template.setScriptLanguage(ScriptLanguages.WINDOWS_BATCH);
        template.setLocalDirectory("upload");
        template.setCustom(templateCustom);

        Cloud cloud = new Cloud();
        CloudCompute cloudCompute = new CloudCompute();
        Map<String, ComputeTemplate> templates = new HashMap<String, ComputeTemplate>();
        templates.put(templateName, template);
        cloudCompute.setTemplates(templates);
        cloud.setCloudCompute(cloudCompute);
        cloud.setCustom(cloudCustom);
        cloud.setProvider(provider);
        cloud.setUser(user);
        return cloud;
    }

}
