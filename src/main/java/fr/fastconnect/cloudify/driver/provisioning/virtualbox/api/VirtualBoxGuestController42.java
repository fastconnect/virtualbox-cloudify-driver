package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IGuest;
import org.virtualbox_4_2.IGuestProcess;
import org.virtualbox_4_2.IGuestSession;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.ProcessCreateFlag;
import org.virtualbox_4_2.ProcessStatus;
import org.virtualbox_4_2.ProcessWaitForFlag;
import org.virtualbox_4_2.VirtualBoxManager;

public class VirtualBoxGuestController42 implements VirtualBoxGuestController {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(VirtualBoxGuestController42.class.getName());

    private VirtualBoxManager virtualBoxManager;
    
    private static final ReentrantLock mutex = new ReentrantLock();
    
    public VirtualBoxGuestController42(VirtualBoxManager virtualBoxManager) {
        this.virtualBoxManager = virtualBoxManager;
    }

    /* (non-Javadoc)
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#executeCommand(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.List, java.util.List)
     */
    public long executeCommand(String machineGuid, String login, String password, String command, List<String> args, List<String> envs) throws Exception {
        
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
                
                //process.waitForArray(Arrays.asList(ProcessWaitForFlag.Terminate, ProcessWaitForFlag.StdErr, ProcessWaitForFlag.StdOut), 60l*1000);
                //process.waitForArray(Arrays.asList(ProcessWaitForFlag.Terminate), 60l*1000);
                process.waitFor(new Long(ProcessWaitForFlag.Terminate.value()), 60l*1000);
                
                if(process.getStatus() != ProcessStatus.TerminatedNormally){
                    
                    throw new VirtualBoxException("Unable to execute command '"+command+" "+StringUtils.join(args, ' ')+"': Status "+process.getStatus() +" ExitCode "+process.getExitCode());
                }
                
                // TODO: get the stdout/stderr with this webservice... 
                
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
}
