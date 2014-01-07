Prerequisite
------------

* Have [Cloudify >= 2.7](http://www.cloudifysource.org/downloads/get_cloudify) installed.
* Have [VirtualBox](https://www.virtualbox.org/) installed. On you local machine or on a server. Tested with 4.2.18.
* Create a ["HostOnlyInterface"](https://www.virtualbox.org/manual/ch06.html#network_hostonly) with DHCP activated.
	*  Ex: IP 27.0.0.1 NetMask 255.255.255.0
	*  DHCP : IP 27.0.0.10 NetMask 255.255.255.0 Limits 27.0.0.11 -> 27.0.0.255 
* Start the [VirtualBox WebService](http://download.virtualbox.org/virtualbox/SDKRef.pdf) on the IP of the "HostOnlyInterface" you want to use. Ex:
```
	$ VBoxManage setproperty websrvauthlibrary null
	$ vboxwebsrv --host 27.0.0.1
	Oracle VM VirtualBox web service version 4.2.18
	(C) 2007-2013 Oracle Corporation
	All rights reserved.
	VirtualBox web service 4.2.18 r88780 win.amd64 (Sep  6 2013 14:21:01) release log
	00:00:00.000 main     Log opened 2012-10-17T06:23:02.644109000Z
	...
```

* Download some "boxes" from Vagrant: http://www.vagrantbox.es/. You can package your own boxes with Veewee: https://github.com/jedi4ever/veewee. They should have:
   * An account with sudo rights without password
   * VirtualBox Guest Additions installed
* Only LINUX boxes are supported for now

Configuration
-------------

```groovy
custom ([
    "vbox.boxes.path" : "/Users/mathias/.vagrant.d/boxes/", // you can download on http://www.vagrantbox.es/
    "vbox.hostonlyinterface" : "vboxnet2", // this interface must be created manually
    "vbox.serverUrl" : "http://27.0.0.1:18083", // must be the IP of the vboxnet2 interface
    "vbox.headless" : "false", // optional
    "vbox.sharedFolder" : "/Users/mathias/Work/vbox_shared" // Optional, to mount a shared folder between VMs
])
```

You can find a full example of the configuration in src/test/resources (https://github.com/fastconnect/virtualbox-cloudify-driver/blob/master/src/main/resources/vbox/vbox-cloud.groovy)

Download and install
--------------------
You can download the driver from our Nexus, and package it in a gigaspaces_overrides.zip (you can use maven:assembly to do that, but you should exclude cloudify dependencies for a lighter archive)
The use of gigaspaces_overrides is explained here: http://www.cloudifysource.org/guide/2.6/clouddrivers/tutorial_maven (Packing and Adding to Cloudify)

Here is the POM configuration to include the driver:
```xml
<repositories>
	<repository>
		<id>repo.opensource.fastconnect.org</id>
		<url>http://fastconnect.org/maven/content/repositories/opensource</url>
	</repository>
</repositories>

<dependencies>
	<dependency>
		<groupId>fr.fastconnect</groupId>
		<artifactId>virtualbox-cloudify-driver</artifactId>
		<version>1.16</version>
		<exclusions>
			<exclusion>
				<artifactId>esc</artifactId>
				<groupId>org.cloudifysource</groupId>
			</exclusion>
			<exclusion>
				<artifactId>dsl</artifactId>
				<groupId>org.cloudifysource</groupId>
			</exclusion>
		</exclusions>
	</dependency>
</dependencies>
```

Here is the full URL: https://fastconnect.org/maven/content/repositories/opensource/fr/fastconnect/virtualbox-cloudify-driver/1.16/virtualbox-cloudify-driver-1.16.jar

Download the additional jars:
* http://search.maven.org/remotecontent?filepath=org/virtualbox/vboxjws/4.2.8/vboxjws-4.2.8.jar
* http://search.maven.org/remotecontent?filepath=commons-codec/commons-codec/20041127.091804/commons-codec-20041127.091804.jar

FAQ
---
1. Not able to bootstrap: **Unable to connect to http://27.0.0.1:18083 with login**

Make sure that the VBox WebService is correctly running.
You may have failed to start the VBox WebService if you have the following output:
```
...
00:00:00.016973 SQPmp    #### SOAP FAULT: Can't assign requested address [SOAP-ENV:Server]
```
In this case, the WebService is not able to bind to the HostOnlyInterface IP address (27.0.0.1).
You can verify that with the **VBoxManage** command:
```
$ VBoxManage list hostonlyifs
Name:            vboxnet0
GUID:            786f6276-656e-4174-8000-0a0027000001
DHCP:            Disabled
IPAddress:       27.0.0.1
NetworkMask:     255.255.255.0
IPV6Address:
IPV6NetworkMaskPrefixLength: 0
HardwareAddress: 0a:00:27:00:00:01
MediumType:      Ethernet
Status:          Down
VBoxNetworkName: HostInterfaceNetworking-vboxnet0
```
The status here is **Down**.
To activate the HostOnlyInterface on Linux/MacOS, you can use the **ifconfig** command:
```
$ VBoxManage hostonlyif ipconfig vboxnet0 --ip 27.0.0.1 --netmask 255.255.255.0
$ sudo ifconfig vboxnet0 up
```

You can do it graphically:
- Go to the **Preferences** menu of VirtualBox
- Go to the **Network** tab
- Edit the HostOnlyInterface (double-click)
- Click OK
- The HostOnlyInterface should be up

Then you can start again the WebService, and the Cloudify Client to bootstrap again.



Copyright and license
----------------------
Copyright (c) 2012 FastConnect SAS All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");<br/>
you may not use this file except in compliance with the License.<br/>
You may obtain a copy of the License at 

       http://www.apache.org/licenses/LICENSE-2.0
	   
Unless required by applicable law or agreed to in writing, software<br/>
distributed under the License is distributed on an "AS IS" BASIS,<br/>
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br/>
See the License for the specific language governing permissions and<br/>
limitations under the License.
