package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IGuest;
import org.virtualbox_4_2.IGuestFile;
import org.virtualbox_4_2.IGuestSession;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.VirtualBoxManager;

public class WindowsGuestIT {

    private static final long END_TIME = System.currentTimeMillis() + 1000L * 60L * 60L;
    private WindowsGuest guest;
    private VirtualBoxManager virtualBoxManager;

    private IMachine machine;

    private String vboxWSURL = "http://26.0.0.1:18083";
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
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:\\windows\\system32\\cmd.exe", Arrays.asList("/c", "rmdir", "/s", "/q", "c:\\scripts"), END_TIME);
        guest.updateHostname(machine.getId(), remoteUsr, remotePwd, "windows-test455", END_TIME);
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
        guest.updateNetworkingInterfaces(machine.getId(), remoteUsr, remotePwd, "25.0.0.9", END_TIME);
    }

    @Test
    public void testExecuteCommand() throws Exception {
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:/Windows/System32/notepad.exe",
                END_TIME);
    }

    @Test
    public void testExecuteCommandSC() throws Exception {
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:/Windows/System32/sc.exe",
                Arrays.asList(new String[] { "start", "WinRM" }),
                END_TIME);
    }

    @Test
    public void testExecuteCommandCreateEmptyFile() throws Exception {
        guest.executeCommand(machine.getId(), remoteUsr, remotePwd,
                "C:\\windows\\system32\\cmd.exe",
                Arrays.asList(new String[] { "/c", "copy", "/y", "NUL", "c:\\empty.txt" }),
                END_TIME);
    }

    @Test
    public void testReboot() throws Exception {
        guest.reboot(machine.getId(), remoteUsr, remotePwd, END_TIME);
    }

    @Test
    public void testExecuteScript() throws Exception {
        String filename = "myhello.bat";
        String content = "echo My Hello World >> %1";
        guest.executeScript(machine.getId(), remoteUsr, remotePwd, filename, content, END_TIME);
    }

    @Test
    public void testCreateFile() throws Exception {
        String content = "echo hello @world@ > C:/tmp/hello.log";
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

    @Test
    @Ignore("fileOpen.write is not implemented")
    public void testVBoxFileOpen() throws Exception {
        ISession session = virtualBoxManager.openMachineSession(machine);
        IConsole console = session.getConsole();
        IGuest guest = console.getGuest();

        IGuestSession guestSession = guest.createSession(remoteUsr, remotePwd, "", "");

        IGuestFile fileOpen = null;
        try {
            // guestSession.fileCreateTemp("tempXXX", 700l, "c:\\tmp", false);
            long creationMode = 0x00000100;
            System.out.println(creationMode);
            fileOpen = guestSession.fileOpen("c:\\tmp\\toto.txt", "0", "0", creationMode, 0l);

            String fileName = fileOpen.getFileName();
            System.out.println(fileName);

            fileOpen.writeAt(0L, new String("hello world").getBytes(), Long.MAX_VALUE);
            fileOpen.close();
        } finally {
            if (fileOpen != null) {
                fileOpen.close();
            }
            guestSession.close();
        }
    }

    @Test
    public void testExecutePowerShellCommand() throws Exception {
        guest.executePowershellCommand(
                machine.getId(),
                remoteUsr,
                remotePwd,
                "Set-Content -Value 'output @test@' -Path C:\\tmp\\toto.txt",
                END_TIME);
    }

    @Test
    public void testBase64() throws Exception {
        String content = "output \"testéà&ç\" -Path C:\\tmp\\toto.txt";
        byte[] bytes = Base64.encodeBase64((content + "\n").getBytes());
        String base64 = new String(bytes);
        guest.executePowershellCommand(machine.getId(), remoteUsr, remotePwd,
                String.format("Set-Content -Value '%s' -Path C:\\tmp\\toto.txt", base64), END_TIME);
        guest.executePowershellCommand(
                machine.getId(),
                remoteUsr,
                remotePwd,
                String.format(
                        "[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((Get-Content c:\\tmp\\toto.txt))) | Set-Content c:\\tmp\\toto.txt",
                        base64), END_TIME);
    }

    @Test
    public void test() throws Exception {
        // List<IHostNetworkInterface> networkInterfaces = virtualBoxManager.getVBox().getHost().getNetworkInterfaces();
        // for (IHostNetworkInterface ni : networkInterfaces) {
        // System.out.println("name=" + ni.getName());
        // System.out.println("networkName=" + ni.getNetworkName());
        // System.out.println("ip=" + ni.getIPAddress());
        // System.out.println();
        // }

        IMachine m = virtualBoxManager.getVBox().findMachine(machine.getId());
        String networkCount = m.getGuestPropertyValue("/VirtualBox/GuestInfo/Net/Count");
        int count = Integer.parseInt(networkCount);
        System.out.println(m.getNetworkAdapter(1l).getBridgedInterface());
        System.out.println(m.getNetworkAdapter(1l).getHostOnlyInterface());
        System.out.println(m.getNetworkAdapter(2l).getBridgedInterface());
        System.out.println(m.getNetworkAdapter(2l).getHostOnlyInterface());
        for (int i = 0; i < count; i++) {
            String guestPropertyValue = m.getGuestPropertyValue(String.format("/VirtualBox/GuestInfo/Net/%s/V4/IP", i));
            System.out.println(guestPropertyValue);
            // if (guestPropertyValue != null) {
            // InetAddress inetadrr = InetAddress.getByName(guestPropertyValue);
            // if (inetadrr.isReachable(5000)) {
            // System.out.println("[" + i + "] public addr=" + guestPropertyValue);
            // } else {
            // System.out.println("[" + i + "] addr=" + guestPropertyValue);
            // }
            // }
        }
    }

    @Test
    public void testGuest() throws Exception {
        UbuntuGuest guest = new UbuntuGuest(virtualBoxManager);
        guest.ping(machine.getId(), "vagrant", "vagrant", 10000);
    }
}
