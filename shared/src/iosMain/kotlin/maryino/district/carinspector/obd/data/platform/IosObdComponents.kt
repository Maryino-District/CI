package maryino.district.carinspector.obd.data.platform

import maryino.district.carinspector.obd.data.discovery.CompositeObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.FailingObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.WifiTcpCandidateScanner
import maryino.district.carinspector.obd.data.discovery.WifiTcpObdAdapterDiscovery
import maryino.district.carinspector.obd.data.transport.CompositeObdTransportFactory
import maryino.district.carinspector.obd.data.transport.WifiTcpTransport
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import maryino.district.carinspector.obd.platform.IosBlePlaceholderTransportFactory
import maryino.district.carinspector.obd.platform.IosBluetoothClassicUnsupportedTransportFactory
import maryino.district.carinspector.obd.platform.IosObdTransportAvailabilityProvider
import maryino.district.carinspector.obd.platform.IosWifiNetworkSnapshotProvider

actual fun createPlatformObdComponents(): PlatformObdComponents {
    val wifiSnapshotProvider = IosWifiNetworkSnapshotProvider()
    return PlatformObdComponents(
        discovery = CompositeObdAdapterDiscovery(
            listOf(
                FailingObdAdapterDiscovery(
                    type = ObdTransportType.BluetoothClassic,
                    error = ObdError.UnsupportedTransport(ObdTransportType.BluetoothClassic),
                    isEnabled = { request -> request.includeBluetoothClassicCandidates }
                ),
                FailingObdAdapterDiscovery(
                    type = ObdTransportType.BluetoothLowEnergy,
                    error = ObdError.Unknown("iOS BLE discovery is not implemented yet"),
                    isEnabled = { request -> request.includeHeuristicBleCandidates }
                ),
                WifiTcpObdAdapterDiscovery(
                    scanner = WifiTcpCandidateScanner(wifiSnapshotProvider)
                )
            )
        ),
        transportFactory = CompositeObdTransportFactory(
            bluetoothClassicFactory = IosBluetoothClassicUnsupportedTransportFactory(),
            bleFactory = IosBlePlaceholderTransportFactory(),
            wifiTcpFactory = WifiTcpTransport()
        ),
        availabilityProvider = IosObdTransportAvailabilityProvider(),
        wifiSnapshotProvider = wifiSnapshotProvider
    )
}
