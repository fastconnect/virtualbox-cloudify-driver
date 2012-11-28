package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController;

public class RedhatGuest extends LinuxGuest {

    public RedhatGuest(VirtualBoxGuestController virtualBoxGuestController) {
        super(virtualBoxGuestController);
    }
    
    public void updateHostname(String machineGuid, String login, String password, String hostname) throws Exception {
     
        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n"+
                "sudo sed -i 's/\\(HOSTNAME=\\).*/\\1"+hostname+"/' /etc/sysconfig/network\n" +
                "sudo hostname "+hostname;
        
        executeScript(machineGuid, login, password, "updatehostname.sh", updatehostnameContent);
    }

    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gatewayIp, String eth0Mac, String eth1Mac) throws Exception {
     
         // create the new /etc/sysconfig/network-scripts file, and copy to guest
        String eth0Content = "DEVICE=eth0\n"+
                "HWADDR="+eth0Mac+"\n"+
                "ONBOOT=yes\n"+
                "BOOTPROTO=dhcp\n"+
                "USERCTL=no";
        
        String eth1Content ="DEVICE=eth1\n"+
                "HWADDR="+eth1Mac+"\n"+
                "IPADDR="+ip+"\n"+
                //"GATEWAY="+gatewayIp+"\n"+
                "NETMASK="+mask+"\n"+
                "ONBOOT=yes\n"+
                "BOOTPROTO=none\n"+
                "USERCTL=no";
        //NM_CONTROLLED="no"
        
        createFile(machineGuid, login, password, "/tmp/ifcfg-eth0", eth0Content);
        createFile(machineGuid, login, password, "/tmp/ifcfg-eth1", eth1Content);
        
        String updateHostnameGatewayContent = "#!/bin/bash\n"+
                "echo GATEWAY="+gatewayIp+" | sudo tee /etc/sysconfig/network\n"+
                "echo GATEWAY_IF=eth1 | sudo tee /etc/sysconfig/network\n";
        //executeScript(machineGuid, login, password, "updatehostnamegateway.sh", updateHostnameGatewayContent);
        
        String updateinterfacesContent = "#!/bin/bash\n"+
                "cat /tmp/ifcfg-eth0 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth0\n"+
                "sudo cp /etc/sysconfig/network-scripts/ifcfg-eth0 /etc/sysconfig/network-scripts/ifcfg-eth1\n"+
                "sudo chmod a+r /etc/sysconfig/network-scripts/ifcfg-eth1\n"+
                "cat /tmp/ifcfg-eth1 | sudo tee /etc/sysconfig/network-scripts/ifcfg-eth1\n";
        executeScript(machineGuid, login, password, "updateinterfaces.sh", updateinterfacesContent);
        
        // create the script to update the interfaces file, and copy it to the guest    
        String refreshinterfacesContent = "#!/bin/bash\n"+
                "sudo rm /etc/udev/rules.d/70-persistent-net.rules\n"+
                "sudo udevadm trigger\n";
        executeScript(machineGuid, login, password, "refreshinterfaces.sh", refreshinterfacesContent);
        
        
        
        String test = "#!/bin/bash\n"+
                //"sudo service network restart\n";
                "sudo ifdown eth0\n"+
                "sudo ifdown eth1\n"+
                "sleep 5\n"+
                "sudo ifup eth0\n"+
                "sudo ifup eth1 "+ip+"\n"; 
        executeScript(machineGuid, login, password, "test.sh", test);
        
        // /usr/bin/sudo
        
        
        /*
        String restartinterfaceeth1Content = "#!/bin/bash\n"+
                //"sudo service network restart\n";
                "sudo ifdown eth1 &> /tmp/ifdowneth1.log\n"+
                "sleep 3\n"+
                "sudo ifup eth1 &> /tmp/ifupeth1.log\n";
        executeScript(machineGuid, login, password, "restartinterfaceeth1.sh", restartinterfaceeth1Content);
        
        String restartinterfaceeth0Content = "#!/bin/bash\n"+
                //"sudo service network restart\n";
                "sudo ifdown eth0 2> /dev/null\n"+
                "sleep 3\n"+
                "sudo ifup eth0 2> /dev/null\n";
        executeScript(machineGuid, login, password, "restartinterfaceeth0.sh", restartinterfaceeth0Content);
*/        

        String stopfirewallContent = "#!/bin/bash\n"+
                "sudo /etc/init.d/iptables stop\n";  
        executeScript(machineGuid, login, password, "stopfirewall.sh", stopfirewallContent);
        

    }

}
