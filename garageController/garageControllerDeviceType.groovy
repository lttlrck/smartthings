/**
 *  Garage Controller
 *
 *  Copyright 2014 Stuart Allen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {

    definition (name: "Garage Controller", namespace: "lttlrck", author: "Stuart Allen") {

        capability "Switch"
        capability "Refresh"
        capability "Polling"

        attribute "door", "string"

        command "close"
        command "open"
    }

    simulator {

    }

    tiles {
        standardTile("door", "device.door", width: 2, height: 2) {
            state("open", label:'Open', icon:"st.doors.garage.garage-open", action: "closeDoor",  backgroundColor:"#b8b821", nextState: "closing")
            state("closing", label:'Closing', icon:"st.doors.garage.garage-closing", /*action: "pushLeft",*/  backgroundColor:"#b8b821")
            state("closed", label:'Closed', icon:"st.doors.garage.garage-closed", action: "openDoor", backgroundColor:"#79b821", nextState: "opening")
            state("opening", label:'Opening', icon:"st.doors.garage.garage-opening", /*action: "pushLeft",*/  backgroundColor:"#b8b821")
        }
        main(["door"])
        details(["door"])
    }
}

def poll() {

    refresh()
}

def locationHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId

    def parsedEvent = parseEventMessage(description)
    parsedEvent << ["hub":hub]

//        log.trace "evt"+evt
    log.trace parsedEvent
}

def refresh() {

    log.debug "Executing 'refresh'"

    get("/GarageController/1/status")
}

def closeDoor() {

    get("/GarageController/1/door/close")
}

def openDoor() {

    get("/GarageController/1/door/open")
}

private getCallBackAddress() {

    device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private Integer convertHexToInt(hex) {

    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {

    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {

    def parts = device.deviceNetworkId.split(":")
    def ip = convertHexToIP(parts[0])
    def port = convertHexToInt(parts[1])

    return ip + ":" + port
}

private get( path) {

    log.debug "GET ${path}"

    def result = new physicalgraph.device.HubAction(
            method: "GET",
            path: path,
            headers: [HOST:getHostAddress()]
    )
}

private subscribe( path) {

    log.debug "SUBSCRIBE ${path}"

    def result = new physicalgraph.device.HubAction(
            method: "SUBSCRIBE",
            path: path,
            headers: [HOST:getHostAddress()]
    )
}

private def parseEventMessage(String description) {
    def event = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()

        if (part.startsWith('headers')) {
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

private parse(String description) {
    log.debug "Parsing '${description}'"

    def parsedEvent= parseEventMessage( description)

    def headerString = new String(parsedEvent.headers.decodeBase64())
    def bodyString = new String(parsedEvent.body.decodeBase64())

    def json = new groovy.json.JsonSlurper().parseText( bodyString)

    log.trace json

    if( json.msg)
    {
        if( json.msg.startsWith("state"))
        {
            log.trace "Setting state"

            sendEvent (name: json.name, value: json.state)
        }
        else if( json.msg.startsWith("status"))
        {
            log.trace "Setting state from status"

            sendEvent (name: "door",  value: json.door)
        }
    }
}
