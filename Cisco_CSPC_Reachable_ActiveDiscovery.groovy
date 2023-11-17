/*******************************************************************************
 * © 2023 Lionel Cottin - Active Discovery - Cisco CSPC Reachable devices
 ******************************************************************************/
import com.logicmonitor.common.sse.utils.GroovyScriptHelper as GSH
import com.logicmonitor.mod.Snippets

modLoader = GSH.getInstance()._getScript("Snippets", Snippets.getLoader()).withBinding(getBinding())
emit = modLoader.load("lm.emit", "0")
msg = modLoader.load("lm.debug", "0", true)
msg.default_context = "Cisco_CSPC:ad"

def host     = hostProps.get("system.hostname")
def port     = hostProps.get("cspc.port")?.trim()
def username = hostProps.get("cspc.user")?.trim()
def password = hostProps.get("cspc.pass")?.trim()
if (port == null) {
    println "No cspc.port set"
    return 1
}
if (username == null) {
    println "No cspc.user set"
    return 1
}
if (password == null) {
    println "No cspc.pass set"
    return 1
}

def authString = "${username}:${password}".bytes.encodeBase64().toString()
def command = '''<Request xmlns="http://www.parinetworks.com/api/schemas/1.1" requestId="">
  <Manage>
    <Get operationId="1">
      <DeviceList all="true" />
    </Get>
  </Manage>
</Request>'''

def conn = "https://${host}:${port}/cspc/xml".toURL().openConnection()
conn.setRequestMethod("POST")
conn.addRequestProperty("Content-Type", 'application/xml; charset=utf-8')
conn.addRequestProperty("Authorization", "Basic ${authString}")
conn.addRequestProperty("cache-control", "no-cache")
// begin POST
conn.setDoOutput(true)
OutputStream os = new BufferedOutputStream(conn.getOutputStream())
os.write(command.getBytes())
os.flush()
os.close()
//end POST

if (conn.responseCode == 200) {
    def xml = new XmlParser().parseText(conn.content.text)
    xml.Manage.Get.DeviceList.Device.each { Device ->
        def device  = [
            "hostname": Device.HostName.text().toUpperCase(),
            "ipaddress": Device.IPAddress.text(),
            "status": Device.Status.text(),
            "family": Device.DeviceFamily.text(),
            "model": Device.Model.text()
        ]
        // Check status & wildalias
        if ( device.status == "Reachable" && device.hostname) {
            Map instance_props = [
                "device.name"          : device.hostname,
                "device.model"         : device.model,
                "device.lan_ip"        : device.ipaddress,
                "device.product_type"  : device.family,
                "system.categories"    : "Cisco_CSPC_Device",
            ]
            // Output instance
            emit.instance(device.ipaddress, device.hostname, device.model, instance_props)
        }
    }
    return 0
} else {
    println "Failure : ${conn.responseCode}"
    println "Message : ${conn.responseMessage}"
    println conn.getErrorStream()?.text
    return 1
}
