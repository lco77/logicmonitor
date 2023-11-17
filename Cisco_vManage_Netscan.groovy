/*******************************************************************************
 * © 2023 Lionel Cottin - Netscan - Cisco devices from a vManage server
 ******************************************************************************/

// To run in debug mode, set to true
Boolean debug = false

import com.logicmonitor.common.sse.utils.GroovyScriptHelper as GSH
import com.logicmonitor.mod.Snippets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.santaba.agent.AgentVersion
import java.text.DecimalFormat

// Retrieve the collector version
Integer collectorVersion = AgentVersion.AGENT_VERSION.toInteger()
 
// Bail out early if we don't have the correct minimum collector version to ensure netscan runs properly
if (collectorVersion < 32400) {
    def formattedVer = new DecimalFormat("00.000").format(collectorVersion / 1000)
    throw new Exception(" Upgrade collector running netscan to 32.400 or higher to run full featured enhanced script netscan. Currently running version ${formattedVer}.")
}

// Get Netscan parameters
def host         = netscanProps.get("vmanage.host")            // vManage host
def port         = netscanProps.get("vmanage.port")?:"8443"    // vManage port
def user         = netscanProps.get("vmanage.user")            // vManage user
def pass         = netscanProps.get("vmanage.pass")            // vManage password
def vedgeGroup   = netscanProps.get("cisco-sdwan-group.vedge") // Target group for cEdge devices (isr-4k / cat8k / ...)
def nfvisGroup   = netscanProps.get("cisco-sdwan-group.nfvis") // Target group for NFVIS devices (encs-5k / c8k-ucpe)
def vsmartGroup  = netscanProps.get("cisco-sdwan-group.vsmart")
def vbondGroup   = netscanProps.get("cisco-sdwan-group.vbond")
def vmanageGroup = netscanProps.get("cisco-sdwan-group.vmanage")
def domain       = netscanProps.get("dns.domain")

PrintStream out

// Class with data and methods for vManage
class vManage {
    Boolean debug = false
    Boolean connected = false
    String  host            // Device hostname
    String  user            // vManage user
    String  pass            // vManage pass
    String  port            // Connection port; defaults to 8443
    String  baseUrl         // Constructed URL for API calls
    String  sessionId       // Session id to authenticate calls
    String  csrfToken       // Cross-site request forgery token

    private JsonSlurper slurper = new JsonSlurper() // Slurper to reuse for responses
    private PrintStream out

    // Constructor
    vManage(out, host, port, user, pass, debug) {
        this.out     = out             // Needed for print/println
        this.debug   = debug
        this.host    = host
        this.user    = user
        this.pass    = pass
        this.port    = port
        this.baseUrl = "https://${this.host}:${this.port}"
        if (login()) {
            this.connected = true
        }
    }

    // Boilerplate to make print/println work
    void println(Object o) { this.out.println o}

    // Helper function for debug messages
    void LMDebug(message) {
        if (this.debug) {
            println(message.toString())
        }
    }

    // Login
    Boolean login() {
        def loginUrl = new URL("${this.baseUrl}/j_security_check")
        def loginPost
        LMDebug("\nCISCO-SDWAN: Logging ${this.user} on ${loginUrl}...\n")
        loginPost = loginUrl.openConnection()
        loginPost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        loginPost.with {
            doOutput= true
            requestMethod = "POST"
        }
        // Pass creds
        def encodePass = URLEncoder.encode(this.pass, "UTF-8")
        def payload = 'j_username=' + this.user + '&j_password=' + encodePass
        loginPost.getOutputStream().write(payload.getBytes("UTF-8"))
        // Store response code and body
        def postResponseCode = loginPost.getResponseCode()
        def postResponseBody = loginPost.getInputStream().getText()
        // Make sure 200 RC is not accompanied by html which is an indication of false success response
        if (postResponseCode == 200 && !postResponseBody.contains("html")) {
            LMDebug("Auth OK\n")
            this.sessionId = loginPost.getHeaderField("Set-Cookie").tokenize(";")[0]
            LMDebug("\nCISCO-SDWAN: Sessionid ${this.sessionId}\n")
            def token = httpGet("dataservice/client/token")
            if (token) {
                LMDebug("\nCISCO-SDWAN:  csrfToken ${token}\n")
                this.csrfToken = token
                return true
            } else {
                LMDebug("\nCISCO-SDWAN: failed to get csrfToken\n")
                return false
            }
        } else {
            LMDebug("Auth Failed\n")
            return false
        }
    }

    // Get devices
    def getDevice() {
        def endpoint = "dataservice/device"
        def deviceResponse = httpGet(endpoint)
        return (deviceResponse) ? slurper.parseText(deviceResponse) : null
    }

    // Generic GET
    String httpGet(String endpoint) {
        LMDebug("\nCISCO-SDWAN: httpGet sessionID=${this.sessionId}\n")
        LMDebug("\nCISCO-SDWAN: httpGet token=${this.csrfToken}\n")
        LMDebug("\nCISCO-SDWAN: httpGet /${endpoint} ...\n")
        // Set headers based on whether we can use CSRF token
        def headers = [
            "Content-Type": "application/json",
            "Cookie" : this.sessionId
        ]
        if (this.csrfToken) {
            headers.put("X-XSRF-TOKEN", this.csrfToken)
        }
        def responseBody
        def responseCode
        def exception
        try {
            def url = "${this.baseUrl}/${endpoint}".toURL()
            def conn = url.openConnection()
            conn.setRequestMethod("GET")
            conn.setDoOutput(true)
            headers.each { k,v ->
                conn.setRequestProperty(k,v)
            }
            responseCode = conn.getResponseCode()
            LMDebug("\nCISCO-SDWAN: httpGet ${endpoint} ${responseCode}\n")
            responseBody = conn.getInputStream().getText()
            if (responseCode == 200 && !responseBody.contains("html")) {
                return responseBody
            }
        }
        catch (Exception e) {
            exception = e
            LMDebug("\nCISCO-SDWAN: Exception occurred:\t${e}")
        }
        LMDebug("\nCISCO-SDWAN: Could not fetch data from /${endpoint}\n")
        return null
    }
}

// Prepare resources
List<Map> resources = []
 
// Connect to vManage
session = new vManage(out, host, port, user, pass, debug)
if (!session.connected) {
    println("\nCISCO-SDWAN: aborting Netscan\n")
    return 1
}
// Get devices
def devices = session.getDevice()
if (devices) {
    // Loop through devices
    devices.'data'.each { it ->
        // Assign group membership based on device type/model
        List<String> targetGroup = []
        String kind = null
        if (it.'device-type' =~ /^vbond$/) {
            targetGroup.add(vbondGroup)
            kind = "controller"
        } else if (it.'device-type' =~ /^vsmart$/) {
            targetGroup.add(vsmartGroup)
            kind = "controller"
        } else if (it.'device-type' =~ /^vmanage$/) {
            targetGroup.add(vmanageGroup)
            kind = "controller"
        } else if (it.'device-model' =~ /^vedge-nfvis-.*$/) {
            targetGroup.add(nfvisGroup)
            // add "-nfvis" extension to hostname
            it.'host-name' = "${it.'host-name'}-nfvis"
            kind = "vedge"
        } else if (it.'device-model' =~ /^vedge-.*$/) {
            targetGroup.add(vedgeGroup)
            kind = "vedge"
        }
        Map hostProps = [
            "cisco.vmanage.device.host-name"            : it.'host-name',
            "cisco.vmanage.device.type"                 : it.'device-type',
            "cisco.vmanage.device.os"                   : it.'device-os',
            "cisco.vmanage.device.model"                : it.'device-model',
            "cisco.vmanage.device.platform"             : it.'platform',
            "cisco.vmanage.device.timezone"             : it.'timezone',
            "cisco.vmanage.device.version"              : it.'version',
            "cisco.vmanage.device.board-serial"         : it.'board-serial',
            "cisco.vmanage.device.device-model"         : it.'device-model',
            "cisco.vmanage.device.site-id"              : it.'site-id',
            // Set system ip as ilp for use in script collection API call
            "cisco.vmanage.device.system-ip"            : it.'system-ip',
            // Set datapoints as ilps in order to avoid other collection api calls
            "cisco.vmanage.device.status"               : it.'status',
            "cisco.vmanage.device.state"                : it.'state',
            "cisco.vmanage.device.reachability"         : it.'reachability',
            "cisco.vmanage.device.total_cpu_count"      : it.'total_cpu_count',
            "cisco.vmanage.device.certificate_validity" : it.'certificate-validity',            
        ]
        Map resource = [
            "hostname"    : "${it.'host-name'}.${domain}".toLowerCase(),
            "displayname" : it.'host-name'.toLowerCase(),
            "hostProps"   : hostProps,
            "groupName"   : targetGroup,    // List<String>
        ]
        if (kind == "vedge") {
          resources.add(resource)
        }
    }
    def json=JsonOutput.toJson(resources)
    println json
} else {
    println("\nCISCO-SDWAN: No devices found\n")
}
