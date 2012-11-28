package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

import java.util.List;

public interface VirtualBoxGuestController {

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
    public long executeCommand(String machineGuid, String login, String password, String command, List<String> args, List<String> envs) throws Exception;
}