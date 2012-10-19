
cloud {
    // Mandatory. The name of the cloud, as it will appear in the Cloudify UI.
    name = "vbox"

    /********
     * General configuration information about the cloud driver implementation.
     */
    configuration {
        className "fr.fastconnect.cloudify.provisioning.virtualbox.VirtualboxCloudifyDriver"
        // Optional. The template name for the management machines. Defaults to the first template in the templates section below.
        managementMachineTemplate "LARGE_LINUX"
        // Optional. Indicates whether internal cluster communications should use the machine private IP. Defaults to true.
        connectToPrivateIp false
    }

    /*************
     * Provider specific information.
     */
    provider {
        // Mandatory. The name of the provider.
        // When using the default cloud driver, maps to the Compute Service Context provider name.
        provider "virtualbox"

        // Mandatory. All files from this LOCAL directory will be copied to the remote machine directory.
        localDirectory "tools/cli/plugins/esc/vbox/upload"
        
        // Mandatory. The HTTP/S URL where cloudify can be downloaded from by newly started machines.
        cloudifyUrl "http://repository.cloudifysource.org/org/cloudifysource/2.1.1/gigaspaces-cloudify-2.1.1-ga-b1400.zip" 
        // create a archive with the driver in folder lib/plateform/esm
        //cloudifyOverridesUrl "https://opensource.fastconnect.org/maven/content/repositories/opensource/fr/fastconnect/cloudify-overrides/1.0/cloudify-overrides-1.0-gigaspaces_overrides.zip"        
        
        // Mandatory. The prefix for new machines started for servies.
        machineNamePrefix "app-agent-"
        // Optional. Defaults to true. Specifies whether cloudify should try to deploy services on the management machine.
        // Do not change this unless you know EXACTLY what you are doing.
        dedicatedManagementMachines true

        //
        managementOnlyFiles ([])

        // Optional. Logging level for the intenal cloud provider logger. Defaults to INFO.
        sshLoggingLevel "WARNING"

        // Mandatory. Name of the new machine/s started as cloudify management machines.
        managementGroup "app-management-"
        // Mandatory. Number of management machines to start on bootstrap-cloud. In production, should be 2. Can be 1 for dev.
        numberOfManagementMachines 1
        zones (["agent"])

        reservedMemoryCapacityPerMachineInMB 1024

    }

    /*************
     * Cloud authentication information
     */
    user {
        // Optional. Identity used to access cloud.
        // When used with the default driver, maps to the identity used to create the ComputeServiceContext.
        user ""

        // Optional. Key used to access cloud.
        // When used with the default driver, maps to the credential used to create the ComputeServiceContext.
        //apiKey "ENTER_API_KEY"
        apiKey ""


        //keyFile "ENTER_KEY_FILE"
    }


    /***********
     * Cloud machine templates available with this cloud.
     */
    templates ([
                // Mandatory. Template Name.
                LARGE_LINUX : template{
                    // Mandatory. Image ID. Points to a Vmware Template name precreated.
                    imageId "precise64"
                    // Mandatory. Amount of RAM available to machine.
                    machineMemoryMB 4096
                    // Mandatory. Hardware ID.
                    //hardwareId "m1.small"
                    // Optional. Location ID.
                    //locationId "us-east-1"
                    username  "vagrant"
                    password  "vagrant"
                    // Mandatory. Files from the local directory will be copied to this directory on the remote machine.
                    remoteDirectory "/tmp/gs-files"
        
                    // Optional. Overrides to default cloud driver behavior.
                    // When used with the default driver, maps to the overrides properties passed to the ComputeServiceContext a
                    options ([:])
                    overrides ([:])



                },
                MEDIUM_LINUX : template{
                    // Mandatory. Image ID.
                    imageId "precise64"
                    // Mandatory. Amount of RAM available to machine.
                    machineMemoryMB 2048
                    // Mandatory. Hardware ID.
                    //hardwareId "m1.small"
                    // Optional. Location ID.
                    //locationId "us-east-1"
                    username  "vagrant"
                    password  "vagrant"
                    // Mandatory. Files from the local directory will be copied to this directory on the remote machine.
                    remoteDirectory "/tmp/gs-files"
        
                    // Optional. Overrides to default cloud driver behavior.
                    // When used with the default driver, maps to the overrides properties passed to the ComputeServiceContext a
                    options ([:])
                    overrides ([:])



                },
                SMALL_LINUX : template{
                    // Mandatory. Image ID.
                    imageId "precise64"
                    // Mandatory. Amount of RAM available to machine.
                    machineMemoryMB 1024
                    // Mandatory. Hardware ID.
                    //hardwareId "m1.small"
                    // Optional. Location ID.
                    //locationId "us-east-1"
                    username  "vagrant"
                    password  "vagrant"
                    // Mandatory. Files from the local directory will be copied to this directory on the remote machine.
                    remoteDirectory "/tmp/gs-files"
        
                    // Optional. Overrides to default cloud driver behavior.
                    // When used with the default driver, maps to the overrides properties passed to the ComputeServiceContext a
                    options ([:])
                    overrides ([:])



                }
            ])


    /*****************
     * Optional. Custom properties used to extend existing drivers or create new ones.
     */
    custom ([
        "vbox.boxes.path" : "/Users/mathias/.vagrant.d/boxes/", // you can download on http://www.vagrantbox.es/
        "vbox.hostonlyinterface" : "vboxnet2", // this interface must be created manually
        "vbox.serverUrl" : "http://192.168.12.1:18083", // must be the IP of the vboxnet2 interface
        "vbox.headless" : "false", // optional
        "vbox.sharedFolder" : "/Users/mathias/Work/vbox_shared" // Optional, to mount a shared folder between VMs
        ])
}
