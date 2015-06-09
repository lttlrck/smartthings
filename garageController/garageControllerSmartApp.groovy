definition(
    name: "Garage Controller",
    namespace: "lttlrck",
    author: "Stuart Allen",
    description: "Garage Controller Service Manager",
    category: "Safety & Security",
    iconUrl: "https://graph.api.smartthings.com/api/devices/icons/st.doors.garage.garage-closed",
    iconX2Url: "https://graph.api.smartthings.com/api/devices/icons/st.doors.garage.garage-closed?displaySize=2x")


preferences {
    page(name:"garageControllerDiscovery", title:"Garage Controller Setup", content:"garageControllerDiscovery", refreshTimeout:5)
}

def update( evt) {

    def devices = getChildDevices()

    devices.each {

        it.poll()
    }
}

mappings {
    path("/event/update") {
        action: [
            GET: "update"
        ]
    }
}

def garageControllerDiscovery()
{
    if(true)
    {
        int discoveryRefreshCount = !state.discoveryRefreshCount ? 0 : state.discoveryRefreshCount as int
        state.discoveryRefreshCount = discoveryRefreshCount + 1
        def refreshInterval = 3

        def options = smartFacesDiscovered() ?: []

        def numFound = options.size() ?: 0

        if(!state.subscribe) {
            log.trace "subscribe to location"
            subscribe(location, null, locationHandler, [filterEvents:false])
            state.subscribe = true
        }

        if((discoveryRefreshCount % 8) == 0) {
            discoverSmartFaces()
        }

        if(((discoveryRefreshCount % 1) == 0) && ((discoveryRefreshCount % 8) != 0)) {
            verifySmartFace()
        }

        return dynamicPage(name:"garageControllerDiscovery", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
            section("Please wait while we discover your Garage Controller. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
                input "selectedGarageController", "enum", required:false, title:"Select Garage Controller (${numFound} found)", multiple:true, options:options
            }
        }
    }
    else
    {
        def upgradeNeeded = """To use SmartThings Labs, your Hub should be completely up to date.

To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""

        return dynamicPage(name:"garageControllerDiscovery", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
            section("Upgrade") {
                paragraph "$upgradeNeeded"
            }
        }
    }
}

private verifySmartFace() {
    def devices = getSmartFace().findAll { it?.value?.verified != true }

    if(devices) {
        log.warn "UNVERIFIED CONTROLLERS!: $devices"
    }

    devices.each {
        log.warn (it?.value)
        log.warn (it?.value?.ip + ":" + it?.value?.port)
        verifySmartFaces((it?.value?.ip + ":" + it?.value?.port))
    }
}

private verifySmartFaces(String deviceNetworkId) {

    log.trace "dni: $deviceNetworkId"
    String ip = getHostAddress(deviceNetworkId)

    log.trace "ip:" + ip

    sendHubCommand(new physicalgraph.device.HubAction("""GET /GarageController/1 HTTP/1.1\r\nHOST: $ip\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

private discoverSmartFaces()
{
    //consider using other discovery methods

    log.debug("Sending lan discovery urn:schemas-upnp-org:device:GarageController:1")
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:GarageController:1", physicalgraph.device.Protocol.LAN))
}

Map smartFacesDiscovered() {
    def v = getVerifiedSmartFaces()
        log.trace "getVerifiedSmartFaces"
        log.trace v
    def map = [:]
    v.each {
        def value = "${it.value.name}"
        def key = it.value.ip + ":" + it.value.port
        map["${key}"] = value
        log.trace key
    }
    map
}

def getSmartFace()
{
    state.smartFace = state.smartFace ?: [:]
}

def getVerifiedSmartFaces()
{
    getSmartFace()
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unschedule()
    initialize()
}


def uninstalled() {
    def devices = getChildDevices()
    log.trace "deleting ${devices.size()} GarageController"
    devices.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initialize() {

    unsubscribe()
    state.subscribe = false

    unschedule()
    scheduleActions()

    if (selectedGarageController) {
        addSmartFace()
    }

    scheduledActionsHandler()
}

def scheduledActionsHandler() {
    log.trace "scheduledActionsHandler()"
    syncDevices()
    refreshAll()

    // TODO - for auto reschedule
    if (!state.threeHourSchedule) {
        scheduleActions()
    }
}

private scheduleActions() {
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * 60))
    def hour = Math.round(Math.floor(Math.random() * 3))
    def cron = "$sec $min $hour/3 * * ?"
    log.debug "schedule('$cron', scheduledActionsHandler)"
    schedule(cron, scheduledActionsHandler)

    // TODO - for auto reschedule
    state.threeHourSchedule = true
    state.cronSchedule = cron
}

private syncDevices() {
    log.trace "Doing smartFace Device Sync!"
    //runIn(300, "doDeviceSync" , [overwrite: false]) //schedule to run again in 5 minutes

    if(!state.subscribe) {
        subscribe(location, null, locationHandler, [filterEvents:false])
        state.subscribe = true
    }

}
}

private refreshAll(){
    log.trace "refreshAll()"
    childDevices*.refresh()
    log.trace "/refreshAll()"
}

def addSmartFace() {
    def players = getVerifiedSmartFaces()
    def runSubscribe = false
    selectedGarageController.each { dni ->
        def d = getChildDevice(dni)
        if(!d) {
            def newPlayer = players.find { (it.value.ip + ":" + it.value.port) == dni }
            log.trace "newPlayer = $newPlayer"
            log.trace "dni = $dni"
            d = addChildDevice("lttlrck", "Garage Controller", dni, newPlayer?.value.hub, [label:"${newPlayer?.value.name} GarageController"])
            log.trace "created ${d.displayName} with id $dni"

            d.setModel(newPlayer?.value.model)
            log.trace "setModel to ${newPlayer?.value.model}"

            runSubscribe = true
        } else {
            log.trace "found ${d.displayName} with id $dni already exists"
        }
    }
}

def locationHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId

    def parsedEvent = parseEventMessage(description)
    parsedEvent << ["hub":hub]

        log.trace "evt"+evt
    log.trace parsedEvent

    if (parsedEvent?.ssdpTerm?.contains("lttlrck:GarageController"))
    { //SSDP DISCOVERY EVENTS

//    state.smartFace= [:]

        log.trace "smartFace found:"+parsedEvent?.ssdpTerm
        def smartFace = getSmartFace()

        if (!(smartFace."${parsedEvent.ssdpUSN.toString()}"))
        { //smartFace does not exist
            smartFace << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
}
        }
        else
        { // update the values

            log.trace "Device was already found in state..."

            def d = smartFace."${parsedEvent.ssdpUSN.toString()}"
            boolean deviceChangedValues = false

            if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
                d.ip = parsedEvent.ip
                d.port = parsedEvent.port
                deviceChangedValues = true
                log.trace "Device's port or ip changed..."
            }

            if (deviceChangedValues) {
                def children = getChildDevices()
                children.each {
                    if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
                        log.trace "updating dni for device ${it} with mac ${parsedEvent.mac}"
                        it.setDeviceNetworkId((parsedEvent.ip + ":" + parsedEvent.port)) //could error if device with same dni already exists
                    }
                }
            }
        }
    }
    else if (parsedEvent.headers && parsedEvent.body)
    { // SONOS RESPONSES
        def headerString = new String(parsedEvent.headers.decodeBase64())
        def bodyString = new String(parsedEvent.body.decodeBase64())

        def type = (headerString =~ /Content-Type:.*/) ? (headerString =~ /Content-Type:.*/)[0] : null
        def body
        log.trace "SONOS REPONSE TYPE: $type"
        log.trace "SONOS BODY TYPE: $bodyString"
        if (type?.contains("json"))
        {
            body = new groovy.json.JsonSlurper().parseText(bodyString)

            if (body?.device?.modelName.startsWith("lttlrck"))
            {
                def sonoses = getSmartFace()

                def player = sonoses.find {it?.key?.contains(body?.device?.key)}
                if (player)
                {
                    player.value << [name:body?.device?.name,model:body?.device?.modelName, serialNumber:body?.device?.serialNum, verified: true]
                }
                else
                {
                    log.error "/xml/device_description.xml returned a device that didn't exist"
                }
            }
        }
        else if(type?.contains("json"))
        { //(application/json)
            body = new groovy.json.JsonSlurper().parseText(bodyString)
            log.trace "GOT JSON $body"
        }

    }
    else {
        log.trace "cp desc: " + description
        //log.trace description
    }
}

private def parseEventMessage(Map event) {
    //handles smartFace attribute events
    return event
}

private def parseEventMessage(String description) {
    def event = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()
        if (part.startsWith('devicetype:')) {
            def valueString = part.split(":")[1].trim()
            event.devicetype = valueString
        }
        else if (part.startsWith('mac:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.mac = valueString
            }
        }
        else if (part.startsWith('networkAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ip = valueString
            }
        }
        else if (part.startsWith('deviceAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.port = valueString
            }
        }
        else if (part.startsWith('ssdpPath:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ssdpPath = valueString
            }
        }
        else if (part.startsWith('ssdpUSN:')) {
            part -= "ssdpUSN:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpUSN = valueString
            }
        }
        else if (part.startsWith('ssdpTerm:')) {
            part -= "ssdpTerm:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpTerm = valueString
            }
        }
        else if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                event.headers = valueString
            }
        }
        else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                event.body = valueString
            }
        }
    }

    event
}


/////////CHILD DEVICE METHODS
def parse(childDevice, description) {
    def parsedEvent = parseEventMessage(description)

    if (parsedEvent.headers && parsedEvent.body) {
        def headerString = new String(parsedEvent.headers.decodeBase64())
        def bodyString = new String(parsedEvent.body.decodeBase64())
        log.trace "parse() - ${bodyString}"

        def body = new groovy.json.JsonSlurper().parseText(bodyString)
    } else {
        log.trace "parse - got something other than headers,body..."
        return []
    }
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress(d) {
    def parts = d.split(":")
    def ip = convertHexToIP(parts[0])
    def port = convertHexToInt(parts[1])
    return ip + ":" + port
}

private Boolean canInstallLabs()
{
    return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
    return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
    return location.hubs*.firmwareVersionString.findAll { it }
}
