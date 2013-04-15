Prerequisite
------------

* Have [Cloudify >= 2.5](http://www.gigaspaces.com/cloudify-open-paas-stack) installed.
* Have [VirtualBox](https://www.virtualbox.org/) installed. On you local machine or on a server. Tested with 4.2.4.
* Create a ["HostOnlyInterface"](https://www.virtualbox.org/manual/ch06.html#network_hostonly) (don't need a DHCP). Ex: IP 27.0.0.1 NetMask 255.255.255.0
* Start the [VirtualBox WebService](http://download.virtualbox.org/virtualbox/SDKRef.pdf) on the IP of the "HostOnlyInterface" you want to use. Ex:
```
	$ VBoxManage setproperty websrvauthlibrary null
	$ vboxwebsrv --host 27.0.0.1
	Oracle VM VirtualBox web service version 4.1.23
	(C) 2005-2012 Oracle Corporation
	All rights reserved.
	VirtualBox web service 4.1.23 r80870 darwin.amd64 (Sep 21 2012 12:31:46) release log
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
The use of gigaspaces_overrides is explained here: http://www.cloudifysource.org/guide/2.3/clouddrivers/tutorial_maven (Packing and Adding to Cloudify)

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
		<version>1.8</version>
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

Here is the full URL: https://fastconnect.org/maven/content/repositories/opensource/fr/fastconnect/virtualbox-cloudify-driver/1.8/virtualbox-cloudify-driver-1.8.jar


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
