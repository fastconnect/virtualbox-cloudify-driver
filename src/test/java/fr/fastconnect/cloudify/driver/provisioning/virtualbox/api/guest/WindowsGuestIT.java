package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.VirtualBoxManager;

public class WindowsGuestIT {

    private static final long END_TIME = System.currentTimeMillis() + Short.MAX_VALUE;
    private WindowsGuest guest;
    private VirtualBoxManager virtualBoxManager;

    private IMachine machine;

    private String vboxWSURL = "http://25.0.0.1:18083";
    private String vboxWSUsr = "";
    private String vboxWSPwd = "";
    private String machineName = "app-management-1";
    private String remoteUsr = "vagrant";
    private String remotePwd = "vagrant";

    @Before
    public void init() {
        virtualBoxManager = VirtualBoxManager.createInstance(null);
        virtualBoxManager.connect(vboxWSURL, vboxWSUsr, vboxWSPwd);
        guest = new WindowsGuest(virtualBoxManager);
        machine = getMachineByName(machineName);
    }

    private IMachine getMachineByName(String machineName) {
        for (IMachine machine : virtualBoxManager.getVBox().getMachines()) {
            if (machineName.equals(machine.getName())) {
                return machine;
            }
        }
        throw new IllegalArgumentException("Machine '" + machineName + "'not found");
    }

    @Test
    public void testPing() throws Exception {
        guest.ping(machine.getId(), remoteUsr, remotePwd, END_TIME);
    }

    @Test
    public void testUpdateHostname() throws Exception {
        guest.updateHostname(machine.getId(), remoteUsr, remotePwd, "windows-test000", END_TIME);
    }

    @Test
    public void testUpdateHostnameAndExecuteScript() throws Exception {
        guest.updateHostname(machine.getId(), remoteUsr, remotePwd, "windows-test111", END_TIME);
        String filename = "afterUpdateHostname.bat";
        String content = "echo myhello >> %1";
        guest.executeScript(machine.getId(), remoteUsr, remotePwd, filename, content, END_TIME);
    }

    @Test
    public void testUpdateNetworkingInterfaces() throws Exception {
        String network1 = "Local Area Connection";
        String network2 = "Local Area Connection 2";
        guest.updateNetworkingInterfaces(machine.getId(), remoteUsr, remotePwd, network1, network2, "25.0.0.2", "255.255.255.0", "25.0.0.1", END_TIME);
    }

    @Test
    public void testExecuteCommand() throws Exception {
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:/Windows/System32/notepad.exe",
                Arrays.asList(new String[0]),
                Arrays.asList(new String[0]),
                END_TIME);
    }

    @Test
    public void testReboot() throws Exception {
        guest.reboot(machine.getId(), remoteUsr, remotePwd, END_TIME);
    }

    @Test
    public void testExecuteCommandSC() throws Exception {
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:/Windows/System32/sc.exe",
                Arrays.asList(new String[] { "start", "WinRM" }),
                Arrays.asList(new String[0]),
                END_TIME);
    }

    @Test
    public void testExecuteScript() throws Exception {
        String filename = "myhello.bat";
        String content = "echo My Hello World >> %1";
        guest.executeScript(machine.getId(), remoteUsr, remotePwd, filename, content, END_TIME);
    }

    @Test
    public void testCreateFile() throws Exception {
        String content = "echo hello world > C:/tmp/hello.log";
        guest.createFile(machine.getId(), remoteUsr, remotePwd, "C:/tmp/hello.bat", content, END_TIME);
    }

    @Test
    public void testUpdateHosts() throws Exception {
        // Generate a hosts file with fixed IP
        String managementGroup = "app-management-";
        String machineNamePrefix = "app-agent-";
        String baseIP = "25.0.0";
        String hostsFile = "\r\n127.0.0.1  localhost\r\n";
        for (int cpt = 2; cpt < 10; cpt++) {
            hostsFile += baseIP + "." + cpt + "\t" + managementGroup + (cpt - 1) + "\r\n";
        }

        for (int cpt = 10; cpt < 30; cpt++) {
            hostsFile += baseIP + "." + cpt + "\t" + machineNamePrefix + (cpt - 9) + "\r\n";
        }
        guest.updateHosts(machine.getId(), remoteUsr, remotePwd, hostsFile, END_TIME);
    }
}
