package fr.fastconnect.cloudify.driver.provisioning.virtualbox;

import org.virtualbox_4_2.NetworkAttachmentType;

public class PublicInterfaceConfig {

    private NetworkAttachmentType attachmentType;
    private String interfaceName;

    public PublicInterfaceConfig() {
    }

    public void setBridgeInterface(String interfaceName) {
        this.attachmentType = NetworkAttachmentType.Bridged;
        this.interfaceName = interfaceName;
    }

    public void setHostOnlyInterface(String interfaceName) {
        this.attachmentType = NetworkAttachmentType.HostOnly;
        this.interfaceName = interfaceName;
    }

    public NetworkAttachmentType getAttachmentType() {
        return attachmentType;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

}
