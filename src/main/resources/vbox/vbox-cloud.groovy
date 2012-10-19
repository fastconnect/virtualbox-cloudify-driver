
cloud {
    // Mandatory. The name of the cloud, as it will appear in the Cloudify UI.
    name = "vbox"

    /********
     * General configuration information about the cloud driver implementation.
     */
    configuration {
        className "org.cloudifysource.sec.driver.provisioning.virtualbox.VirtualboxCloudifyDriver"
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
        //cloudifyUrl "http://171.68.121.203/gigaspaces-cloudify-2.1.0-rc-b1196.zip"
        //cloudifyOverridesUrl "http://171.68.121.203/gigaspaces-overrides.zip"
        cloudifyUrl "http://192.168.12.1/mathias/gigaspaces-cloudify-2.1.1-ga-b1400.zip"
        cloudifyOverridesUrl "http://192.168.12.1/mathias/gigaspaces_overrides.zip"
        
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
        "vbox.boxes.path" : "/Users/mathias/.vagrant.d/boxes/",
        "vbox.hostonlyinterface" : "vboxnet2",
        "vbox.serverUrl" : "http://192.168.12.1:18083",
        "vbox.headless" : "false", // optional
        "vbox.sharedFolder" : "/Users/mathias/Work/vbox_shared" // Optional
        ])
}


cloud {
	// Mandatory. The name of the cloud, as it will appear in the Cloudify UI.
	name = "vSphere"

	/********
	 * General configuration information about the cloud driver implementation.
	 */
	configuration {
		className "org.cloudifysource.esc.driver.provisioning.virtualbox.VirtualboxCloudifyDriver"
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
		// Mandatory. Files from the local directory will be copied to this directory on the remote machine.
		remoteDirectory "/tmp/gs-files"
		// Mandatory. The HTTP/S URL where cloudify can be downloaded from by newly started machines.
		cloudifyUrl "http://171.68.121.203/gigaspaces-cloudify-2.1.0-rc-b1196.zip"
		cloudifyOverridesUrl "http://171.68.121.203/gigaspaces-overrides.zip"
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
		//user "vagrant"

		// Optional. Key used to access cloud.
		// When used with the default driver, maps to the credential used to create the ComputeServiceContext.
		//apiKey "ENTER_API_KEY"
        //apiKey "vagrant"


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

					// Optional. Overrides to default cloud driver behavior.
					// When used with the default driver, maps to the overrides properties passed to the ComputeServiceContext a
					options ([:])
					overrides ([:])



				},
				SMALL_LINUX : template{
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
		"vbox.boxes.path" : "~/.vagrant.d/boxes/",
        "vbox.hostonlyinterface" : "vboxnet2"
		])
}

