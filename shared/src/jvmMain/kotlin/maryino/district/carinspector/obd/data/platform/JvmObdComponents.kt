package maryino.district.carinspector.obd.data.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import maryino.district.carinspector.obd.data.discovery.CompositeObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.FailingObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.WifiTcpCandidateScanner
import maryino.district.carinspector.obd.data.discovery.WifiTcpObdAdapterDiscovery
import maryino.district.carinspector.obd.data.transport.CompositeObdTransportFactory
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.data.transport.WifiTcpTransport
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshot
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshotProvider
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportStatus
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

actual fun createPlatformObdComponents(): PlatformObdComponents {
    val wifiSnapshotProvider = JvmWifiNetworkSnapshotProvider()
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
                    error = ObdError.UnsupportedTransport(ObdTransportType.BluetoothLowEnergy),
                    isEnabled = { request -> request.includeHeuristicBleCandidates }
                ),
                WifiTcpObdAdapterDiscovery(
                    scanner = WifiTcpCandidateScanner(wifiSnapshotProvider)
                )
            )
        ),
        transportFactory = CompositeObdTransportFactory(
            bluetoothClassicFactory = JvmUnsupportedTransportFactory(ObdTransportType.BluetoothClassic),
            bleFactory = JvmUnsupportedTransportFactory(ObdTransportType.BluetoothLowEnergy),
            wifiTcpFactory = WifiTcpTransport()
        ),
        availabilityProvider = JvmObdTransportAvailabilityProvider(),
        wifiSnapshotProvider = wifiSnapshotProvider
    )
}

private class JvmUnsupportedTransportFactory(
    private val type: ObdTransportType
) : ObdTransportFactory {
    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> =
        ObdResult.Failure(ObdError.UnsupportedTransport(type))
}

private class JvmWifiNetworkSnapshotProvider : WifiNetworkSnapshotProvider {
    override suspend fun snapshot(): WifiNetworkSnapshot? = null
}

private class JvmObdTransportAvailabilityProvider : ObdTransportAvailabilityProvider {
    override fun observeAvailability(): Flow<List<ObdTransportAvailability>> =
        flowOf(
            listOf(
                ObdTransportAvailability(
                    type = ObdTransportType.BluetoothClassic,
                    status = ObdTransportStatus.UnsupportedOnPlatform,
                    userAction = null
                ),
                ObdTransportAvailability(
                    type = ObdTransportType.BluetoothLowEnergy,
                    status = ObdTransportStatus.UnsupportedOnPlatform,
                    userAction = null
                ),
                ObdTransportAvailability(
                    type = ObdTransportType.WifiTcp,
                    status = ObdTransportStatus.Available,
                    userAction = null
                )
            )
        )
}
