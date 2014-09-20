package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.VirtualBoxManager;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.PrivateInterfaceConfig;

public class RedhatGuest extends LinuxGuest {

    public RedhatGuest(VirtualBoxManager virtualBoxManager) {
        super(virtualBoxManager);
    }

    public void updateHostname(String machineGuid, String login, String password, String hostname, long endTime) throws Exception {

        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n" +
                "sudo sed -i 's/\\(HOSTNAME=\\).*/\\1" + hostname + "/' /etc/sysconfig/network\n" +
                "sudo hostname " + hostname;

        executeScript(machineGuid, login, password, "updatehostname.sh", updatehostnameContent, endTime);
    }

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privateAddrIP, long endTime) throws Exception {

        IMachine machine = virtualBoxManager.getVBox().findMachine(machineGuid);
        String eth0Mac = this.getFormattedMACAddress(machine, 0l);
        String eth1Mac = this.getFormattedMACAddress(machine, 1l);
        String eth2Mac = this.getFormattedMACAddress(machine, 2l);

        String eth0Content = "DEVICE=eth0\n" +
                "HWADDR=" + eth0Mac + "\n" +
                "ONBOOT=yes\n" +
                "BOOTPROTO=dhcp\n" +
                "USERCTL=no";

        // create the new /etc/sysconfig/network-scripts file, and copy to guest
        String eth1Content = "DEVICE=eth1\n" +
                "HWADDR=" + eth1Mac + "\n" +
                "IPADDR=" + privateAddrIP + "\n" +
                // "GATEWAY="+gatewayIp+"\n"+
                "NETMASK=" + PrivateInterfaceConfig.PRIVATE_IF_MASK + "\n" +
                "ONBOOT=yes\n" +
                "BOOTPROTO=none\n" +
                "USERCTL=no";
        // NM_CONTROLLED="no"

        String eth2Content = "DEVICE=eth2\n" +
                "HWADDR=" + eth2Mac + "\n" +
                "ONBOOT=yes\n" +
                "BOOTPROTO=dhcp\n" +
                "USERCTL=no";

        createFile(machineGuid, login, password, "/tmp/ifcfg-eth0", eth0Content, endTime);
        createFile(machineGuid, login, password, "/tmp/ifcfg-eth1", eth1Content, endTime);
        createFile(machineGuid, login, password, "/tmp/ifcfg-eth2", eth2Content, endTime);

        // String updateHostnameGatewayContent = "#!/bin/bash\n" +
        // "echo GATEWAY=" + gatewayIp + " | sudo tee /etc/sysconfig/network\n" +
        // "echo GATEWAY_IF=eth1 | sudo tee /etc/sysconfig/network\n";
        // executeScript(machineGuid, login, password, "updatehostnamegateway.sh", updateHostnameGatewayContent);

        String updateinterfacesContent = "#!/bin/bash\n" +
                "cat /tmp/ifcfg-eth0 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth0\n" +
                "sudo cp /etc/sysconfig/network-scripts/ifcfg-eth0 /etc/sysconfig/network-scripts/ifcfg-eth1\n" +
                "sudo cp /etc/sysconfig/network-scripts/ifcfg-eth0 /etc/sysconfig/network-scripts/ifcfg-eth2\n" +
                "sudo chmod a+r /etc/sysconfig/network-scripts/ifcfg-eth*\n" +
                "cat /tmp/ifcfg-eth1 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth1\n" +
                "cat /tmp/ifcfg-eth2 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth2\n";
        executeScript(machineGuid, login, password, "updateinterfaces.sh", updateinterfacesContent, endTime);

        // create the script to update the interfaces file, and copy it to the guest
        String refreshinterfacesContent = "#!/bin/bash\n" +
                "sudo rm /etc/udev/rules.d/70-persistent-net.rules\n" +
                "sudo udevadm trigger\n";
        executeScript(machineGuid, login, password, "refreshinterfaces.sh", refreshinterfacesContent, endTime);

        String test = "#!/bin/bash\n" +
                // "sudo service network restart\n";
                "sudo ifdown eth0\n" +
                "sudo ifdown eth1\n" +
                "sudo ifdown eth2\n" +
                "sleep 5\n" +
                "sudo ifup eth0\n" +
                "sudo ifup eth1 " + privateAddrIP + "\n" +
                "sudo ifup eth2\n";
        executeScript(machineGuid, login, password, "test.sh", test, endTime);

        String stopfirewallContent = "#!/bin/bash\n" +
                "sudo /etc/init.d/iptables stop\n" +
                "sudo chkconfig iptables off\n";
        executeScript(machineGuid, login, password, "stopfirewall.sh", stopfirewallContent, endTime);

        this.waitUntilPrivateIPIsConfigured(machineGuid, privateAddrIP, endTime);
        this.waitPublicAddressToBeReachable(machineGuid, endTime);
    }
}
