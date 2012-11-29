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
import org.virtualbox_4_2.IGuestOSType;
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

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.UbuntuGuest;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuest;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuestProvider;


public class VirtualBoxService42 implements VirtualBoxService {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualBoxService42.class.getName());
    
    private static final String cloudify_shared_folder = "cloudify";
    
    private VirtualBoxManager virtualBoxManager;
    
    private VirtualBoxGuestController virtualBoxGuestController;
    
    private VirtualBoxGuestProvider virtualBoxGuestProvider;
    
    private static final ReentrantLock mutex = new ReentrantLock();
    
    public VirtualBoxService42() {
        this.virtualBoxManager = VirtualBoxManager.createInstance(null);
        this.virtualBoxGuestController = new VirtualBoxGuestController42(this.virtualBoxManager);
        this.virtualBoxGuestProvider = new VirtualBoxGuestProvider(this.virtualBoxGuestController);
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
        progress.waitForCompletion(60*3000);
        
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
        
        IMachine machine = virtualBoxManager.getVBox().findMachine(machineGuid);        
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        
        // wait for the guest OS to be ready
        int nbTry = 0;
        boolean isReady = false;
        Exception lastException = null;
        do{
            nbTry++;
            try{
                guest.ping(machineGuid, login, password);
                
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
        
        // create a script to update the hostname
        guest.updateHostname(machineGuid, login, password, machine.getName());
    }
    
    public void grantAccessToSharedFolder(String machineGuid, String login, String password) throws Exception{
     
        // add the current user to the group vboxsf to read the shared folder
        String addUserScript = "#!/bin/bash\n"+
                "sudo usermod -a -G vboxsf $(whoami)";
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.executeScript(machineGuid, login, password, "adduser.sh", addUserScript);
    }
    
    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gateway) throws Exception {
        
        logger.log(Level.INFO, "Trying to update network interfaces on VM '"+machineGuid+"'");
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        String eth0Mac = machine.getNetworkAdapter(0l).getMACAddress();
        eth0Mac = eth0Mac.substring(0,2)+":"+eth0Mac.substring(2, 4)+":"+eth0Mac.substring(4, 6)+":"+eth0Mac.substring(6, 8)+":"+eth0Mac.substring(8, 10)+":"+eth0Mac.substring(10, 12);
        String eth1Mac = machine.getNetworkAdapter(1l).getMACAddress();
        eth1Mac = eth1Mac.substring(0,2)+":"+eth1Mac.substring(2, 4)+":"+eth1Mac.substring(4, 6)+":"+eth1Mac.substring(6, 8)+":"+eth1Mac.substring(8, 10)+":"+eth1Mac.substring(10, 12);
        
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateNetworkingInterfaces(machineGuid, login, password, ip, mask, gateway, eth0Mac, eth1Mac);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts) throws InterruptedException, Exception {

        logger.log(Level.INFO, "Trying to update hosts on VM '"+machineGuid+"'");
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateHosts(machineGuid, login, password, hosts);
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
