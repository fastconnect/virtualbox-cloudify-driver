package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.cloudifysource.domain.cloud.Cloud;
import org.cloudifysource.domain.cloud.compute.ComputeTemplate;
import org.cloudifysource.dsl.internal.DSLException;
import org.cloudifysource.dsl.internal.ServiceReader;
import org.cloudifysource.esc.driver.provisioning.CloudProvisioningException;
import org.cloudifysource.esc.driver.provisioning.ComputeDriverConfiguration;
import org.cloudifysource.esc.driver.provisioning.MachineDetails;
import org.cloudifysource.esc.driver.provisioning.ProvisioningContextAccess;
import org.cloudifysource.esc.driver.provisioning.ProvisioningContextImpl;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * <p>
 * Start machines, test configurations, and shutdown.
 * </p>
 * <p>
 * Properties must be configured :
 * <ul>
 * <li>For ubuntu ./src/test/resources/ubuntu/vbox-cloud.properties</li>
 * <li>For windows ./src/test/resources/windows/vbox-cloud.properties</li>
 * </ul>
 * When starting a VM, it will create a temporary vbox cloud folder and copy groovy and properties file. Configurations are read from that temporary folder. The
 * file './src/main/resources/vbox/vbox-cloud.properties.sample' is copy and rename into 'vbox-cloud.properties'. Then the above properties file will override
 * this new 'vbox-cloud.properties'.
 * </p>
 * 
 * @author victor
 * 
 */
public class VirtualboxCloudifyDriverIT {

    private static final Logger logger = Logger.getLogger(VirtualboxCloudifyDriverIT.class.getName());

    private static final int TIMEOUT = 1000 * 60 * 15; // 15 minutes

    private Cloud cloud;

    @BeforeClass
    public static void beforeClass() throws CloudProvisioningException, DSLException, TimeoutException {
        Logger logger = Logger.getLogger(VirtualboxCloudifyDriver.class.getName());
        logger.setLevel(Level.FINEST);
        Handler[] handlers = logger.getParent().getHandlers();
        for (Handler handler : handlers) {
            handler.setLevel(Level.ALL);
        }
    }

    private VirtualboxCloudifyDriver createDriver(String computeTemplate, String overridesDir, boolean useBridgeInterface)
            throws IOException, DSLException, CloudProvisioningException {

        // Create a temporary directory
        File tmpCloudDir = File.createTempFile("vbox-test", "");
        tmpCloudDir.delete();
        tmpCloudDir.mkdir();
        tmpCloudDir.deleteOnExit();
        new File(tmpCloudDir, "upload").mkdir();
        logger.info("Initialize driver using cloud folder at " + tmpCloudDir.getAbsolutePath());

        // Copy vbox cloud file into the temporary directory
        FileUtils.copyDirectory(new File("./src/main/resources/vbox"), tmpCloudDir);

        // Configure vbox-cloud.properties file
        File propertiesFile = new File(tmpCloudDir, "vbox-cloud.properties.sample");
        String vboxPropertiesFile = FileUtils.readFileToString(propertiesFile);

        // Create vbox-cloud.properties from vbox-cloud.properties.sample and override it with the content of the test properties file
        Properties overrides = new Properties();
        overrides.load(new FileInputStream(new File(overridesDir, "vbox-cloud.properties")));
        for (Object key : overrides.keySet()) {
            String value = (String) overrides.get(key.toString());
            if (value.contains("\\")) {
                value = value.replaceAll("\\\\", "/");
            }
            vboxPropertiesFile = vboxPropertiesFile.replaceAll(String.format("%s=.*", key.toString()),
                    String.format("%s=%s", key.toString(), value));
        }
        File tmpPropertiesfile = new File(tmpCloudDir, "vbox-cloud.properties");
        FileUtils.writeStringToFile(tmpPropertiesfile, vboxPropertiesFile);
        logger.info("Using properties :\n" + FileUtils.readFileToString(tmpPropertiesfile));

        // Handle bridge or hosted interface
        if (!useBridgeInterface) {
            File vboxCloudFile = new File(tmpCloudDir, "vbox-cloud.groovy");
            String vboxCloud = FileUtils.readFileToString(vboxCloudFile);
            vboxCloud = vboxCloud.replaceAll("\"vbox.bridgedInterface\" : BRIDGED_INTERFACE.*", "\"vbox.hostOnlyInterface\" : HOST_ONLY_INTERFACE,");
            FileUtils.writeStringToFile(vboxCloudFile, vboxCloud);
        }

        // Use Cloudify API to read the cloud configuration file
        cloud = ServiceReader.readCloudFromDirectory(tmpCloudDir.getAbsolutePath());

        // Create the Driver
        VirtualboxCloudifyDriver driver = new VirtualboxCloudifyDriver();
        ProvisioningContextImpl ctx = new ProvisioningContextImpl();
        ProvisioningContextAccess.setCurrentProvisioingContext(ctx);
        ctx.getInstallationDetailsBuilder().setCloud(cloud);
        ComputeTemplate template = cloud.getCloudCompute().getTemplates().get(computeTemplate);
        ctx.getInstallationDetailsBuilder().setTemplate(template);

        ComputeDriverConfiguration configuration = new ComputeDriverConfiguration();
        configuration.setCloud(cloud);
        configuration.setCloudTemplate(computeTemplate);
        configuration.setManagement(true);

        driver.setConfig(configuration);
        logger.info(cloud.toString());
        return driver;
    }

    private void doTestStarManagementMachine(String computeTemplate, String overridesDir, boolean useBridgeInterface) throws Exception {
        VirtualboxCloudifyDriver driver = this.createDriver(computeTemplate, overridesDir, useBridgeInterface);
        try {
            MachineDetails[] mds = driver.startManagementMachines(null, TIMEOUT, TimeUnit.MILLISECONDS);

            Assert.assertNotNull("MachineDetails is null", mds);
            Assert.assertFalse("MachineDetails is empty", mds.length == 0);
            MachineDetails md = mds[0];
            String publicAddress = md.getPublicAddress();
            logger.info("public ip=" + publicAddress);
            Assert.assertNotNull("machineId is null", md.getMachineId());
            Assert.assertNotNull("public address is null", publicAddress);

            if (useBridgeInterface) {
                this.assertBridgedIfWithPublicAddrIsConfigured(publicAddress);
            } else {
                // TODO assertion for host only
            }

            Assert.assertNotNull("private address is null", md.getPrivateAddress());
            Assert.assertTrue("private address should start with " + PrivateInterfaceConfig.PRIVATE_BASE_IP + ".X, got :" + md.getPrivateAddress(),
                    md.getPrivateAddress().startsWith(PrivateInterfaceConfig.PRIVATE_BASE_IP));
        } finally {
            if (driver != null) {
                try {
                    // driver.stopManagementMachines();
                } catch (Exception e) {
                    // FIXME Sometimes throw exception because the session is locked.
                    logger.log(Level.WARNING, "Fail to stop machine", e);
                }
            }
        }
    }

    private void assertBridgedIfWithPublicAddrIsConfigured(String publicAddress) throws SocketException {
        String bridgedIfName = (String) cloud.getCustom().get(VirtualboxCloudifyDriver.VBOX_BRIDGEDIF);
        NetworkInterface bridgedIf = this.getNetworkInterfaceByDisplayName(bridgedIfName);
        Enumeration<InetAddress> inetAddresses = bridgedIf.getInetAddresses();
        if (inetAddresses.hasMoreElements()) {
            String hostBridgedIfIp = inetAddresses.nextElement().getHostAddress();
            String hostBridgedIfIpBase = hostBridgedIfIp.substring(0, hostBridgedIfIp.lastIndexOf("."));
            String publicAddressBase = publicAddress.substring(0, hostBridgedIfIp.lastIndexOf("."));
            Assert.assertEquals("Using bridged mode. The guest public interface should have an ip like " + hostBridgedIfIpBase + ".x",
                    hostBridgedIfIpBase, publicAddressBase);
        } else {
            Assert.fail("Couldn't retrieve an IP for the host bridged interface. Verify that network interface '" + bridgedIfName + "' is activated");
        }
    }

    private NetworkInterface getNetworkInterfaceByDisplayName(String displayName) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            if (networkInterface.getDisplayName().equals(displayName)) {
                return networkInterface;
            }
        }
        return null;
    }

    @Test(timeout = TIMEOUT)
    public void testStartMachineUbuntuBridgeInterface() throws Exception {
        this.doTestStarManagementMachine("SMALL_LINUX", "./src/test/resources/ubuntu", true);
    }

    @Test(timeout = TIMEOUT)
    public void testStartMachineUbuntuHostOnlyInterface() throws Exception {
        this.doTestStarManagementMachine("SMALL_LINUX", "./src/test/resources/ubuntu", false);
    }

    @Test(timeout = TIMEOUT)
    public void testStartMachineWindowsBridgeInterface() throws Exception {
        this.doTestStarManagementMachine("SMALL_WIN", "./src/test/resources/windows", true);
    }

    @Test(timeout = TIMEOUT)
    public void testStartMachineWindowsHostOnlyInterface() throws Exception {
        this.doTestStarManagementMachine("SMALL_WIN", "./src/test/resources/windows", false);
    }

    @Test
    public void shutdownManagementMachine() {
        try {
            VirtualboxCloudifyDriver driver = this.createDriver("SMALL_LINUX", "./src/test/resources/ubuntu", false);
            driver.stopManagementMachines();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Should not throw exception. Got : " + e.getMessage());
        }
    }

}
