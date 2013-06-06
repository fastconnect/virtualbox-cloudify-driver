package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;
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

import fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.VirtualBoxException;

public abstract class BaseGuest implements VirtualBoxGuest {

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(BaseGuest.class.getName());

    private static final ReentrantLock mutex = new ReentrantLock();

    protected VirtualBoxManager virtualBoxManager;

    protected BaseGuest(VirtualBoxManager virtualBoxManager) {
        this.virtualBoxManager = virtualBoxManager;
    }

    List<String> createSplittedBase64Content(String content) {
        byte[] bytes = Base64.encodeBase64((content + "\n").getBytes());

        // openssl has a limit for each line in base64
        // should be 76 but seems that sometimes it's not working
        final int maxBase64lenght = 60;
        String base64 = new String(bytes);
        int linesNumber = base64.length() / maxBase64lenght;
        if ((base64.length() % maxBase64lenght) > 0) {
            linesNumber++;
        }

        List<String> builder = new ArrayList<String>();

        for (int cpt = 0; cpt < linesNumber; cpt++) {
            if (cpt == linesNumber - 1) {
                builder.add(base64.substring(cpt * maxBase64lenght, base64.length()));
            }
            else {
                builder.add(base64.substring(cpt * maxBase64lenght, (cpt + 1) * maxBase64lenght));
            }
        }
        return builder;
    }

    public long executeCommand(String machineGuid, String login, String password, String command, long endTime)
            throws Exception {
        return this.executeCommand(machineGuid, login, password, command, Arrays.asList(new String[0]), Arrays.asList(new String[0]), endTime);
    }

    public long executeCommand(String machineGuid, String login, String password, String command, List<String> args, long endTime)
            throws Exception {
        return this.executeCommand(machineGuid, login, password, command, args, Arrays.asList(new String[0]), endTime);
    }

    public long executeCommand(String machineGuid, String login, String password, String command, List<String> args, List<String> envs, long endTime)
            throws Exception {

        logger.log(Level.INFO, "Trying to execute command on machine '" + machineGuid + "': " + command + " " + StringUtils.join(args, ' '));

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
                IGuestProcess process = guestSession.processCreate(command, args, envs, Arrays.asList(ProcessCreateFlag.None), timeLeft);
                timeLeft = endTime - System.currentTimeMillis();

                // process.waitForArray(Arrays.asList(ProcessWaitForFlag.Terminate, ProcessWaitForFlag.StdErr, ProcessWaitForFlag.StdOut), 60l*1000);
                // process.waitForArray(Arrays.asList(ProcessWaitForFlag.Terminate), 60l*1000);
                process.waitFor(new Long(ProcessWaitForFlag.Terminate.value()), timeLeft);

                if (process.getStatus() != ProcessStatus.TerminatedNormally) {
                    throw new VirtualBoxException("Unable to execute command '" + command + " " + StringUtils.join(args, ' ') + "': Status "
                            + process.getStatus() + " ExitCode " + process.getExitCode());
                }

                // TODO: get the stdout/stderr with this webservice...
                return process.getPID();
            } finally {
                guestSession.close();
                this.virtualBoxManager.closeMachineSession(session);
            }
        } finally {
            mutex.unlock();
        }
    }

    public void runCommandsBeforeBootstrap(String machineGuid, String login, String password, long endTime) throws Exception {
        // Do nothing by default
        return;
    }

}
