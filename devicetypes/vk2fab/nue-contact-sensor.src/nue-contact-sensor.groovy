/**
 *  Copyright 2018 SmartThings
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
 *
 *  Date:  2018-07-03
 */

import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata {
//	definition(name: "Nue Contact Sensor - New", namespace: "VK2FAB", author: "VK2FAB", runLocally: true, minHubCoreVersion: '000.017.0012', mnmn:"SmartThings", vid:"generic-contact-3", ocfDeviceType: "x.com.st.d.sensor.contact") {
	definition(name: "Nue Contact Sensor", namespace: "VK2FAB", author: "VK2FAB", runLocally: false, minHubCoreVersion: '000.017.0012', mnmn:"SmartThings", vid:"generic-contact-3", ocfDeviceType: "x.com.st.d.sensor.contact") {
		capability "Battery"
		capability "Configuration"
		capability "Contact Sensor"
		capability "Refresh"
		capability "Health Check"
		capability "Sensor"
        //capability "Door Control"

		// Added 2020.09.02 - VK2FAB for Nue/Tuya Sensor
		//fingerprint profileId: "0104", inClusters: "0000,0001,0003,0500", outClusters: "0003", deviceJoinName: "Tuya Door/Window Sensor"
		////////fingerprint profileId: "0104", inClusters: "0000,0001,0003,0500", outClusters: "0003", manufacturer: "TUYATEC-xrvzm3yI", model: "RH3001", deviceJoinName: "Tuya Door/Window Sensor"

		// From 3A Published Device Handlers
    	fingerprint inClusters: "0000,0001,0003,0020,0500,0402,0502", outClusters: "", manufacturer: "Feibit,co.ltd", model: "FB56-DOS06HM1.1", deviceJoinName: "Nue Door/Window Sensor"
		fingerprint inClusters: "0000,0001,0003,0020,0500,0402,0502,0006,0019", outClusters: "", manufacturer: "Feibit", model: "FNB56-DOS07FB3.1", deviceJoinName: "Nue Door/Window Sensor"
	}

	simulator {
		status "open": "zone status 0x0021 -- extended status 0x00"
		status "close": "zone status 0x0000 -- extended status 0x00"
		for (int i = 0; i <= 90; i += 10) {
		//	status "battery 0x${i}": "read attr - raw: 2E6D01000108210020C8, dni: 2E6D, endpoint: 01, cluster: 0001, size: 08, attrId: 0021, encoding: 20, value: ${i}"
        //	status "battery 0x${i}": "read attr - raw: 892B010001102000201B, dni: 892B, endpoint: 01, cluster: 0001, size: 16, attrId: 0020, encoding: 20, value: ${i}"
        	status "battery 0x${i}": "read attr - raw: 892B010001102100201B, dni: 892B, endpoint: 01, cluster: 0001, size: 16, attrId: 0021, encoding: 20, value: ${i}"
		}
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
			tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13"
				attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC"
			}
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery', unit: ""
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}
		main(["contact"])
		details(["contact", "battery", "refresh"])
	}
}

def parse(String description) {
	log.debug "description: $description"
	def result = [:]
	Map map = zigbee.getEvent(description)
	if (!map) {
		if (description?.startsWith('zone status')) {
			ZoneStatus zs = zigbee.parseZoneStatus(description)
			map = zs.isAlarm1Set() ? getContactResult('open') : getContactResult('closed')
			result = createEvent(map)
		} else if (description?.startsWith('enroll request')) {
			List cmds = zigbee.enrollResponse()
			log.debug "enroll response: ${cmds}"
			result = cmds?.collect { new physicalgraph.device.HubAction(it) }
		} else {
			Map descMap = zigbee.parseDescriptionAsMap(description)
            // Testing - 2020.10.20
            map.isStateChange = true
            // Testing - 2020.10.10
			if (descMap?.clusterInt == 0x0001 && descMap?.commandInt != 0x07 && descMap?.value) {
				if (descMap?.attrInt==0x0021) {
					map = getBatteryPercentageResult(Integer.parseInt(descMap.value, 16))
				} else {
					map = getBatteryResult(Integer.parseInt(descMap.value, 16))
				}
				result = createEvent(map)
			} else if (descMap?.clusterInt == 0x0500 && descMap?.attrInt == 0x0002) {
				// Testing - 2020.10.20
            	map.isStateChange = true
            	// Testing - 2020.10.10
				def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
				map = getContactResult(zs.isAlarm1Set() ? "open" : "closed")
				result = createEvent(map)
			}
		}
	} else {
		result = createEvent(map)
	}
	log.debug "Parse returned $result"
	result
}


/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.debug "ping is called"
	zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS)
    return zigbee.readAttribute(0x0001, 0x0020) // Read the Battery Level
}

def installed() {
	log.debug "call installed()"
	sendEvent(name: "checkInterval", value:60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
}


def refresh() {
	log.debug "Refreshing Battery and Zone Status..."
	def refreshCmds = zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS)
//		refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021)
		refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)
	refreshCmds + zigbee.enrollResponse()
}


/* =====================
def refresh() {
	log.debug "Refreshing Battery and Zone Status..."
	def manufacturer = getDataValue("manufacturer")
//	}
	if (manufacturer == "Aurora") {
		refreshCmds += zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 60 * 5, null) + zigbee.batteryConfig()
	} else if (manufacturer == "ORVIBO" || manufacturer == "eWeLink" || manufacturer == "HEIMAN") {
		refreshCmds += zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 60 * 5, null) + zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 600, 1)
	}
	refreshCmds + zigbee.enrollResponse()
}
=========================*/

def configure() {
	def manufacturer = getDataValue("manufacturer")
    sendEvent(name: "checkInterval", value:60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
//	}
	def cmds = []
	log.debug "Configuring Reporting, IAS CIE, and Bindings."
	//The electricity attribute is reported without bind and reporting CFG.
    //The TI plan reports the power once in about 10 minutes; the NXP plan reports the electricity once in 20 minutes
	if (manufacturer == "Aurora") {
		cmds = zigbee.enrollResponse() + zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 60 * 5, null) + zigbee.batteryConfig()
	} else if (manufacturer == "eWeLink" || manufacturer == "HEIMAN") {
		cmds = zigbee.enrollResponse() + zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, 30, 60 * 5, null) + zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 600, 1)
	}
	cmds += refresh()
	cmds
}

def getBatteryPercentageResult(rawValue) {
	log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
	def result = [:]
	if (0 <= rawValue && rawValue <= 200) {
		result.name = 'battery'
		result.translatable = true
		result.value = Math.round(rawValue / 2)
		result.descriptionText = "${device.displayName} battery was ${result.value}%"
	}
	result
}

private Map getBatteryResult(rawValue) {
	log.debug 'Battery'
	def linkText = getLinkText(device)
	def result = [:]
	def volts = rawValue / 10
	if (!(rawValue == 0 || rawValue == 255)) {
		def minVolts = 2.1
		def maxVolts = 3.0
		def pct = (volts - minVolts) / (maxVolts - minVolts)
		def roundedPct = Math.round(pct * 100)
		if (roundedPct <= 0)
			roundedPct = 1
		result.value = Math.min(100, roundedPct)
		result.descriptionText = "${linkText} battery was ${result.value}%"
		result.name = 'battery'
	}
	return result
}

def getContactResult(value) {
	log.debug 'Contact Status'
	def linkText = getLinkText(device)
	def descriptionText = "${linkText} was ${value == 'open' ? 'opened' : 'closed'}"
	[
		name           : 'contact',
		value          : value,
		descriptionText: descriptionText
	]
}