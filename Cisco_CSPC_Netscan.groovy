/*******************************************************************************
 * © 2023 Lionel Cottin - Netscan - Cisco devices from a Cisco CSPC server
 ******************************************************************************/

// To run in debug mode, set to true
Boolean debug = false

import com.santaba.agent.AgentVersion
import java.text.DecimalFormat
import groovy.json.JsonOutput

// Retrieve the collector version
Integer collectorVersion = AgentVersion.AGENT_VERSION.toInteger()
 
// Bail out early if we don't have the correct minimum collector version to ensure netscan runs properly
if (collectorVersion < 32400) {
    def formattedVer = new DecimalFormat("00.000").format(collectorVersion / 1000)
    throw new Exception(" Upgrade collector running netscan to 32.400 or higher to run full featured enhanced script netscan. Currently running version ${formattedVer}.")
}

// Get Netscan parameters
def host   = netscanProps.get("cspc.host")
def port   = netscanProps.get("cspc.port")
def user   = netscanProps.get("cspc.user")
def pass   = netscanProps.get("cspc.pass")
def search = netscanProps.get("cspc.search")  // search scope: Reachable or Unreachable
def filter = netscanProps.get("cspc.filter")  // search filter: Routers||Switches||Servers-UnifiedComputing||DataCenterSwitches||...
def group  = netscanProps.get("lm.device.group") // target group in LogicMonitor

// Class
class CiscoCSPC {
    Boolean debug = false
    Boolean connected = false
    String  host
    String  user
    String  pass
    String  port
    String  path
    String  auth

    // Constructor
    CiscoCSPC(host, port, user, pass) {
        this.host    = host
        this.user    = user
        this.pass    = pass
        this.port    = port
        this.auth    = "${user}:${pass}".bytes.encodeBase64().toString()
        this.path = "https://${this.host}:${this.port}/cspc/xml"
    }

    // Helper POST function
    String post(String payload) {
        def conn = "${this.path}".toURL().openConnection()
        conn.setRequestMethod("POST")
        conn.addRequestProperty("Content-Type", 'application/xml; charset=utf-8')
        conn.addRequestProperty("Authorization", "Basic ${this.auth}")
        conn.addRequestProperty("cache-control", "no-cache")
        // begin POST
        conn.setDoOutput(true)
        OutputStream os = new BufferedOutputStream(conn.getOutputStream())
        os.write(payload.getBytes())
        os.flush()
        os.close()
        //end POST
        if (conn.responseCode == 200) {
            return conn.content.text
        } else {
            return null
        }
    }

    // Get devices
    def getDeviceList() {
        def payload = '''<Request xmlns="http://www.parinetworks.com/api/schemas/1.1" requestId="">
  <Manage>
    <Get operationId="1">
      <DeviceList all="true" />
    </Get>
  </Manage>
</Request>'''
        return post(payload)
    }
}

// Fire request
session = new CiscoCSPC(host, port, user, pass)
def response = session.getDeviceList()

// Process result
if (response) {
    List<Map> resources = []

    // Parse XML
    def xml = new XmlParser().parseText(response)
    xml.Manage.Get.DeviceList.Device.each { Device ->
        Boolean toAdd = false
        Map hostProps = [:]

        // Map XML data
        def device  = [
            "hostname":  Device.HostName.text().toLowerCase(),
            "ipaddress": Device.IPAddress.text(),
            "status":    Device.Status.text(),
            "family":    Device.DeviceFamily.text(),
            "model":     Device.Model.text()
        ]

        // Reachable device
        if ( search == "Reachable" && device.status == "Reachable" && device.family == filter && device.hostname) {
            toAdd = true
            // Resource properties
            hostProps = [
                "device.name"       : device.hostname,
                "device.model"      : device.model,
                "device.lan_ip"     : device.ipaddress,
                "device.family"     : device.family,
                "system.categories" : "Cisco_CSPC_Reachable",
            ]
        }

        // Unreachable device
        if ( search == "Unreachable" && device.status == "Unreachable") {
            toAdd = true
            // Resource properties
            hostProps = [
                "device.name"       : device.ipaddress,
                "device.lan_ip"     : device.ipaddress,
                "system.categories" : "Cisco_CSPC_Unreachable",
            ]
        }

        // Create resource
        if (toAdd) {
            Map resource = [
                "hostname"    : device.ipaddress,
                "displayname" : device.hostname,
                "hostProps"   : hostProps,
                "groupName"   : [group],
                //"collectorId" : 2
            ]
            resources.add(resource)
        }
    }
    def json=JsonOutput.toJson(resources)
    println json
} else {
    println "ERROR"
    return 1
}