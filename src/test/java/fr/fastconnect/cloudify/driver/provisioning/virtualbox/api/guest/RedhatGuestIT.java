package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.virtualbox_4_2.*;

import java.util.Arrays;
import java.util.Date;

public class RedhatGuestIT {

    private static final long END_TIME = System.currentTimeMillis() + 1000L * 60L;
    private RedhatGuest guest;
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
        guest = new RedhatGuest(virtualBoxManager);
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
    public void testUpdateNetworkingInterfaces() throws Exception {
        guest.updateNetworkingInterfaces(machine.getId(), remoteUsr, remotePwd, "26.0.0.15", END_TIME);
    }

}
