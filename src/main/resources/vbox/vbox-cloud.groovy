
cloud {
    // Mandatory. The name of the cloud, as it will appear in the Cloudify UI.
    name = "vbox"

    /********
     * General configuration information about the cloud driver implementation.
     */
    configuration {
        className "fr.fastconnect.cloudify.driver.provisioning.virtualbox.VirtualboxCloudifyDriver"
        storageClassName "fr.fastconnect.cloudify.driver.provisioning.virtualbox.VirtualBoxStorageDriver"
        // Optional. The template name for the management machines. Defaults to the first template in the templates section below.
        managementMachineTemplate "MEDIUM_LINUX"
        //managementStorageTemplate "SMALL_BLOCK"
        // Optional. Indicates whether internal cluster communications should use the machine private IP. Defaults to true.
        connectToPrivateIp false
        persistentStoragePath null
    }

    /*************
     * Provider specific information.
     */
    provider {
        // Mandatory. The name of the provider.
        // When using the default cloud driver, maps to the Compute Service Context provider name.
        provider "virtualbox"

        // Mandatory. The HTTP/S URL where cloudify can be downloaded from by newly started machines.
        //cloudifyUrl "gigaspaces-cloudify-2.5.0-ga-b4010.zip"
        //cloudifyOverridesUrl "gigaspaces_overrides.zip"
        
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

        reservedMemoryCapacityPerMachineInMB 512

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

    cloudStorage {
        templates ([
                
            SMALL_BLOCK : storageTemplate{
                deleteOnExit true
                size 1
                path "/home/mathias/vagrant-volumes/"
                namePrefix "cloudify-storage-volume"
                deviceName "/dev/sdb"
                fileSystemType "ext4"
                custom ([:])
            }
        ])
    }

    /***********
     * Cloud machine templates available with this cloud. 
     */
    cloudCompute {
        templates ([
            SMALL_LINUX : computeTemplate{
                imageId "precise64"
                machineMemoryMB 1024
                username  "vagrant"
                password  "vagrant"
                remoteDirectory "/home/vagrant/gs-files"
                localDirectory "upload"
                // enable sudo.
                privileged true 
                options ([:])
                overrides ([:])
            }
        ])
    }

    /*****************
     * Optional. Custom properties used to extend existing drivers or create new ones. 
     */
    custom ([ 
        "vbox.boxes.path" : BOXES_PATH,
        "vbox.hostonlyinterface" : HOST_ONLY_INTERFACE,
        "vbox.serverUrl" : SERVER_URL,
        "vbox.headless" : "true", // optional
        "vbox.sharedFolder" : SHARED_FOLDER // Optional
    ])
}

