package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cloudifysource.esc.driver.provisioning.storage.VolumeDetails;
import org.virtualbox_4_2.CleanupMode;
import org.virtualbox_4_2.DeviceType;
import org.virtualbox_4_2.Holder;
import org.virtualbox_4_2.IAppliance;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IHostNetworkInterface;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IMedium;
import org.virtualbox_4_2.IMediumAttachment;
import org.virtualbox_4_2.INetworkAdapter;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.IStorageController;
import org.virtualbox_4_2.IVirtualSystemDescription;
import org.virtualbox_4_2.ImportOptions;
import org.virtualbox_4_2.MachineState;
import org.virtualbox_4_2.NetworkAttachmentType;
import org.virtualbox_4_2.VBoxException;
import org.virtualbox_4_2.VirtualBoxManager;
import org.virtualbox_4_2.VirtualSystemDescriptionType;
import org.virtualbox_4_2.jaxws.InvalidObjectFaultMsg;
import org.virtualbox_4_2.jaxws.MediumVariant;
import org.virtualbox_4_2.jaxws.RuntimeFaultMsg;
import org.virtualbox_4_2.jaxws.VboxPortType;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuest;
import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest.VirtualBoxGuestProvider;


public class VirtualBoxService42 implements VirtualBoxService {
    
    private static final int SERVER_POLLING_INTERVAL_MILLIS = 10 * 1000; // 10 seconds
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualBoxService42.class.getName());
    
    private static final String cloudify_shared_folder = "cloudify";
    
    private static final String defaultController = "SATA Controller";
    
    private VirtualBoxManager virtualBoxManager;
    
    private VirtualBoxGuestProvider virtualBoxGuestProvider;
    
    private static final ReentrantLock computeMutex = new ReentrantLock();
    
    private static final ReentrantLock storageMutex = new ReentrantLock();
    
    public VirtualBoxService42() {
        this.virtualBoxManager = VirtualBoxManager.createInstance(null);
        this.virtualBoxGuestProvider = new VirtualBoxGuestProvider(this.virtualBoxManager);
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
    
    public VirtualBoxMachineInfo create(String boxPath, String vmname, long cpus, long memory, String hostOnlyInterface, String hostSharedFolder, long endTime) throws Exception {

        logger.log(Level.INFO, "Trying to create VM '"+vmname+"' cpus:"+cpus+" memory:"+memory+" from template: "+boxPath);
        
        File boxPathFile = new File(boxPath);

        IAppliance appliance = this.virtualBoxManager.getVBox().createAppliance();
        
        long timeLeft = endTime - System.currentTimeMillis();
        IProgress progress = appliance.read(boxPathFile.getAbsolutePath());
        progress.waitForCompletion((int)timeLeft);
        if(!progress.getCompleted()){
            throw new TimeoutException("Unable to import VM: Timeout");   
        }
        if(progress.getResultCode() != 0){
            throw new VirtualBoxException("Unable to import VM: "+progress.getErrorInfo().getText());
        }
        
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
        
        timeLeft = endTime - System.currentTimeMillis();
        progress = appliance.importMachines(Arrays.asList(new ImportOptions[0]));
        progress.waitForCompletion((int)timeLeft);
        
        if(!progress.getCompleted()){
            throw new TimeoutException("Unable to import VM: Timeout");   
        }
        
        if(progress.getResultCode() != 0){
            throw new VirtualBoxException("Unable to import VM: "+progress.getErrorInfo().getText());
        }
        
        String machineName = appliance.getMachines().get(0);
        
        IMachine machine = virtualBoxManager.getVBox().findMachine(machineName);
        VirtualBoxMachineInfo result = new VirtualBoxMachineInfo();
        result.setGuid(machine.getId());
        result.setMachineName(vmname);
        
        computeMutex.lock();
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
                
                IStorageController storageController = machine.getStorageControllerByName(defaultController);
                storageController.setPortCount(storageController.getMaxPortCount());
                
                machine.saveSettings();
            }
            finally{
                this.virtualBoxManager.closeMachineSession(session);
            }
        }
        finally {
            computeMutex.unlock();
        }
        
        return result;
    }

    public void destroy(String machineGuid, long endTime) throws Exception {
        logger.log(Level.INFO, "Trying to destroy VM '"+machineGuid+"'");
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        Pattern errorMessagePattern = Pattern.compile("Cannot unregister the machine '[^']*' while it is locked \\(0x80BB0007\\)");
        
        computeMutex.lock();
        try{

            boolean removed = false;
            while(!removed && System.currentTimeMillis() < endTime){
                try{
                    List<IMedium> mediums = m.unregister(CleanupMode.Full);
                    m.delete(mediums);
                    removed = true;
                }
                catch(VBoxException ex){
                    Matcher matcher = errorMessagePattern.matcher(ex.getMessage());
                    if(matcher.find()){
                        if (System.currentTimeMillis() > endTime) {
                            throw new TimeoutException("timeout destroying server.");
                        }

                        Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
                    }
                    else{
                        throw ex;
                    }
                }
            };
        }
        finally{
            computeMutex.unlock();
        }
    }

    public void start(String machineGuid, String login, String password, boolean headless, long endTime) throws Exception {
        
        logger.log(Level.INFO, "Trying to start and setup VM '"+machineGuid+"'");
        
        long timeLeft = endTime - System.currentTimeMillis();
        
        // start the VM
        boolean started = virtualBoxManager.startVm(machineGuid, headless ? "headless" : "gui", (int)timeLeft);
        if(!started){
            throw new VirtualBoxException("Unable to start VM:");
        }
        
        IMachine machine = virtualBoxManager.getVBox().findMachine(machineGuid);        
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        
        // wait for the guest OS to be ready
        boolean isReady = false;
        while(!isReady && System.currentTimeMillis() < endTime){
            try{
                guest.ping(machineGuid, login, password, endTime);
                
                isReady = true;
            }
            catch(Exception ex){
                logger.log(Level.FINE, "OS not ready yet", ex);
                
                if (System.currentTimeMillis() > endTime) {
                    throw new TimeoutException("timeout creating server.");
                }

                Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
            }
        }
        
        
        //machine.getGuestPropertyValue("/VirtualBox/GuestInfo/Net/0/V4/IP")
        
        // create a script to update the hostname
        guest.updateHostname(machineGuid, login, password, machine.getName(), endTime);
    }
    
    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception{
     
        // add the current user to the group vboxsf to read the shared folder
        String addUserScript = "#!/bin/bash\n"+
                "sudo usermod -a -G vboxsf $(whoami)";
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.executeScript(machineGuid, login, password, "adduser.sh", addUserScript, endTime);
    }
    
    public void updateNetworkingInterfaces(String machineGuid, String login, String password, String ip, String mask, String gateway, long endTime) throws Exception {
        
        logger.log(Level.INFO, "Trying to update network interfaces on VM '"+machineGuid+"'");
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        String eth0Mac = machine.getNetworkAdapter(0l).getMACAddress();
        eth0Mac = eth0Mac.substring(0,2)+":"+eth0Mac.substring(2, 4)+":"+eth0Mac.substring(4, 6)+":"+eth0Mac.substring(6, 8)+":"+eth0Mac.substring(8, 10)+":"+eth0Mac.substring(10, 12);
        String eth1Mac = machine.getNetworkAdapter(1l).getMACAddress();
        eth1Mac = eth1Mac.substring(0,2)+":"+eth1Mac.substring(2, 4)+":"+eth1Mac.substring(4, 6)+":"+eth1Mac.substring(6, 8)+":"+eth1Mac.substring(8, 10)+":"+eth1Mac.substring(10, 12);
        
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateNetworkingInterfaces(machineGuid, login, password, ip, mask, gateway, eth0Mac, eth1Mac, endTime);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception {

        logger.log(Level.INFO, "Trying to update hosts on VM '"+machineGuid+"'");
        
        IMachine machine = this.virtualBoxManager.getVBox().findMachine(machineGuid);
        VirtualBoxGuest guest = this.virtualBoxGuestProvider.getVirtualBoxGuest(machine.getOSTypeId());
        guest.updateHosts(machineGuid, login, password, hosts, endTime);
    }

    public void stop(String machineGuid, long endTime) throws Exception {
        
        logger.log(Level.INFO, "Trying to stop VM '"+machineGuid+"'");
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        computeMutex.lock();
        try{
            
            ISession session = virtualBoxManager.openMachineSession(m);
            m = session.getMachine();
            IConsole console = session.getConsole();
            
            IProgress progress = console.powerDown();
            
            long timeLeft = endTime - System.currentTimeMillis();
            progress.waitForCompletion((int)timeLeft);
            
            if(!progress.getCompleted()){
                throw new TimeoutException("Unable to shutdown vm "+machineGuid+": Timeout");
            }
            
            if(progress.getResultCode() != 0){
                throw new VirtualBoxException("Unable to shutdown vm "+machineGuid+": "+progress.getErrorInfo().getText());
            }
            
            boolean off = false;
            while(!off && System.currentTimeMillis() < endTime){
                off = m.getState() == MachineState.PoweredOff;
                
                if(!off){
                    if (System.currentTimeMillis() > endTime) {
                        throw new TimeoutException("timeout stopping server.");
                    }

                    Thread.sleep(SERVER_POLLING_INTERVAL_MILLIS);
                }
            }
        }finally {
            computeMutex.unlock();
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
    
    public VirtualBoxVolumeInfo[] getAllVolumesInfo() throws IOException {
        return this.getAllVolumesInfo(null);
    }

    public VirtualBoxVolumeInfo[] getAllVolumesInfo(String prefix) throws IOException {

        ArrayList<VirtualBoxVolumeInfo> result = new ArrayList<VirtualBoxVolumeInfo>();

        for(IMedium v : this.virtualBoxManager.getVBox().getHardDisks()){
            
            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(v.getId());
            info.setName(v.getName());
            info.setLocation(v.getLocation());
            info.setSize((int)(v.getSize() / (1024*1024*1024)));
            
            if (prefix != null) {
                if (info.getName().startsWith(prefix)) {
                    result.add(info);
                }
            }
            else {
                result.add(info);
            }
        }
        
        return result.toArray(new VirtualBoxVolumeInfo[0]);
    }

    public VirtualBoxVolumeInfo getVolumeInfo(String name) throws IOException {
        for (VirtualBoxVolumeInfo info : getAllVolumesInfo()) {
            if (info.getName().equals(name)) {
                return info;
            }
        }

        return null;
    }

    public VirtualBoxVolumeInfo createVolume(String prefix, String path, int size, String hardDiskType, long endTime) throws TimeoutException, VirtualBoxException {
        
        storageMutex.lock();
        
        try{
            int lastId = -1;
            try {
                for(VirtualBoxVolumeInfo v : getAllVolumesInfo(prefix)){
                    String idString = v.getName().substring(prefix.length(), v.getName().length()-prefix.length());
                    int id = Integer.parseInt(idString);
                    if(id > lastId){
                        lastId = id;
                    }
                }
            } catch (NumberFormatException e) {
                throw new VirtualBoxException("Unable to list exiting volumes", e);
            } catch (IOException e) {
                throw new VirtualBoxException("Unable to list exiting volumes", e);
            }
            
            String name = prefix+(lastId+1);
            
            String fullPath = name;
            if(path != null && path.length() > 0){
                fullPath = new File(path, name).getAbsolutePath();
            }
            
            IMedium medium = this.virtualBoxManager.getVBox().createHardDisk(hardDiskType, fullPath);
            Long sizeInBytes = size * 1024l * 1024l * 1024l;
            IProgress progress = medium.createBaseStorage(sizeInBytes, (long)org.virtualbox_4_2.MediumVariant.Standard.value());
            
            long timeLeft = endTime - System.currentTimeMillis();
            progress.waitForCompletion((int)timeLeft);
            
            if(!progress.getCompleted()){
                throw new TimeoutException("Unable to create volume "+fullPath+": Timeout");
            }
            
            if(progress.getResultCode() != 0){
                throw new VirtualBoxException("Unable to create volume "+fullPath+": "+progress.getErrorInfo().getText());
            }
            
            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(medium.getId());
            info.setName(medium.getName());
            info.setLocation(medium.getLocation());
            info.setSize(size);
            
            return info;
        }finally {
            storageMutex.unlock();
        }
    }
    
    public void attachVolume(String machineGuid, String volumeName, int controllerPort, long endTime) {
        
        IMedium medium = null;
        for(IMedium m : virtualBoxManager.getVBox().getHardDisks()){
            if(m.getName().equals(volumeName)){
                medium = m;
                break;
            }
        }
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        m.attachDevice(defaultController, controllerPort, 0, DeviceType.HardDisk, medium);
    }
    
    public void detachVolume(String machineGuid, String volumeName, long endTime) throws VirtualBoxException {
        
        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);
        
        int controllerPort = -1;
        for(IMediumAttachment mediumAttachment : m.getMediumAttachmentsOfController(defaultController)){
            if(mediumAttachment.getMedium().getName().equals(volumeName)){
                controllerPort = mediumAttachment.getPort();
                break;
            }
        }
        
        if(controllerPort == -1){
            throw new VirtualBoxException("Volume "+volumeName+" not attached to VM "+m.getId());
        }
        
        m.attachDevice(defaultController, controllerPort, 0, DeviceType.HardDisk, null);
    }
    
    public VirtualBoxVolumeInfo[] getVolumeInfoByMachine(String machineName){
        IMachine m = virtualBoxManager.getVBox().findMachine(machineName);
        
        ArrayList<VirtualBoxVolumeInfo> result = new ArrayList<VirtualBoxVolumeInfo>();
        
        for(IMediumAttachment mediumAttachment : m.getMediumAttachmentsOfController(defaultController)){
            VirtualBoxVolumeInfo info = new VirtualBoxVolumeInfo();
            info.setGuid(mediumAttachment.getMedium().getId());
            info.setName(mediumAttachment.getMedium().getName());
            info.setLocation(mediumAttachment.getMedium().getLocation());
            info.setSize((int)(mediumAttachment.getMedium().getSize() / (1024*1024*1024)));
            
            result.add(info);
        }
        
        return result.toArray(new VirtualBoxVolumeInfo[0]);
    }
    
    public void deleteVolume(String volumeId, long endTime) throws VirtualBoxException, TimeoutException{
        IMedium medium = null;
        
        for(IMedium m : this.virtualBoxManager.getVBox().getHardDisks()){
            if(m.getId().equals(volumeId)){
                medium = m;
                break;
            }
        }
        
        if(medium == null){
            throw new VirtualBoxException("No volume with ID "+volumeId);
        }
        
        IProgress progress = medium.deleteStorage();
        
        long timeLeft = endTime - System.currentTimeMillis();
        progress.waitForCompletion((int)timeLeft);
        
        if(!progress.getCompleted()){
            throw new TimeoutException("Unable to delete volume "+volumeId+": Timeout");
        }
        
        if(progress.getResultCode() != 0){
            throw new VirtualBoxException("Unable to delete volume "+volumeId+": "+progress.getErrorInfo().getText());
        }
    }
}
