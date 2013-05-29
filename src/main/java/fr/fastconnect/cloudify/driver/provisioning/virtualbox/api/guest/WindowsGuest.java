package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.virtualbox_4_2.CopyFileFlag;
import org.virtualbox_4_2.DirectoryCreateFlag;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IGuest;
import org.virtualbox_4_2.IGuestSession;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.VirtualBoxManager;

public class WindowsGuest extends BaseGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(WindowsGuest.class.getName());

    private static final String WINDOWS_SCRIPT_FOLDER = "C:/Windows/System32/";
    private static final String GUEST_DESTINATION_SCRIPT_FOLDER = "C:/scripts/";

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

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privIfName, String pubIfName, String ip, String mask,
            String gatewayIp, long endTime)
            throws Exception {

        // Update 1st Network Interface
        logger.log(Level.INFO, String.format("Updating network interface (%s)", privIfName));
        this.executeScript(machineGuid, login, password, "updateNetwork1.bat",
                String.format("netsh interface ip set address \"%s\" dhcp >> %supdateNetwork1.log", privIfName, GUEST_DESTINATION_SCRIPT_FOLDER), endTime);

        // Update 2nd Network Interface
        logger.log(Level.INFO, String.format("Updating network interface (%s): new ip=%s", pubIfName, ip));
        this.executeScript(machineGuid, login, password, "updateNetwork2.bat",
                String.format("netsh interface ip set address \"%s\" static %s %s %s >> %supdateNetwork2.log", pubIfName, ip, mask, gatewayIp,
                        GUEST_DESTINATION_SCRIPT_FOLDER), endTime);

        this.executeCommand(machineGuid, login, password, WINDOWS_SCRIPT_FOLDER + "ipconfig.exe", Arrays.asList("/renew"), Arrays.asList(new String[0]),
                endTime);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws Exception {
        this.createFile(machineGuid, login, password, GUEST_DESTINATION_SCRIPT_FOLDER + "hosts", hosts, endTime);

        // /!\ The windows 'type' command is tricky. /!\
        // If you want to do a 'type' on a file in a different folder, you have to use '\' in your path i.e : 'C:\my_folder\my_file.txt'
        // ('C:/my_folder/my_file.txt' is not valid).
        String script = String.format("type \"%shosts\" >> C:/Windows/System32/drivers/etc/hosts", GUEST_DESTINATION_SCRIPT_FOLDER.replaceAll("/", "\\\\"));
        this.executeScript(machineGuid, login, password, "updateHosts.bat", script, endTime);
    }

    public void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception {
        // Create and copy a file which will contains the script context on the Guest machine.
        String destination = GUEST_DESTINATION_SCRIPT_FOLDER + filename;
        this.createFile(machineGuid, login, password, destination, content, endTime);

        // Execute script.
        logger.log(Level.INFO, "Trying to execute file '" + destination + "' on VM '" + machineGuid + "'");
        this.executeCommand(machineGuid, login, password, destination, Arrays.asList(new String[0]), Arrays.asList(new String[0]), endTime);
    }

    public void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception {
        File sourceFile = File.createTempFile("vboxScript", ".bat");
        FileOutputStream fos = new FileOutputStream(sourceFile);
        fos.write(content.getBytes());
        fos.close();

        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        ISession session = virtualBoxManager.openMachineSession(m);
        IGuestSession guestSession = null;
        try {
            m = session.getMachine();
            IConsole console = session.getConsole();
            IGuest guest = console.getGuest();
            guestSession = guest.createSession(login, password, "", "");

            if (!this.directoryExists(guestSession, new File(destination).getParent())) {
                guestSession.directoryCreate(new File(destination).getParent(), 600L, Arrays.asList(DirectoryCreateFlag.None));
            }
            IProgress process = guestSession.copyTo(sourceFile.getPath(), destination, Arrays.asList(CopyFileFlag.None));
            process.waitForCompletion(-1);
        } finally {
            if (guestSession != null) {
                guestSession.close();
            }
            this.virtualBoxManager.closeMachineSession(session);

        }
    }

    /**
     * /!\ Hack /!\
     * This method encapsulate IGuestSession.directoryExists because it throws an Exception when the directory doesn't exists on the guest.
     * 
     * @return true if the directory exists, false if not.
     */
    private boolean directoryExists(IGuestSession guestSession, String path) {
        try {
            return guestSession.directoryExists(path);
        } catch (Exception e) {
            return false;
        }
    }

    public void ping(String machineGuid, String login, String password, long endTime) throws Exception {
        // do a PING command just to test if it's up
        this.executeCommand(machineGuid, login, password, WINDOWS_SCRIPT_FOLDER + "PING.EXE", Arrays.asList(new String[0]),
                Arrays.asList(new String[0]), endTime);
    }

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception {
        // Nothing to do
        return;
    }

    @Override
    public void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception {
        // Set timezone to GMT+1
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/tzutil.exe", Arrays.asList(new String[] { "/s", "Romance Standard Time" }),
                Arrays.asList(new String[0]), endTime);

        // Reboot the VM for updates to be considerate (especially hostname, network ip)...
        this.reboot(machineGuid, login, password, endTime);

        // Start the WinRM service on the guest.
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/sc.exe", Arrays.asList(new String[] { "start", "WinRM" }),
                Arrays.asList(new String[0]), endTime);

        // TODO Start Samba service
    }

    void reboot(String machineGuid, String login, String password, long endTime) throws Exception {
        this.executeCommand(machineGuid, login, password, "C:/Windows/System32/shutdown.exe", Arrays.asList(new String[] { "/r", "/t", "0", "/f" }),
                Arrays.asList(new String[0]), endTime);

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
