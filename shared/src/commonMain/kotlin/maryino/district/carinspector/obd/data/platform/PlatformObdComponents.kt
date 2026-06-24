package maryino.district.carinspector.obd.data.platform

import maryino.district.carinspector.obd.data.discovery.ObdAdapterDiscovery
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshotProvider

data class PlatformObdComponents(
    val discovery: ObdAdapterDiscovery,
    val transportFactory: ObdTransportFactory,
    val availabilityProvider: ObdTransportAvailabilityProvider,
    val wifiSnapshotProvider: WifiNetworkSnapshotProvider
)

expect fun createPlatformObdComponents(): PlatformObdComponents
