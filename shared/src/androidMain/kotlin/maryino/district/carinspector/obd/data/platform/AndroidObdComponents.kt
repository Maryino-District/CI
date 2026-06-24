package maryino.district.carinspector.obd.data.platform

import maryino.district.carinspector.obd.data.discovery.CompositeObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.FailingObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.WifiTcpCandidateScanner
import maryino.district.carinspector.obd.data.discovery.WifiTcpObdAdapterDiscovery
import maryino.district.carinspector.obd.data.transport.CompositeObdTransportFactory
import maryino.district.carinspector.obd.data.transport.WifiTcpTransport
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import maryino.district.carinspector.obd.platform.AndroidBlePlaceholderTransportFactory
import maryino.district.carinspector.obd.platform.AndroidBluetoothClassicSppTransportFactory
import maryino.district.carinspector.obd.platform.AndroidObdTransportAvailabilityProvider
import maryino.district.carinspector.obd.platform.AndroidWifiNetworkSnapshotProvider

actual fun createPlatformObdComponents(): PlatformObdComponents {
    val wifiSnapshotProvider = AndroidWifiNetworkSnapshotProvider()
    return PlatformObdComponents(
        discovery = CompositeObdAdapterDiscovery(
            listOf(
                FailingObdAdapterDiscovery(
                    type = ObdTransportType.BluetoothClassic,
                    error = ObdError.Unknown("Android Bluetooth Classic discovery is not implemented yet"),
                    isEnabled = { request -> request.includeBluetoothClassicCandidates }
                ),
                FailingObdAdapterDiscovery(
                    type = ObdTransportType.BluetoothLowEnergy,
                    error = ObdError.Unknown("Android BLE discovery is not implemented yet"),
                    isEnabled = { request -> request.includeHeuristicBleCandidates }
                ),
                WifiTcpObdAdapterDiscovery(
                    scanner = WifiTcpCandidateScanner(wifiSnapshotProvider)
                )
            )
        ),
        transportFactory = CompositeObdTransportFactory(
            bluetoothClassicFactory = AndroidBluetoothClassicSppTransportFactory(),
            bleFactory = AndroidBlePlaceholderTransportFactory(),
            wifiTcpFactory = WifiTcpTransport()
        ),
        availabilityProvider = AndroidObdTransportAvailabilityProvider(),
        wifiSnapshotProvider = wifiSnapshotProvider
    )
}
