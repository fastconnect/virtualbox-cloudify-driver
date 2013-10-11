package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api;

public class VirtualBoxBoxNotFoundException extends VirtualBoxException {
    
    private static final long serialVersionUID = -1440773896424471962L;

    public VirtualBoxBoxNotFoundException() {
        super();
    }

    public VirtualBoxBoxNotFoundException(String message) {
        super(message);
    }

    public VirtualBoxBoxNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public VirtualBoxBoxNotFoundException(Throwable cause) {
        super(cause);
    }

}
