package maryino.district.carinspector.obd.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import maryino.district.carinspector.obd.data.platform.ObdTransportAvailabilityProvider
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshot
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshotProvider
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportStatus
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class AndroidBluetoothClassicPlaceholderTransportFactory : ObdTransportFactory {
    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> =
        ObdResult.Failure(
            ObdError.Unknown("Android Bluetooth Classic transport is not implemented yet")
        )
}

class AndroidBlePlaceholderTransportFactory : ObdTransportFactory {
    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> =
        ObdResult.Failure(
            ObdError.Unknown("Android BLE transport is not implemented yet")
        )
}

class AndroidWifiNetworkSnapshotProvider : WifiNetworkSnapshotProvider {
    override suspend fun snapshot(): WifiNetworkSnapshot? = null
}

class AndroidObdTransportAvailabilityProvider : ObdTransportAvailabilityProvider {
    override fun observeAvailability(): Flow<List<ObdTransportAvailability>> =
        flowOf(
            listOf(
                ObdTransportAvailability(
                    type = ObdTransportType.BluetoothClassic,
                    status = ObdTransportStatus.RequiresExternalSetup,
                    userAction = ObdRequiredSetupAction.OpenAndroidBluetoothSettings
                ),
                ObdTransportAvailability(
                    type = ObdTransportType.BluetoothLowEnergy,
                    status = ObdTransportStatus.PermissionRequired,
                    userAction = ObdRequiredSetupAction.GrantBluetoothPermission
                ),
                ObdTransportAvailability(
                    type = ObdTransportType.WifiTcp,
                    status = ObdTransportStatus.RequiresExternalSetup,
                    userAction = ObdRequiredSetupAction.ConnectToAdapterWifi
                )
            )
        )
}
