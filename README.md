# Netscans

## Cisco_CSPC_Netscan.groovy

Discover Cisco devices from a Cisco CSPC Collector and add them to the Logic Monitor resource inventory.

You must configure the following custom variables on the Netscan:
- `cspc.host` -> Cisco CSPC Hostname
- `cspc.port` -> Cisco CSPC Port (typically 8001)
- `cspc.user` -> Cisco CSPC Username
- `cspc.pass` -> Cisco CSPC Password
- `cspc.search` -> Search scope: choose from `Reachable` or `Unreachable`
- `cspc.filter` -> Search filter: `Routers` or `Switches` or `Servers-UnifiedComputing` or `DataCenterSwitches`
- `lm.device.group` ->  Target resource group in LogicMonitor (for instance: `Network/Cisco-Devices`)
    > Don't forget to add custom properties on the target group to enable ActiveDiscovery


## Cisco_vManage_Netscan.groovy

Discover Cisco vEdges devices from a Cisco vManage server and add them to the Logic Monitor resource inventory.

You must configure the following custom variables on the Netscan:

- `vmanage.host` -> vManage hostname
- `vmanage.port` -> vManage port
- `vmanage.user` -> vManage username
- `vmanage.pass` -> vManage password
- `cisco-sdwan-group.vedge` -> Target group for vEdge devices (isr-4k / cat8k / ...)
- `cisco-sdwan-group.nfvis` -> Target group for NFVIS devices (encs-5k / c8k-ucpe)
- `cisco-sdwan-group.vsmart` -> Target group for vSmart devices
- `cisco-sdwan-group.vbond` -> Target group for vBond devices
- `cisco-sdwan-group.vmanage` -> Target group for vManage devices
- `dns.domain` -> DNS Domain to append to discovered hostnames


## Palo_Panorama_Netscan.groovy

Discover Firewalls from a Panorama server and add them to the Logic Monitor resource inventory.

You must configure the following custom variables on the Netscan:

- `panorama.host` -> Panorama hostname
- `panorama.port` -> Panorama port
- `panorama.key` -> Panorama API Key
- `lm.device.group` -> Target group for Firewall devices

## Infoblox_Grid_Members_Netscan.groovy

Discover Grid members from a Grid master and add them to the Logic Monitor resource inventory.

You must configure the following custom variables on the Netscan:

- `infoblox.host` -> Grid master hostname
- `infoblox.port` -> Grid master port
- `infoblox.user` -> Grid master username
- `infoblox.pass` -> Grid master password
- `lm.device.group` -> Target group for Firewall devices

# Data Sources

## Cisco_CSPC_Reachable_ActiveDiscovery.groovy

Discover `reachable` devices from the Cisco CSPC inventory and add them as resource instances

You must configure the following custom variables on the parent CSPC collector resource:
- `cspc.port` -> Cisco CSPC collector port
- `cspc.user` -> Cisco CSPC collector username
- `cspc.pass` -> Cisco CSPC collector password

## Cisco_CSPC_Unreachable_ActiveDiscovery.groovy

Discover `unreachable` devices from the Cisco CSPC inventory and add them as resource instances

You must configure the following custom variables on the parent CSPC collector resource:
- `cspc.port` -> Cisco CSPC collector port
- `cspc.user` -> Cisco CSPC collector username
- `cspc.pass` -> Cisco CSPC collector password

