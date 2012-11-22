package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.virtualbox_4_2.CleanupMode;
import org.virtualbox_4_2.Holder;
import org.virtualbox_4_2.IAppliance;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IGuest;
import org.virtualbox_4_2.IGuestProcess;
import org.virtualbox_4_2.IGuestSession;
import org.virtualbox_4_2.IHostNetworkInterface;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IMedium;
import org.virtualbox_4_2.INetworkAdapter;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.IVirtualSystemDescription;
import org.virtualbox_4_2.ImportOptions;
import org.virtualbox_4_2.MachineState;
import org.virtualbox_4_2.NetworkAttachmentType;
import org.virtualbox_4_2.ProcessCreateFlag;
import org.virtualbox_4_2.ProcessStatus;
import org.virtualbox_4_2.ProcessWaitForFlag;
import org.virtualbox_4_2.VBoxException;
import org.virtualbox_4_2.VirtualBoxManager;
import org.virtualbox_4_2.VirtualSystemDescriptionType;
import org.virtualbox_4_2.jaxws.InvalidObjectFaultMsg;
import org.virtualbox_4_2.jaxws.RuntimeFaultMsg;
import org.virtualbox_4_2.jaxws.VboxPortType;


public class VirtualBoxService42 implements VirtualBoxService {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualBoxService42.class.getName());
    
    private static final String cloudify_shared_folder = "cloudify";
    
    private VirtualBoxManager virtualBoxManager;
    
    private static final ReentrantLock mutex = new ReentrantLock();
    
    public VirtualBoxService42() {
        this.virtualBoxManager = VirtualBoxManager.createInstance(null);
    }

    public void connect(String url, String login, String password) throws VirtualBoxException{
        try{
            logger.log(Level.INFO, "Trying to connect to "+url+" using login: "+login);
            this.virtualBoxManager.connect(url, login, password);
        }
        catch(Exception ex){
            throw new VirtualBoxException("Unable to connect to "+url+" with login "+login, ex);
        }
    }
    
    public VirtualBoxMachineInfo[] getAll() throws IOException {
        return this.getAll(null);
    }

    public VirtualBoxMachineInfo[] getAll(String prefix) throws IOException {

        ArrayList<VirtualBoxMachineInfo> result = new ArrayList<VirtualBoxMachineInfo>();

        for(IMachine m : this.virtualBoxManager.getVBox().getMachines()){
            VirtualBoxMachineInfo vboxInfo = new VirtualBoxMachineInfo();
            vboxInfo.setGuid(m.getId());
            vboxInfo.setMachineName(m.getName());

            if (prefix != null) {
                if (vboxInfo.getMachineName().startsWith(prefix)) {
                    result.add(vboxInfo);
                }
            }
            else {
                result.add(vboxInfo);
            }
        }
        
        return result.toArray(new VirtualBoxMachineInfo[0]);
    }

    public VirtualBoxMachineInfo getInfo(String name) throws IOException {
        for (VirtualBoxMachineInfo info : getAll()) {
            if (info.getMachineName().equals(name)) {
                return info;
            }
        }

        return null;
    }

    @SuppressWarnings("restriction")
    private void getDescription(VboxPortType port, String obj, Holder<List<org.virtualbox_4_2.VirtualSystemDescriptionType>> aTypes, Holder<List<String>> aRefs, Holder<List<String>> aOvfValues, Holder<List<String>> aVBoxValues, Holder<List<String>> aExtraConfigValues) {
        try {
            javax.xml.ws.Holder<List<org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType>>   tmp_aTypes = new  javax.xml.ws.Holder<List<org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType>>();            
            javax.xml.ws.Holder<List<String>>   tmp_aRefs = new  javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>>   tmp_aOvfValues = new  javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>>   tmp_aVBoxValues = new  javax.xml.ws.Holder<List<String>>();
            javax.xml.ws.Holder<List<String>>   tmp_aExtraConfigValues = new  javax.xml.ws.Holder<List<String>>();
            port.iVirtualSystemDescriptionGetDescription(obj, tmp_aTypes, tmp_aRefs, tmp_aOvfValues, tmp_aVBoxValues, tmp_aExtraConfigValues);
            
            // this one is buggy
            //aTypes.value = Helper.convertEnums(org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType.class, org.virtualbox_4_2.VirtualSystemDescriptionType.class, tmp_aTypes.value);
            aTypes.value = new ArrayList<VirtualSystemDescriptionType>();
            for(org.virtualbox_4_2.jaxws.VirtualSystemDescriptionType tmp_t : tmp_aTypes.value){
                VirtualSystemDescriptionType t = VirtualSystemDescriptionType.fromValue(tmp_t.value());
                aTypes.value.add(t);
            }
            aRefs.value = tmp_aRefs.value;
            aOvfValues.value = tmp_aOvfValues.value;
            aVBoxValues.value = tmp_aVBoxValues.value;
            aExtraConfigValues.value = tmp_aExtraConfigValues.value;
         } catch (InvalidObjectFaultMsg e) {
              throw new VBoxException(e, e.getMessage());
         } catch (RuntimeFaultMsg e) {
              throw new VBoxException(e, e.getMessage());
         }
    }
    
    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, String hostOnlyInterface, String hostSharedFolder) throws Exception {

        logger.log(Level.INFO, "Trying to create VM '"+vmname+"' cpus:"+cpus+" memory:"+memory+" from template: "+boxPath);
        
        File boxPathFile = new File(boxPath);

        IAppliance appliance = this.virtualBoxManager.getVBox().createAppliance();
        appliance.read(boxPathFile.getAbsolutePath());
        appliance.interpret();
        
        IVirtualSystemDescription virtualSystemDescription = appliance.getVirtualSystemDescriptions().get(0);
        
        Holder<List<VirtualSystemDescriptionType>> types = new Holder<List<VirtualSystemDescriptionType>>();
        Holder<List<String>> refs = new Holder<List<String>>();
        Holder<List<String>> ovfValues = new Holder<List<String>>();
        Holder<List<String>> vBoxValues = new Holder<List<String>>();
        Holder<List<String>> extraConfigValues = new Holder<List<String>>();

        // there is a bug in the vboxws function: it's unable to convert the WS Enums, so do it manually
        // virtualSystemDescription.getDescription(types, refs, ovfValues, vBoxValues, extraConfigValues);
        getDescription(appliance.getRemoteWSPort(), virtualSystemDescription.getWrapped(), types, refs, ovfValues, vBoxValues, extraConfigValues);


        List<Boolean> enabled = new ArrayList<Boolean>();
        for(int cpt = 0; cpt < vBoxValues.value.size(); cpt++){
            if(types.value.get(cpt) == VirtualSystemDescriptionType.Name){
                vBoxValues.value.set(cpt, vmname);
            }
            enabled.add(true);
        }

        virtualSystemDescription.setFinalValues(enabled, vBoxValues.value, extraConfigValues.value);
        
        IProgress progress = appliance.importMachines(Arrays.asList(new ImportOptions[0]));
        progress.waitForCompletion(60*1000);
        
        if(!progress.getCompleted()){
            throw new VirtualBoxException("Unable to import VM: Timeout");   
        }
        
        if(progress.getResultCode() != 0){
            throw new VirtualBoxException("Unable to import VM: "+progress.getErrorInfo().getText());
        }
        
        String machineName = appliance.getMachines().get(0);
        
        IMachine machine = virtualBoxManager.getVBox().findMachine(machineName);
        VirtualBoxMachineInfo result = new VirtualBoxMachineInfo();
        result.setGuid(machine.getId());
        result.setMachineName(vmname);
        
        mutex.lock();
        try {
            ISession session = virtualBoxManager.openMachineSession(machine);
            machine = session.getMachine();
            
            try{
                machine.setName(vmname);
                machine.setCPUCount(cpus);
                machine.setMemorySize(memory);
                
                INetworkAdapter nic1 = machine.getNetworkAdapter(0l);
                nic1.setNATNetwork("default");
                nic1.setAttachmentType(NetworkAttachmentType.NAT);
                nic1.setEnabled(true);
                
                INetworkAdapter nic2 = machine.getNetworkAdapter(1l);
                nic2.setAttachmentType(NetworkAttachmentType.HostOnly);
                nic2.setHostOnlyInterface(hostOnlyInterface);
                nic2.setEnabled(true);
                
                if(hostSharedFolder != null && hostSharedFolder.length() > 0){
                    machine.createSharedFolder(cloudify_shared_folder, hostSharedFolder, true, true);
                }
                
                machine.saveSettings();
            }
            finally{
                this.virtualBoxManager.closeMachineSession(session);
            }
        }
        finally {
            mutex.unlock();
        }
        
        return result;
    }

    public void destroy(String machineGuid) throws Exception {
        logger.log(Level.INFO, "Trying to destroy VM '"+machineGuid+"'");
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        Pattern errorMessagePattern = Pattern.compile("Cannot unregister the machine '[^']*' while it is locked \\(0x80BB0007\\)");
        
        mutex.lock();
        try{

            int nbTry = 0;
            boolean removed = false;
            do{
                nbTry++;
                try{
                    List<IMedium> mediums = m.unregister(CleanupMode.Full);
                    m.delete(mediums);
                    removed = true;
                }
                catch(VBoxException ex){
                    Matcher matcher = errorMessagePattern.matcher(ex.getMessage());
                    if(matcher.find()){
                        Thread.sleep(5*1000);
                    }
                    else{
                        throw ex;
                    }
                }
            }while(!removed && nbTry < 10);
        }
        finally{
            mutex.unlock();
        }
    }

    public void start(String machineGuid, String login, String password, boolean headless) throws Exception {
        
        logger.log(Level.INFO, "Trying to start and setup VM '"+machineGuid+"'");
        
        // start the VM
        boolean started = virtualBoxManager.startVm(machineGuid, headless ? "headless" : "gui", 60*1000);
        if(!started){
            throw new VirtualBoxException("Unable to start VM:");
        }
        
        // TODO: do it in another way with Windows OS
        
        // wait for the guest OS to be ready
        int nbTry = 0;
        boolean isReady = false;
        Exception lastException = null;
        do{
            nbTry++;
            try{
                executeCommand(machineGuid, login, password, "/bin/ls",Arrays.asList(new String[0]), Arrays.asList(new String[0]));
                
                isReady = true;
            }
            catch(Exception ex){
                lastException = ex;
                logger.log(Level.FINE, "OS not ready yet, nbTry:"+nbTry, ex);
            }
            
            if(!isReady){
                Thread.sleep(5*1000);
            }
        }while(!isReady && nbTry < 10);
        
        if(!isReady){
            throw new VirtualBoxException("Timeout while waiting guest to be ready", lastException);
        }
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        
        // create a script to update the hostname
        String updatehostnameContent = "#!/bin/bash\n"+
                "sudo sed -i s/.*$/" + machine.getName() + "/ /etc/hostname\n" +
                "sudo service hostname start";
        
        this.executeScript(machineGuid, login, password, "updatehostname.sh", updatehostnameContent);
    }
    
    public void grantAccessToSharedFolder(String machineGuid, String login, String password) throws Exception{
     
        // add the current user to the group vboxsf to read the shared folder
        String addUserScript = "#!/bin/bash\n"+
                "sudo usermod -a -G vboxsf $(whoami)";
        
        this.executeScript(machineGuid, login, password, "adduser.sh", addUserScript);
    }
    
    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask) throws Exception {
        
        logger.log(Level.INFO, "Trying to update network interfaces on VM '"+machineGuid+"'");
        
        // TODO: do it in another way in Windows OS
        
        // create the new /etc/network/interfaces file, and copy to guest
        String interfacesContent = "auto lo\n"+
                "iface lo inet loopback\n\n"+
                "auto eth0\n"+
                "iface eth0 inet dhcp\n\n"+
                "auto eth1\n"+
                "iface eth1 inet static\n"+
                "address "+ip+"\n"+
                "netmask "+mask+"\n";
        
        this.createFile(machineGuid, login, password, "/tmp/interfaces", interfacesContent);
        
        // create the script to update the interfaces file, and copy it to the guest        
        String updateinterfacesContent = "#!/bin/bash\n"+
                "cat /tmp/interfaces | sudo tee /etc/network/interfaces\n"+
                "sudo /etc/init.d/networking restart";  
        
        this.executeScript(machineGuid, login, password, "updateinterfaces.sh", updateinterfacesContent);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts) throws InterruptedException, Exception {

        logger.log(Level.INFO, "Trying to update hosts on VM '"+machineGuid+"'");
        
        // TODO : do it in another way on Windows... detect the OS type of the VM,
        // or connect to it to really detect the OS
//        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
//        if(machine.getOSTypeId() == 0){
//            
//        }
        
        // create the new /etc/hosts file, and copy to guest
        this.createFile(machineGuid, login, password, "/tmp/hosts", hosts);
        
        // create the script to update the interfaces file, and copy it to the guest
        String updatehostsScript = "#!/bin/bash\n"+
                "cat "+"/tmp/hosts"+" | sudo tee /etc/hosts\n";
        
        this.executeScript(machineGuid, login, password, "updatehosts.sh", updatehostsScript);
    }

    public void stop(String machineGuid) throws Exception {
        
        logger.log(Level.INFO, "Trying to stop VM '"+machineGuid+"'");
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        mutex.lock();
        try{
            
            ISession session = virtualBoxManager.openMachineSession(m);
            m = session.getMachine();
            IConsole console = session.getConsole();
            
            IProgress progress = console.powerDown();
            
            progress.waitForCompletion(60*1000);
            
            if(!progress.getCompleted()){
                throw new VirtualBoxException("Unable to shutdown vm "+machineGuid+": Timeout");
            }
            
            if(progress.getResultCode() != 0){
                throw new VirtualBoxException("Unable to shutdown vm "+machineGuid+": "+progress.getErrorInfo().getText());
            }
            
            int nbTry = 0;
            boolean off = false;
            do{
                nbTry++;
                
                off = m.getState() == MachineState.PoweredOff;
                
                if(!off){
                    Thread.sleep(5*1000);
                }
            }
            while(!off && nbTry < 10);
            
            if(!off){
                throw new VirtualBoxException("Unable to shutdown vm "+machineGuid+": Timeout");
            }
        }finally {
            mutex.unlock();
        }
    }
    
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
        
        // copy a file
        this.executeCommand(
                machineGuid,
                login, 
                password,
                "/bin/cp", 
                Arrays.asList("/etc/hostname", destination+".base64"),
                Arrays.asList(new String[0]));
        
        // replace all chars by the base64
        // because the base64 can be too big, append each lines to the file
     
        // create a first "markup"
        this.executeCommand(
                machineGuid,
                login, 
                password,
                "/bin/sed", 
                Arrays.asList("-i", "s/.*/:/g", destination+".base64"),
                Arrays.asList(new String[0]));
        
        for(String line : builder){
            this.executeCommand(
                    machineGuid,
                    login, 
                    password,
                    "/bin/sed", 
                    Arrays.asList("-i", "'s/:/\\n"+line+":/'", destination+".base64"),
                    Arrays.asList(new String[0]));
        }
        
        // replace the final ':' by a new line
        this.executeCommand(
                machineGuid,
                login, 
                password,
                "/bin/sed", 
                Arrays.asList("-i", "s/:/\\n/g", destination+".base64"),
                Arrays.asList(new String[0]));
        
        // convert the base64 file with openssl
        this.executeCommand(
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
    
    /**
     * Create a script file (.sh or whatever) copy it on the guest OS and execute it
     * @param machineGuid
     * @param login
     * @param password
     * @param filename : the filename, will be saved in /tmp/ on the guest OS
     * @param content : content of the script file
     * @throws Exception
     */
    private void executeScript(String machineGuid, String login, String password, String filename, String content) throws Exception {
        
        // copy the file
        this.createFile(machineGuid, login, password, "/tmp/"+filename, content);
        
        logger.log(Level.INFO, "Trying to execute file '"+"/tmp/"+filename+"' on VM '"+machineGuid+"'");
        
        this.executeCommand(machineGuid, login, password, "/bin/chmod", Arrays.asList("a+x","/tmp/"+filename), Arrays.asList(new String[0]));
        this.executeCommand(machineGuid, login, password, "/tmp/"+filename, Arrays.asList(new String[0]), Arrays.asList(new String[0]));
    }

    /**
     * Execute a remote command on the Guest OS
     * @param machineGuid
     * @param login
     * @param password
     * @param command
     * @param args
     * @param envs
     * @return the PID
     * @throws Exception
     */
    private long executeCommand(String machineGuid, String login, String password, String command, List<String> args, List<String> envs) throws Exception {
        
        logger.log(Level.INFO, "Trying to execute command on machine '"+machineGuid+"': "+command+" "+StringUtils.join(args, ' '));
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        mutex.lock();
        
        try{
            ISession session = virtualBoxManager.openMachineSession(m);
            m = session.getMachine();
            IConsole console = session.getConsole();
            IGuest guest = console.getGuest();
            
            IGuestSession guestSession = guest.createSession(login, password, "", "");
            
            try{
                
                IGuestProcess process = guestSession.processCreate(
                        command, 
                        args, 
                        envs,
                        Arrays.asList(ProcessCreateFlag.None),
                        60l*1000l);
                
                process.waitFor(new Long(ProcessWaitForFlag.Terminate.value()), 60l*1000);
                
                if(process.getStatus() != ProcessStatus.TerminatedNormally){
                    throw new VirtualBoxException("Unable to execute command '"+command+" "+StringUtils.join(args, ' ')+"': Status "+process.getStatus() +" ExitCode "+process.getExitCode());
                }
                
                // we can't really get the stdout/stderr with this webservice... 
                
                return process.getPID();
            }
            finally{
                guestSession.close();
                this.virtualBoxManager.closeMachineSession(session);
            }
        }
        finally{
            mutex.unlock();
        }
    }
    
    public VirtualBoxHostOnlyInterface getHostOnlyInterface(String hostonlyifName) {
        
        for(IHostNetworkInterface hostonlyif : this.virtualBoxManager.getVBox().getHost().getNetworkInterfaces()){
            if(hostonlyif.getName().equals(hostonlyifName)){
                VirtualBoxHostOnlyInterface result = new VirtualBoxHostOnlyInterface();
                result.setIp(hostonlyif.getIPAddress());
                result.setMask(hostonlyif.getNetworkMask());
                return result;
            }
        }
        
        return null;
    }
}
