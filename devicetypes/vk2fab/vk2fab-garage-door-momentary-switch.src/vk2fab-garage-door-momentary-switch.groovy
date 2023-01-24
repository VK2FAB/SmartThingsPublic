/**
 *
 *  Garage Door Momentary Device Handler
 *
 *  This device handler has been is based on a Nue Zigbee Curtain/Blind controller (hence the reference to Window Coverings in the code)
 *  It has been adapted to activate one of the three relays in the device momentarily in order to simulate a press of the garage door button
 * 
 *  Author:  VK2FAB
 *  Date:  2020-09-24
 */
 
import physicalgraph.zigbee.zcl.DataType
 
metadata {
	definition (name: "VK2FAB Garage Door Momentary Switch", namespace: "VK2FAB", author: "VK2FAB", ocfDeviceType: "x.com.st.d.momentary.relay") {
		capability "Actuator"
		capability "Switch"
		capability "Momentary"
		capability "Refresh"
		capability "Sensor"
      //  capability "Door Control"
        
        // define custom attribute and commands to support garage door function
        attribute 'door', 'enum', ['open', 'closed']
		command 'doorOpen'
        command 'doorClosed'
        
////////        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0102", outClusters: "000A", manufacturer: "Feibit Co.Ltd", model: "FB56+CUR17SB1.4", deviceJoinName: "Garage Door Controller"
        
	}


	// simulator metadata
	simulator {
		// status messages
		// none

		// reply messages
		reply "'on','delay 2000','off'": "switch:off"
	}


	// tile definitions
	tiles {
		standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true) {
			state 'off', label: 'push', action: 'momentary.push', icon: 'st.switches.switch.off', backgroundColor: '#ffffff'
			state 'on', label: 'pressed', action: 'switch.off', icon: 'st.switches.switch.on', backgroundColor: '#00a0dc'
		}
		standardTile('refresh', 'device.switch', inactiveLabel: false, decoration: 'flat') {
			state 'default', label:'', action:'refresh.refresh', icon:'st.secondary.refresh'
		}

		//main 'switch'
        main 'toggle'
		details(['switch', 'refresh'])
	}
}

private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }

private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return  descMaps
}

def parse(String description) {
	def result = null
    log.debug "description:- ${description}"
    if (description?.startsWith("read attr -")) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
            log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"
            List<Map> descMaps = collectAttributes(descMap)
            def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
            if (liftmap) {
                state.level = Integer.parseInt(liftmap.value, 16)
                if (liftmap.value == "64") { //open
                    sendEvent(name: "windowShade", value: "open")
                    sendEvent(name: "level", value: "100")
                } else if (liftmap.value == "00") { //closed
                    sendEvent(name: "windowShade", value: "closed")
                    sendEvent(name: "level", value: "0")
                } else {
                    sendEvent(name: "windowShade", value: "partially open")
                    sendEvent(name: "level", value: zigbee.convertHexToInt(liftmap.value))
                }
            }
        }
    }
    return result
}

// actions to keep track of door state
def doorOpen() {
	state.door = "open"
    log.debug "New garage door state: $state.door"
}

def doorClosed() {
	state.door = "closed"
    log.debug "New garage door state: $state.door"
}

// actions to run the momentary push button
def push() {
	log.debug "Push Event: Door state is...  $state.door"
	if ("busy" == state.door) {
    	log.debug "Patience, don't push the button so fast"
    }
	// Don't operate at all if location mode is set to 'Sleep'.
	else if ("Away" == location.mode) {
    	log.debug "Location in Away mode, did NOT push button"
    }
//    else {
  		log.debug "I pushed the button"
        state.door = "busy"
	    delayBetween([
		zigbee.command(CLUSTER_WINDOW_COVERING, 0x00),
		zigbee.command(CLUSTER_WINDOW_COVERING, 0x02),
		], 1000)
//	}
}

def on() { // open the door
    log.debug "Attempt to open door while door is $state.door"
//	if (state.door == "closed") {
    	log.debug "Pushing the button to open the door"
		push()
//    }
}

def off() { // close the door
    log.debug "Attempt to close door while door is $state.door"
//	if (state.door == "open") {
    	log.debug "Pushing the button to close the door"
		push()
//    }
}

def open() { // open the door
    log.debug "Attempt to open door while door is $state.door"
//	if (state.door == "closed") {
    	log.debug "Pushing the button to open the door"
		push()
//    }
}

def close() { // close the door
    log.debug "Attempt to close door while door is $state.door"
//	if (state.door == "open") {
    	log.debug "Pushing the button to close the door"
		push()
//   }
}


/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    return refresh()
}

def refresh() { // also releases the relay in case it is stuck in the pressed position
    log.info "refresh()"
    def cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT)
    zigbee.command(CLUSTER_WINDOW_COVERING, 0x02)
    return cmds
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 2 min lag time)
    log.info "configure()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting and Bindings."
    zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 0, 600, null)
}
