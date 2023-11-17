/*******************************************************************************
 * © 2023 Lionel Cottin - Netscan - Infoblox Grid members from a Grid master
 ******************************************************************************/

// To run in debug mode, set to true
Boolean debug = true
PrintStream out

import com.santaba.agent.AgentVersion
import java.text.DecimalFormat
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Retrieve the collector version
Integer collectorVersion = AgentVersion.AGENT_VERSION.toInteger()
 
// Bail out early if we don't have the correct minimum collector version to ensure netscan runs properly
if (collectorVersion < 32400) {
    def formattedVer = new DecimalFormat("00.000").format(collectorVersion / 1000)
    throw new Exception(" Upgrade collector running netscan to 32.400 or higher to run full featured enhanced script netscan. Currently running version ${formattedVer}.")
}

// Get Netscan parameters
def host    = netscanProps.get("infoblox.host")
def port    = netscanProps.get("infoblox.port")
def user    = netscanProps.get("infoblox.user")
def pass    = netscanProps.get("infoblox.pass")
def group   = netscanProps.get("lm.device.group")

// Class
class Infoblox {
    Boolean debug
    String  host
    String  port
    String  user
    String  pass
    String  url
    String  auth

    private JsonSlurper slurper = new JsonSlurper() // Slurper to reuse for responses
    private PrintStream out

    // Constructor
    Infoblox(out, host, port, user, pass, debug) {
        this.out   = out
        this.debug = debug
        this.host  = host
        this.port  = port
        this.user  = user
        this.pass  = pass
        this.url   = "https://${host}:${port}/wapi/v2.12/"
        this.auth  = "${user}:${pass}".bytes.encodeBase64().toString()
    }

    // Boilerplate to make print/println work
    void println(Object o) { this.out.println o}

    // Helper function for debug messages
    void LMDebug(message) {
        if (this.debug) {
            println(message.toString())
        }
    }

    // Helper GET function
    String httpGet(String command) {
        // Set headers
        def headers = [
            "Content-Type": "application/json",
            "Authorization": "Basic ${this.auth}",
            "cache-control": "no-cache"
        ]
        // Send request
        try {
            def conn = "${this.url}${command}".toURL().openConnection()
            conn.setRequestMethod("GET")
            conn.setDoOutput(true)
            headers.each { k,v ->
                conn.setRequestProperty(k,v)
            }
            if (conn.responseCode == 200) {
                def responseBody = conn.getInputStream().getText()
                LMDebug("\nINFOBLOX: Get ${command} OK\n")
                return responseBody
            }
        }
        // Catch errors
        catch (Exception e) {
            LMDebug("\nINFOBLOX: Exception occurred:\t${e}")
        }
        LMDebug("\nINFOBLOX: Could not fetch data from ${this.url}${command}\n")
        return null
    }

    // Get devices
    def getMembers() {
        def response = httpGet('member')
        return (response) ? slurper.parseText(response) : null
    }
}


List<Map> resources = []

// Fire request
session = new Infoblox(out, host, port, user, pass, debug)
def members = session.getMembers()

// Process result
if (members) {
    members.each { member ->
        // build resource
        Map resource = [
            "hostname"    : member.'host_name'.toLowerCase(),
            "displayname" : member.'host_name'.split("\\.")[0].toLowerCase(),
            "hostProps"   : [
                "infoblox.platform": member.'platform',
                "sys.categories": "Infoblox"
            ],
            "groupName"   : [ group ]
        ]
        // add to resource list
        resources.add(resource)
    }
    // Output to LM
    def json=JsonOutput.toJson(resources)
    println json

// Exit with error
} else {
    println "ERROR"
    return 1
}