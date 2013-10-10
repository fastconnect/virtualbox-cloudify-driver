<#
.SYNOPSIS
	Configure network interface.
.DESCRIPTION
	Configure network given a mac address to set static IP or use dhcp.
.PARAMETER macAddress
	The madAddress of the network interface to configure
.PARAMETER dhcp 
	Use DHCP or static configuration
.PARAMETER ip
	Set the IP when configure with static address
.PARAMETER mask
	Set the mask when configure with static address
.PARAMETER gateway
	Set the gateway when configure with static address
.EXAMPLE
	ConfigureNetworkInterface 0 $false 25.0.0.3 255.255.255.0 
#>
Param(
  [string]$macAddress,
  [string]$dhcp="true",
  [string]$ip,
  [string]$mask,
  [string]$gateway
)

$scriptFolder=Split-Path -Path $MyInvocation.MyCommand.Definition -Parent

echo macAddress=$macAddress
$network=(Get-WmiObject win32_networkadapter | where {$_.MACAddress -match $macAddress} | select NetConnectionId | ft -HideTableHeaders | out-string).trim()

echo network=$network
if ($dhcp -eq "true") {
	write-host "Configuring '$network' with dhcp"
	netsh interface ip set address "$network" dhcp | Out-File "$scriptFolder\$network.log"
} else {
	if(!$ip -or !$mask) {
		write-host "Error configuring '$network': missing parameter (ip=$ip mask=$mask gateway=$gateway)"
		exit 666
	} else {
		write-host "Configuring '$network' with static ip=$ip"
		netsh interface ip set address "$network" static $ip $mask $gateway | Out-File "$scriptFolder\$network.log"
	}
}

Start-Sleep -s 5