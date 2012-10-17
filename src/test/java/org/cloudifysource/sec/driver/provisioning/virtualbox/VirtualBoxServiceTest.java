package org.cloudifysource.sec.driver.provisioning.virtualbox;

import java.io.IOException;

import org.cloudifysource.sec.driver.provisioning.virtualbox.api.VirtualBoxMachineInfo;
import org.cloudifysource.sec.driver.provisioning.virtualbox.api.VirtualBoxService;
import org.junit.Test;

public class VirtualBoxServiceTest {

    @Test
    public void testGetAll() throws IOException{
        VirtualBoxService service = new VirtualBoxService();
        service.connect("http://localhost:18083","","");
        
        for(VirtualBoxMachineInfo m : service.getAll()){
            System.out.println(m.getGuid()+" "+m.getMachineName());
        }
    }
}
