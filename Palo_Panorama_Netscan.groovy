/*******************************************************************************
 * © 2023 Lionel Cottin - Netscan - Palo Firewalls from a Panorama server
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
def host   = netscanProps.get("panorama.host")
def port   = netscanProps.get("panorama.port")
def key    = netscanProps.get("panorama.key")
def group  = netscanProps.get("lm.device.group")

// Class
class Panorama {
    Boolean debug = false
    String  host
    String  port
    String  key
    String  path

    // Constructor
    Panorama(host, port, key) {
        this.host = host
        this.port = port
        this.key  = key
        this.path = "https://${host}:${port}/api/?type=op&key=${key}&cmd="
    }

    // Helper GET function
    String get(String command) {
        def conn = "${this.path}${command}".toURL().openConnection()
        conn.setRequestMethod("GET")
        conn.addRequestProperty("Content-Type", 'application/xml; charset=utf-8')
        conn.addRequestProperty("cache-control", "no-cache")
        if (conn.responseCode == 200) {
            return conn.content.text
        } else {
            return null
        }
    }

    // Get devices
    def showDevicesConnected() {
        return get("<show><devices><connected></connected></devices></show>")
    }
}

// Fire request
session = new Panorama(host, port, key)
def response = session.showDevicesConnected()

// Process result
if (response) {
    List<Map> resources = []

    // Parse XML
    def xml = new XmlParser().parseText(response)
    xml.result.devices.entry.each { entry ->

        // Map XML data
        def device  = [
            "displayname": entry.'hostname'.text().toLowerCase(),
            "hostname":    entry.'ip-address'.text(),
            "groupName":   [group],
            "hostProps": [
                // Property source addERI_PaloAlto 
                "system.categories" : "PaloAlto",
                // custom info
                "palo.hostname":        entry.'hostname'.text().toLowerCase(),
                "palo.model":           entry.'model'.text(),
                "palo.lan_ip":          entry.'ip-address'.text(),
                "palo.family":          entry.'family'.text(),
                "palo.serial":          entry.'serial'.text(),
                "palo.swVersion":       entry.'sw-version'.text(),
                "palo.appVersion":      entry.'app-version'.text(),
                "palo.avVersion":       entry.'av-version'.text(),
                "palo.wildfireVersion": entry.'wildfire-version'.text(),
                "palo.logdbVersion":    entry.'logdb-version'.text(),
                "palo.gpVersion":       entry.'global-protect-client-package-version'.text(),
                "palo.threatVersion":   entry.'threat-version'.text(),
                // custom datapoints
                "palo.deviceStatus":    entry.'operational-mode'.text(),
                "palo.certStatus":      entry.'device-cert-present'.text(),
                "palo.certExpiration":  entry.'device-cert-expiry-date'.text(),
            ],

        ]
        // Add resource
        resources.add(device)
    }

    // Output to LM
    def json=JsonOutput.toJson(resources)
    println json

// Exit with error
} else {
    println "ERROR"
    return 1
}