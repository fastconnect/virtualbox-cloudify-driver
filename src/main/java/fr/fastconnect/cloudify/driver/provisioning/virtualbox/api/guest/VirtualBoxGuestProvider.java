package fr.fastconnect.cloudify.driver.provisioning.virtualbox.api.guest;

import java.util.HashMap;

import org.virtualbox_4_2.VirtualBoxManager;

public class VirtualBoxGuestProvider {

    private final HashMap<String, VirtualBoxGuest> registeredGuests = new HashMap<String, VirtualBoxGuest>();

    public VirtualBoxGuestProvider(VirtualBoxManager virtualBoxManager) {
        /*
         * ID: Other Family: Other
         * ID: Windows31 Family: Windows
         * ID: Windows95 Family: Windows
         * ID: Windows98 Family: Windows
         * ID: WindowsMe Family: Windows
         * ID: WindowsNT4 Family: Windows
         * ID: Windows2000 Family: Windows
         * ID: WindowsXP Family: Windows
         * ID: WindowsXP_64 Family: Windows
         * ID: Windows2003 Family: Windows
         * ID: Windows2003_64 Family: Windows
         * ID: WindowsVista Family: Windows
         * ID: WindowsVista_64 Family: Windows
         * ID: Windows2008 Family: Windows
         * ID: Windows2008_64 Family: Windows
         * ID: Windows7 Family: Windows
         * ID: Windows7_64 Family: Windows
         * ID: Windows8 Family: Windows
         * ID: Windows8_64 Family: Windows
         * ID: Windows2012_64 Family: Windows
         * ID: WindowsNT Family: Windows
         * 
         * ID: Linux22 Family: Linux
         * ID: Linux24 Family: Linux
         * ID: Linux24_64 Family: Linux
         * ID: Linux26 Family: Linux
         * ID: Linux26_64 Family: Linux
         * ID: ArchLinux Family: Linux
         * ID: ArchLinux_64 Family: Linux
         * ID: Debian Family: Linux
         * ID: Debian_64 Family: Linux
         * ID: OpenSUSE Family: Linux
         * ID: OpenSUSE_64 Family: Linux
         * ID: Fedora Family: Linux
         * ID: Fedora_64 Family: Linux
         * ID: Gentoo Family: Linux
         * ID: Gentoo_64 Family: Linux
         * ID: Mandriva Family: Linux
         * ID: Mandriva_64 Family: Linux
         * ID: RedHat Family: Linux
         * ID: RedHat_64 Family: Linux
         * ID: Turbolinux Family: Linux
         * ID: Turbolinux_64 Family: Linux
         * ID: Ubuntu Family: Linux
         * ID: Ubuntu_64 Family: Linux
         * ID: Xandros Family: Linux
         * ID: Xandros_64 Family: Linux
         * ID: Oracle Family: Linux
         * ID: Oracle_64 Family: Linux
         * ID: Linux Family: Linux
         * 
         * ID: Solaris Family: Solaris
         * ID: Solaris_64 Family: Solaris
         * ID: OpenSolaris Family: Solaris
         * ID: OpenSolaris_64 Family: Solaris
         * ID: Solaris11_64 Family: Solaris
         * 
         * ID: FreeBSD Family: BSD
         * ID: FreeBSD_64 Family: BSD
         * ID: OpenBSD Family: BSD
         * ID: OpenBSD_64 Family: BSD
         * ID: NetBSD Family: BSD
         * ID: NetBSD_64 Family: BSD
         * ID: OS2Warp3 Family: OS2
         * ID: OS2Warp4 Family: OS2
         * ID: OS2Warp45 Family: OS2
         * ID: OS2eCS Family: OS2
         * ID: OS2 Family: OS2
         * 
         * ID: MacOS Family: MacOS
         * ID: MacOS_64 Family: MacOS
         * 
         * ID: DOS Family: Other
         * ID: Netware Family: Other
         * ID: L4 Family: Other
         * ID: QNX Family: Other
         * ID: JRockitVE Family: Other
         */

        UbuntuGuest ubuntuGuest = new UbuntuGuest(virtualBoxManager);
        registeredGuests.put("Ubuntu", ubuntuGuest);
        registeredGuests.put("Ubuntu_64", ubuntuGuest);

        RedhatGuest redhatGuest = new RedhatGuest(virtualBoxManager);
        registeredGuests.put("RedHat", redhatGuest);
        registeredGuests.put("RedHat_64", redhatGuest);

        WindowsGuest windowsGuest = new WindowsGuest(virtualBoxManager);
        registeredGuests.put("Windows2008", windowsGuest);
        registeredGuests.put("Windows2008_64", windowsGuest);
    }

    public VirtualBoxGuest getVirtualBoxGuest(String osId) {
        return registeredGuests.get(osId);
    }
}
