package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;
import org.virtualbox_4_2.VirtualBoxManager;

public abstract class LinuxGuest extends BaseGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(LinuxGuest.class.getName());
    
    public LinuxGuest(VirtualBoxManager virtualBoxManager) {
        super(virtualBoxManager);
    }

    public void ping(String machineGuid, String login, String password, long endTime) throws Exception {
        // do a ls command just to test if it's up
        this.executeCommand(machineGuid, login, password, "/bin/ls",Arrays.asList(new String[0]), Arrays.asList(new String[0]), endTime);
    }
    

    /* (non-Javadoc)
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#createFile(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception{
        
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
        this.executeCommand(
                machineGuid,
                login, 
                password,
                "/bin/touch", 
                Arrays.asList(destination+".base64"),
                Arrays.asList(new String[0]),
                endTime);
        
        // replace all chars by the base64
        // because the base64 can be too big, append each lines to the file
     
        for(String line : builder){
            this.executeCommand(
                    machineGuid,
                    login, 
                    password,
                    "/bin/bash", 
                    Arrays.asList("-c", "echo '"+line+"' >> "+ destination+".base64"),
                    Arrays.asList(new String[0]),
                    endTime);
        }
        
        // convert the base64 file with openssl
        this.executeCommand(
                machineGuid,
                login, 
                password,
                "/usr/bin/openssl", 
                Arrays.asList("enc", "-base64", "-d", "-base64", "-in", destination+".base64", "-out", destination),
                Arrays.asList(new String[0]),
                endTime);
        
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
    public void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception {
        
        // copy the file
        this.createFile(machineGuid, login, password, "/tmp/"+filename, content, endTime);
        
        logger.log(Level.INFO, "Trying to execute file '"+"/tmp/"+filename+"' on VM '"+machineGuid+"'");
        
        this.executeCommand(
                machineGuid, 
                login,
                password, 
                "/bin/chmod", 
                Arrays.asList("a+x","/tmp/"+filename), 
                Arrays.asList(new String[0]),
                endTime);
        
        this.executeCommand(
                machineGuid,
                login, 
                password, 
                "/tmp/"+filename, 
                Arrays.asList(new String[0]), 
                Arrays.asList(new String[0]),
                endTime);
    }
    
    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception {
        
        // create the new /etc/hosts file, and copy to guest
        createFile(machineGuid, login, password, "/tmp/hosts", hosts, endTime);
        
        // create the script to update the interfaces file, and copy it to the guest
        String updatehostsScript = "#!/bin/bash\n"+
                "cat "+"/tmp/hosts"+" | sudo tee /etc/hosts\n";
        
        executeScript(machineGuid, login, password, "updatehosts.sh", updatehostsScript, endTime);
    }
}
