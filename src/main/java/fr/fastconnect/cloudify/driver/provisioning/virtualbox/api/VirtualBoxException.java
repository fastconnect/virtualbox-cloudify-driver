package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

public class VirtualBoxException extends Exception {

    private static final long serialVersionUID = -3427534641162314761L;

    public VirtualBoxException() {
        super();
    }

    public VirtualBoxException(String message) {
        super(message);
    }

    public VirtualBoxException(String message, Throwable cause) {
        super(message, cause);
    }

    public VirtualBoxException(Throwable cause) {
        super(cause);
    }
}
