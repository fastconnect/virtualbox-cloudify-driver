package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxException;
import org.virtualbox_4_2.*;

public abstract class LinuxGuest extends BaseGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(LinuxGuest.class.getName());

    public LinuxGuest(VirtualBoxManager virtualBoxManager) {
        super(virtualBoxManager);
    }

    public void ping(String machineGuid, String login, String password, long endTime) throws Exception {
        // do a ls command just to test if it's up
        this.executeCommand(machineGuid, login, password, "/bin/ls", Arrays.asList(new String[0]), endTime);
    }

    /*
     * (non-Javadoc)
     * 
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#createFile(java.lang.String, java.lang.String,
     * java.lang.String, java.lang.String, java.lang.String)
     */
    /*public void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception {
        logger.log(Level.INFO, "Trying to create file '" + destination + "' on machine '" + machineGuid + "'");

        // ultra hack: VirtualBox has a 'fileCreate' function, but not in the WS API
        // it's not possible to use '>' or '|' with "/bin/bash" command
        // and the content of the file could have special chars for bash
        // so the hack is to copy an existing file, change it with sed with a base64 content to avoid special chars
        // and decode it with openssl

        List<String> builder = this.createSplittedBase64Content(content);

        // create an empty file
        this.executeCommand(
                machineGuid,
                login,
                password,
                "/bin/touch",
                Arrays.asList(destination + ".base64"),
                endTime);

        // replace all chars by the base64
        // because the base64 can be too big, append each lines to the file
        for (String line : builder) {
            this.executeCommand(
                    machineGuid,
                    login,
                    password,
                    "/bin/bash",
                    //"/bin/echo",
                    //Arrays.asList("-c", "&quot;echo " + line + " >> " + destination + ".base64&quot;"),
                    Arrays.asList("-c", "'echo " + line + " >> " + destination + ".base64'"),
                    //Arrays.asList("-c", "'echo " + line + " | tee -a " + destination + ".base64'"),
                    //Arrays.asList(line,">>", destination + ".base64"),
                    endTime);
        }

        // convert the base64 file with openssl
        this.executeCommand(
                machineGuid,
                login,
                password,
                "/usr/bin/openssl",
                Arrays.asList("enc", "-base64", "-d", "-base64", "-in", destination + ".base64", "-out", destination),
                endTime);

        // remove the base64 file
        // this.executeCommand(
        // machineGuid,
        // login,
        // password,
        // "/bin/rm",
        // Arrays.asList(destination+".base64"),
        // Arrays.asList(new String[0]));
    }*/


    /*
     * (non-Javadoc)
     *
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#createFile(java.lang.String, java.lang.String,
     * java.lang.String, java.lang.String, java.lang.String)
     */
    public void createFile(String machineGuid, String login, String password, String destination, String content, long endTime) throws Exception {
        logger.log(Level.INFO, "Trying to create file '" + destination + "' on machine '" + machineGuid + "'");

        IMachine m = virtualBoxManager.getVBox().findMachine(machineGuid);

        mutex.lock();

        try {
            ISession session = virtualBoxManager.openMachineSession(m);
            m = session.getMachine();
            IConsole console = session.getConsole();
            IGuest guest = console.getGuest();

            IGuestSession guestSession = guest.createSession(login, password, "", "");
            try {

                long timeLeft = endTime - System.currentTimeMillis();
                IGuestProcess process = guestSession.processCreate("/bin/bash", Arrays.asList(new String[0]), Arrays.asList(new String[0]),
                        Arrays.asList(ProcessCreateFlag.None), timeLeft);

                timeLeft = endTime - System.currentTimeMillis();
                process.waitFor(new Long(ProcessWaitForFlag.Start.value()), timeLeft);

                timeLeft = endTime - System.currentTimeMillis();
                process.waitFor(new Long(ProcessWaitForFlag.StdIn.value()), timeLeft);

                String command = "echo '"+content+"' >> "+destination+"\nexit\n";
                long maxWaitForWrite = 1*1000l;
                try {
                    process.writeArray(0l, Arrays.asList(ProcessInputFlag.EndOfFile), command.getBytes("UTF-8"), (timeLeft > maxWaitForWrite ? maxWaitForWrite : timeLeft));
                }catch(VBoxException vbe){
                    if(vbe.getMessage().contains("VERR_TIMEOUT (0x80BB0005)")){
                        // "write" hang, don't know why :(
                        // maybe there's a solution?: https://www.virtualbox.org/pipermail/vbox-dev/2013-June/011556.html
                    }
                    else {
                        throw vbe;
                    }
                }

                timeLeft = endTime - System.currentTimeMillis();
                process.waitFor(new Long(ProcessWaitForFlag.Terminate.value()), timeLeft);
                if (process.getStatus() != ProcessStatus.TerminatedNormally) {
                    throw new VirtualBoxException("Unable to create file '" + destination + "': Status "
                            + process.getStatus() + " ExitCode " + process.getExitCode());
                }
            } finally {
                guestSession.close();
                this.virtualBoxManager.closeMachineSession(session);
            }
        } finally {
            mutex.unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxGuestController#executeScript(java.lang.String, java.lang.String,
     * java.lang.String, java.lang.String, java.lang.String)
     */
    public void executeScript(String machineGuid, String login, String password, String filename, String content, long endTime) throws Exception {

        // copy the file
        this.createFile(machineGuid, login, password, "/tmp/" + filename, content, endTime);

        logger.log(Level.INFO, "Trying to execute file '" + "/tmp/" + filename + "' on VM '" + machineGuid + "'");

        this.executeCommand(
                machineGuid,
                login,
                password,
                "/bin/chmod",
                Arrays.asList("a+x", "/tmp/" + filename),
                endTime);

        this.executeCommand(
                machineGuid,
                login,
                password,
                "/tmp/" + filename,
                Arrays.asList(new String[0]),
                endTime);
    }

    public void updateHosts(String machineGuid, String login, String password, String hosts, long endTime) throws InterruptedException, Exception {

        // create the new /etc/hosts file, and copy to guest
        createFile(machineGuid, login, password, "/tmp/hosts", hosts, endTime);

        // create the script to update the interfaces file, and copy it to the guest
        String updatehostsScript = "#!/bin/bash\n" +
                "cat " + "/tmp/hosts" + " | sudo tee /etc/hosts\n";

        executeScript(machineGuid, login, password, "updatehosts.sh", updatehostsScript, endTime);
    }

    public void grantAccessToSharedFolder(String machineGuid, String login, String password, long endTime) throws Exception {

        // add the current user to the group vboxsf to read the shared folder
        String addUserScript = "#!/bin/bash\n" +
                "sudo usermod -a -G vboxsf $(whoami)";
        this.executeScript(machineGuid, login, password, "adduser.sh", addUserScript, endTime);
    }

    protected void waitPublicAddressToBeReachable(String machineGuid, long endTime) throws Exception {
        while (System.currentTimeMillis() < endTime) {
            String ipAddress = this.getPublicAddressIP(machineGuid);
            if (ipAddress != null) {
                InetAddress inetadrr = InetAddress.getByName(ipAddress);
                if (inetadrr.isReachable(5000)) {
                    logger.fine("Waiting public ip: " + ipAddress);
                    return;
                }
            }
            try {
                logger.fine("Waiting public ip...");
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new TimeoutException("Timeout allocating public ip.");
    }

    protected void waitUntilPrivateIPIsConfigured(String machineGuid, String privateAddrIP, long endTime) throws Exception {
        while (System.currentTimeMillis() < endTime) {
            if (privateAddrIP.equals(this.getPrivateAddressIP(machineGuid))) {
                return;
            }
            logger.info("Private IP is not yet configured, waiting...");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new TimeoutException("Timeout configuring private ip.");
    }
}
