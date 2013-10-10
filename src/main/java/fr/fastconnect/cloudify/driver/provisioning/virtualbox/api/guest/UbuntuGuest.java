package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import org.virtualbox_4_2.VirtualBoxManager;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.PrivateInterfaceConfig;

public class UbuntuGuest extends LinuxGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(UbuntuGuest.class.getName());

    public UbuntuGuest(VirtualBoxManager virtualBoxManager) {
        super(virtualBoxManager);
    }

    public void updateHostname(String machineGuid, String login, String password, String hostname, long endTime) throws Exception {

        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n" +
                "sudo sed -i s/.*$/" + hostname + "/ /etc/hostname\n" +
                "sudo service hostname start";

        executeScript(machineGuid, login, password, "updatehostname.sh", updatehostnameContent, endTime);
    }

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privateAddrIP, long endTime) throws Exception {

        // create the new /etc/network/interfaces file, and copy to guest
        String interfacesContent = "auto lo\n" +
                "iface lo inet loopback\n\n" +
                "auto eth0\n" +
                "iface eth0 inet dhcp\n" +
                "auto eth1\n" +
                "iface eth1 inet static\n" +
                "address " + privateAddrIP + "\n" +
                "netmask " + PrivateInterfaceConfig.PRIVATE_IF_MASK + "\n" +
                "gateway " + PrivateInterfaceConfig.PRIVATE_IF_GATEWAY + "\n" +
                "\n" +
                "auto eth2\n" +
                "iface eth2 inet dhcp\n\n";

        createFile(machineGuid, login, password, "/tmp/interfaces", interfacesContent, endTime);

        // create the script to update the interfaces file, and copy it to the guest
        String updateinterfacesContent = "#!/bin/bash\n" +
                "cat /tmp/interfaces | sudo tee /etc/network/interfaces\n" +
                "sudo rm -fr /etc/udev/rules.d/70-persistent-net.rules\n" +
                "sudo udevadm trigger\n" +
                "sudo /etc/init.d/networking restart\n" +
                "sudo ifdown eth0\n" +
                "sudo ifdown eth1\n" +
                "sudo ifdown eth2\n" +
                "sudo wait 5\n" +
                "sudo ifup eth0\n" +
                "sudo ifup eth1\n" +
                "sudo ifup eth2\n" +
                "sudo wait 5\n";

        executeScript(machineGuid, login, password, "updateinterfaces.sh", updateinterfacesContent, endTime);

        this.waitUntilPrivateIPIsConfigured(machineGuid, privateAddrIP, endTime);
        this.waitPublicAddressToBeReachable(machineGuid, endTime);
    }

}
