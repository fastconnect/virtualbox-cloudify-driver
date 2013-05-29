package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.VirtualBoxManager;

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

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String privIfName, String pubIfName, String ip, String mask,
            String gatewayIp, long endTime)
            throws Exception {

        IMachine machine = virtualBoxManager.getVBox().findMachine(machineGuid);
        String eth0Mac = machine.getNetworkAdapter(0l).getMACAddress();
        eth0Mac = eth0Mac.substring(0, 2) + ":" + eth0Mac.substring(2, 4) + ":" + eth0Mac.substring(4, 6) + ":" + eth0Mac.substring(6, 8) + ":"
                + eth0Mac.substring(8, 10) + ":" + eth0Mac.substring(10, 12);
        String eth1Mac = machine.getNetworkAdapter(1l).getMACAddress();
        eth1Mac = eth1Mac.substring(0, 2) + ":" + eth1Mac.substring(2, 4) + ":" + eth1Mac.substring(4, 6) + ":" + eth1Mac.substring(6, 8) + ":"
                + eth1Mac.substring(8, 10) + ":" + eth1Mac.substring(10, 12);

        // create the new /etc/sysconfig/network-scripts file, and copy to guest
        String eth0Content = "DEVICE=eth0\n" +
                "HWADDR=" + eth0Mac + "\n" +
                "ONBOOT=yes\n" +
                "BOOTPROTO=dhcp\n" +
                "USERCTL=no";

        String eth1Content = "DEVICE=eth1\n" +
                "HWADDR=" + eth1Mac + "\n" +
                "IPADDR=" + ip + "\n" +
                // "GATEWAY="+gatewayIp+"\n"+
                "NETMASK=" + mask + "\n" +
                "ONBOOT=yes\n" +
                "BOOTPROTO=none\n" +
                "USERCTL=no";
        // NM_CONTROLLED="no"

        createFile(machineGuid, login, password, "/tmp/ifcfg-eth0", eth0Content, endTime);
        createFile(machineGuid, login, password, "/tmp/ifcfg-eth1", eth1Content, endTime);

        String updateHostnameGatewayContent = "#!/bin/bash\n" +
                "echo GATEWAY=" + gatewayIp + " | sudo tee /etc/sysconfig/network\n" +
                "echo GATEWAY_IF=eth1 | sudo tee /etc/sysconfig/network\n";
        // executeScript(machineGuid, login, password, "updatehostnamegateway.sh", updateHostnameGatewayContent);

        String updateinterfacesContent = "#!/bin/bash\n" +
                "cat /tmp/ifcfg-eth0 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth0\n" +
                "sudo cp /etc/sysconfig/network-scripts/ifcfg-eth0 /etc/sysconfig/network-scripts/ifcfg-eth1\n" +
                "sudo chmod a+r /etc/sysconfig/network-scripts/ifcfg-eth1\n" +
                "cat /tmp/ifcfg-eth1 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth1\n";
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
                "sleep 5\n" +
                "sudo ifup eth0\n" +
                "sudo ifup eth1 " + ip + "\n";
        executeScript(machineGuid, login, password, "test.sh", test, endTime);

        String stopfirewallContent = "#!/bin/bash\n" +
                "sudo /etc/init.d/iptables stop\n" +
                "sudo chkconfig iptables off\n";
        executeScript(machineGuid, login, password, "stopfirewall.sh", stopfirewallContent, endTime);

    }

}
