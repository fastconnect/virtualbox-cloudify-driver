package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController;

public class UbuntuGuest extends LinuxGuest {

    public UbuntuGuest(VirtualBoxGuestController virtualBoxGuestController) {
        super(virtualBoxGuestController);
    }
    
    public void updateHostname(String machineGuid, String login, String password, String hostname) throws Exception {
     
        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n"+
                "sudo sed -i s/.*$/" + hostname + "/ /etc/hostname\n" +
                "sudo service hostname start";
        
        executeScript(machineGuid, login, password, "updatehostname.sh", updatehostnameContent);
    }
    
    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gatewayIp, String eth0Mac, String eth1Mac) throws Exception {
        
        // create the new /etc/network/interfaces file, and copy to guest
        String interfacesContent = "auto lo\n"+
                "iface lo inet loopback\n\n"+
                "auto eth0\n"+
                "iface eth0 inet dhcp\n"+
                "\n"+
                "auto eth1\n"+
                "iface eth1 inet static\n"+
                "address "+ip+"\n"+
                "netmask "+mask+"\n"+
                "gateway "+gatewayIp+"\n";
        
        createFile(machineGuid, login, password, "/tmp/interfaces", interfacesContent);
        
        // create the script to update the interfaces file, and copy it to the guest        
        String updateinterfacesContent = "#!/bin/bash\n"+
                "cat /tmp/interfaces | sudo tee /etc/network/interfaces\n"+
                "sudo rm /etc/udev/rules.d/70-persistent-net.rules\n"+
                "sudo udevadm trigger\n"+
                "sudo /etc/init.d/networking restart";  
        
        executeScript(machineGuid, login, password, "updateinterfaces.sh", updateinterfacesContent);
    }
}
