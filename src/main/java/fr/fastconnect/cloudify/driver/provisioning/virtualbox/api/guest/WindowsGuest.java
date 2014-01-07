package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.VirtualBoxManager;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.PrivateInterfaceConfig;

public class WindowsGuest extends BaseGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(WindowsGuest.class.getName());

    private static final String WINDOWS_SCRIPT_FOLDER = "C:\\Windows\\System32\\";
    private static final String GUEST_DESTINATION_SCRIPT_FOLDER = "C:\\scripts\\";

    private static final long SERVER_POLLING_INTERVAL_MILLIS = 1000L * 10L; // 10 secondes

    public WindowsGuest(VirtualBoxManager virtualBoxManager) {
        super(virtualBoxManager);
    }

    public void updateHostname(String machineGuid, String login, String password, String hostname, long endTime) throws Exception {
        logger.log(Level.INFO, String.format("Rename hostname to %s", hostname));
        String scriptContent = "C:/Windows/System32/netdom.exe renamecomputer %COMPUTERNAME% /newname:" + hostname + " /force >> "
                + GUEST_DESTINATION_SCRIPT_FOLDER + "updateHostname.log";
        this.executeScript(machineGuid, login, password, "updateHostname.bat", scriptContent, endTime);
    }

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privateAddrIP, long endTime) throws Exception {

        String script = IOUtils.toString(ClassLoader.getSystemResourceAsStream("scripts/ConfigureNetworkInterface.ps1"));
        this.createFile(machineGuid, login, password, GUEST_DESTINATION_SCRIPT_FOLDER + "ConfigureNetworkInterface.ps1", script, endTime);

        // Update NAT Network Interface
        logger.log(Level.INFO, "Updating NAT network interface");
        String powershellCmd = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe -InputFormat none -NoProfile -ExecutionPolicy RemoteSigned ";

        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        this.executeScript(machineGuid, login, password, "updateNetwork0.bat",
                String.format(
                        powershellCmd + "%sConfigureNetworkInterface.ps1 %s >> %supdateNetwork0.log",
                        GUEST_DESTINATION_SCRIPT_FOLDER,
                        this.getFormattedMACAddress(m, 0L),
                        GUEST_DESTINATION_SCRIPT_FOLDER), endTime);

        // Update Private Network Interface
        logger.log(Level.INFO, "Updating private network interface: new ip=" + privateAddrIP);
        this.configurePrivateNetwork(m, login, password, privateAddrIP, endTime);

        // Update Public Network Interface
        logger.log(Level.INFO, "Updating public network interface");
        this.executeScript(machineGuid, login, password, "updateNetwork2.bat",
                String.format(
                        powershellCmd + "%sConfigureNetworkInterface.ps1 %s >> %supdateNetwork2.log",
                        GUEST_DESTINATION_SCRIPT_FOLDER,
                        this.getFormattedMACAddress(m, 2L),
                        GUEST_DESTINATION_SCRIPT_FOLDER), endTime);

        this.executeCommand(machineGuid, login, password, WINDOWS_SCRIPT_FOLDER + "ipconfig.exe", Arrays.asList("/renew"), endTime);
        this.executeScript(machineGuid, login, password, "disableFirewall.bat", "netsh advfirewall set allprofiles state off >> disableFirewall.log", endTime);
    }

    private void configurePrivateNetwork(IMachine m, String login, String password, String privateAddrIP, long endTime) throws Exception {
        String powershellCmd = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe -InputFormat none -NoProfile -ExecutionPolicy RemoteSigned ";
        String scriptContent = String.format(powershellCmd + "%sConfigureNetworkInterface.ps1 %s false %s %s %s >> %supdateNetwork1.log",
                GUEST_DESTINATION_SCRIPT_FOLDER,
                this.getFormattedMACAddress(m, 1L),
                privateAddrIP,
                PrivateInterfaceConfig.PRIVATE_IF_MASK,
                PrivateInterfaceConfig.PRIVATE_IF_GATEWAY,
                GUEST_DESTINATION_SCRIPT_FOLDER);

        // Create and copy a file which will contains the script context on the Guest machine.
        String destinationScript = GUEST_DESTINATION_SCRIPT_FOLDER + "updateNetwork1.bat";

        this.createFile(m.getId(), login, password, destinationScript, scriptContent, endTime);

        // Execute script.
        logger.log(Level.INFO, "Trying to execute file '" + destinationScript + "' on VM '" + m.getId() + "'");
        this.executeCommand(m.getId(), login, password, destinationScript, endTime);

        // For some unknown reasons, the script execution is not working.
        // To hack this failure, we execute the script until having a correct private IP.
        while (this.getPrivateAddressIP(m.getId()) == null) {
            logger.info("Private IP is not yet configured. retrying...");
            this.executeCommand(m.getId(), login, password, destinationScript, endTime);
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String getPrivateAddressIP(String machineNameOrId) throws Exception {
        IMachine m = this.virtualBoxManager.getVBox().findMachine(machineNameOrId);
        String networkCount = m.getGuestPropertyValue("/VirtualBox/GuestInfo/Net/Count");
        int count = Integer.parseInt(networkCount);

        // On Windows machine, we have to iterate over the network interfaces
        // because the private network defined on slot #1 is not corresponding to
        // the property '/VirtualBox/GuestInfo/Net/1/V4/IP' just like Linux machines.
        for (int i = 0; i < count; i++) {
            String ip = m.getGuestPropertyValue(String.format("/VirtualBox/GuestInfo/Net/%s/V4/IP", i));
            if (ip.startsWith(PrivateInterfaceConfig.PRIVATE_BASE_IP)) {
                return ip;
            }
        }
        return null;
    }

    @Override
    public String getPublicAddressIP(String machineNameOrId) throws Exception {
        IMachine m = this.virtualBoxManager.getVBox().findMachine(machineNameOrId);
        String networkCount = m.getGuestPropertyValue("/VirtualBox/GuestInfo/Net/Count");
        int count = Integer.parseInt(networkCount);

        // On Windows machine, we have to iterate over the network interfaces
        // because the public network defined on slot #1 is not corresponding to
        // the property '/VirtualBox/GuestInfo/Net/2/V4/IP' just like Linux machines.
        for (int slot = 0; slot < count; slot++) {
            String ipAddress = m.getGuestPropertyValue(String.format("/VirtualBox/GuestInfo/Net/%s/V4/IP", slot));
            if (ipAddress != null) {
                InetAddress inetadrr = InetAddress.getByName(ipAddress);
                if (inetadrr.isReachable(5000)) {
                    return ipAddress;
                }
            }
        }
        return null;
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws Exception {
        this.createFile(machineGuid, login, password, GUEST_DESTINATION_SCRIPT_FOLDER + "hosts", hosts, endTime);

        // /!\ The windows 'type' command is tricky. /!\
        // If you want to do a 'type' on a file in a different folder, you have to use '\' in your path i.e : 'C:\my_folder\my_file.txt'
        // ('type C:/my_folder/my_file.txt' won't work).
        String script = String.format("type \"%shosts\" >> C:/Windows/System32/drivers/etc/hosts", GUEST_DESTINATION_SCRIPT_FOLDER.replaceAll("/", "\\\\"));
        this.executeScript(machineGuid, login, password, "updateHosts.bat", script, endTime);
    }

    public void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception {
        // Create and copy a file which will contains the script context on the Guest machine.
        String destinationScript = GUEST_DESTINATION_SCRIPT_FOLDER + filename;

        this.createFile(machineGuid, login, password, destinationScript, content, endTime);
        // Execute script.
        logger.log(Level.INFO, "Trying to execute file '" + destinationScript + "' on VM '" + machineGuid + "'");
        this.executeCommand(machineGuid, login, password, destinationScript, endTime);
    }

    public void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception {
        logger.log(Level.INFO, "Trying to create file '" + destination + "' on machine '" + machineGuid + "'");

        // Delete file if exists
        this.executeCommand(machineGuid, login, password, "C:\\windows\\system32\\cmd.exe",
                Arrays.asList(new String[] { "/c", "del", "/F", "/Q", destination }), endTime);

        // Create parent folder
        this.executeCommand(machineGuid, login, password, "C:\\windows\\system32\\cmd.exe",
                Arrays.asList(new String[] { "/c", "md", new File(destination).getParent() }),
                endTime);

        // We are having issue with some escaping (espacially '"'), so we do a little trick by encoding the content to base64.
        List<String> builder = this.createSplittedBase64Content(content);

        // Send content in base64.
        for (String line : builder) {
            this.executePowershellCommand(machineGuid, login, password, String.format("Add-Content -Value '%s' -Path %s", line, destination), endTime);
        }

        // Decode remote base64 file.
        this.executePowershellCommand(machineGuid, login, password,
                String.format("[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((Get-Content %s))) | Set-Content %s",
                        destination, destination), endTime);
    }

    void executePowershellCommand(String machineGuid, String login, String password, String powershellCommand, long endTime) throws Exception {
        this.executeCommand(machineGuid, login, password, "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                Arrays.asList(
                        "-InputFormat",
                        "none",
                        "-NoProfile",
                        "-ExecutionPolicy", "RemoteSigned",
                        "-Command", "& {" + powershellCommand + "}"),
                endTime);
    }

    void executePowershellScript(String machineGuid, String login, String password, List<String> additionalArgs, long endTime) throws Exception {
        List<String> args = new ArrayList<String>(Arrays.asList(
                "-InputFormat",
                "none",
                "-NoProfile",
                "-ExecutionPolicy", "RemoteSigned",
                "-File"));
        args.addAll(additionalArgs);
        this.executeCommand(machineGuid, login, password, "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                args,
                endTime);
    }

    public void ping(String machineGuid, String login, String password, long endTime) throws Exception {
        // do a PING command just to test if it's up
        this.executeCommand(machineGuid, login, password, WINDOWS_SCRIPT_FOLDER + "PING.EXE", endTime);
    }

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception {
        // Nothing to do
        return;
    }

    @Override
    public void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception {
        // Set timezone to GMT+1
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/tzutil.exe", Arrays.asList(new String[] { "/s", "Romance Standard Time" }),
                endTime);

        // Reboot the VM for updates to be considerate (especially hostname, network ip)...
        this.reboot(machineGuid, login, password, endTime);

        // Start the WinRM service on the guest.
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/sc.exe", Arrays.asList(new String[] { "start", "WinRM" }), endTime);
    }

    void reboot(String machineGuid, String login, String password, long endTime) throws Exception {
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/shutdown.exe", Arrays.asList(new String[] { "/r", "/t", "0", "/f" }), endTime);

        // Wait the reboot command to be applied
        Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);

        // Wait for the guest VM to be ready
        boolean isReady = false;
        while (!isReady && System.currentTimeMillis() < endTime) {
            try {
                this.ping(machineGuid, login, password, endTime);
                isReady = true;
            } catch (Exception ex) {
                logger.log(Level.FINE, "OS not ready yet", ex);
                if (System.currentTimeMillis() > endTime) {
                    throw new TimeoutException("timeout creating server.");
                }
                Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
            }
        }

        Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
    }

}
