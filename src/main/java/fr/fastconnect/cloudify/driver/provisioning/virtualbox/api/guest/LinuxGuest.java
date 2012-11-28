package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController;

public abstract class LinuxGuest implements VirtualBoxGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(LinuxGuest.class.getName());
    
    protected VirtualBoxGuestController virtualBoxGuestController;
    
    public LinuxGuest(VirtualBoxGuestController virtualBoxGuestController) {
        this.virtualBoxGuestController = virtualBoxGuestController;
    }

    public void ping(String machineGuid, String login, String password) throws Exception {
        // do a ls command just to test if it's up
        virtualBoxGuestController.executeCommand(machineGuid, login, password, "/bin/ls",Arrays.asList(new String[0]), Arrays.asList(new String[0]));
    }
    

    /* (non-Javadoc)
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#createFile(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void createFile(String machineGuid, String login, String password, String destination, String content) throws Exception{
        
        logger.log(Level.INFO, "Trying to create file '"+destination+"' on machine '"+machineGuid+"'");
        
        // TODO: do it in another way with Windows OS
        
        // ultra hack: VirtualBox has a 'fileCreate' function, but not in the WS API
        // it's not possible to use '>' or '|' with "/bin/bash" command
        // and the content of the file could have special chars for bash
        // so the hack is to copy an existing file, change it with sed with a base64 content to avoid special chars
        // and decode it with openssl
        
        byte[] bytes = Base64.encodeBase64((content+"\n").getBytes());
        
        // openssl has a limit for each line in base64
        // should be 76 but seems that sometimes it's not working
        final int maxBase64lenght = 60;
        String base64 = new String(bytes);
        int linesNumber = base64.length() / maxBase64lenght;
        if((base64.length() % maxBase64lenght) > 0){
            linesNumber++;
        }
        
        List<String> builder = new ArrayList<String>();
        
        for(int cpt = 0; cpt < linesNumber; cpt++){
            if(cpt == linesNumber-1){
                builder.add(base64.substring(cpt*maxBase64lenght, base64.length()));   
            }
            else {
                builder.add(base64.substring(cpt*maxBase64lenght, (cpt+1)*maxBase64lenght));   
            }
        }
        
        // create an empty file
        virtualBoxGuestController.executeCommand(
                machineGuid,
                login, 
                password,
                "/bin/touch", 
                Arrays.asList(destination+".base64"),
                Arrays.asList(new String[0]));
        
        // replace all chars by the base64
        // because the base64 can be too big, append each lines to the file
     
        for(String line : builder){
            virtualBoxGuestController.executeCommand(
                    machineGuid,
                    login, 
                    password,
                    "/bin/bash", 
                    Arrays.asList("-c", "echo '"+line+"' >> "+ destination+".base64"),
                    Arrays.asList(new String[0]));
        }
        
        // convert the base64 file with openssl
        virtualBoxGuestController.executeCommand(
                machineGuid,
                login, 
                password,
                "/usr/bin/openssl", 
                Arrays.asList("enc", "-base64", "-d", "-base64", "-in", destination+".base64", "-out", destination),
                Arrays.asList(new String[0]));
        
        // remove the base64 file
//        this.executeCommand(
//                machineGuid,
//                login, 
//                password,
//                "/bin/rm", 
//                Arrays.asList(destination+".base64"),
//                Arrays.asList(new String[0]));
    }

    /* (non-Javadoc)
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#executeScript(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void executeScript(String machineGuid, String login, String password, String filename, String content) throws Exception {
        
        // copy the file
        this.createFile(machineGuid, login, password, "/tmp/"+filename, content);
        
        logger.log(Level.INFO, "Trying to execute file '"+"/tmp/"+filename+"' on VM '"+machineGuid+"'");
        
        virtualBoxGuestController.executeCommand(
                machineGuid, 
                login,
                password, 
                "/bin/chmod", 
                Arrays.asList("a+x","/tmp/"+filename), 
                Arrays.asList(new String[0]));
        
        virtualBoxGuestController.executeCommand(
                machineGuid,
                login, 
                password, 
                "/tmp/"+filename, 
                Arrays.asList(new String[0]), 
                Arrays.asList(new String[0]));
    }
    
    public void updateHosts(String machineGuid, String login, String password, String hosts) throws InterruptedException, Exception {
        
        // create the new /etc/hosts file, and copy to guest
        createFile(machineGuid, login, password, "/tmp/hosts", hosts);
        
        // create the script to update the interfaces file, and copy it to the guest
        String updatehostsScript = "#!/bin/bash\n"+
                "cat "+"/tmp/hosts"+" | sudo tee /etc/hosts\n";
        
        executeScript(machineGuid, login, password, "updatehosts.sh", updatehostsScript);
    }
}
