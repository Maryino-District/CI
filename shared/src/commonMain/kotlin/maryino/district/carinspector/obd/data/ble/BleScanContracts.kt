package maryino.district.carinspector.obd.data.ble

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.ObdError

data class BleScanRequest(
    val timeout: Duration = 10.seconds,
    val includeRememberedCandidates: Boolean = true,
    val includeHeuristicCandidates: Boolean = true
)

sealed interface BleScanEvent {
    data class PeripheralFound(val peripheral: BleScannedPeripheral) : BleScanEvent
    data class PeripheralUpdated(val peripheral: BleScannedPeripheral) : BleScanEvent
    data class Failed(val error: ObdError) : BleScanEvent
    data object Finished : BleScanEvent
}

data class BleScannedPeripheral(
    val peripheralId: String,
    val name: String?,
    val rssiDbm: Int?,
    val advertisedServiceUuids: List<String>,
    val manufacturerData: List<BleManufacturerData>,
    val isConnectable: Boolean?,
    val seenAt: Instant
)

data class BleManufacturerData(
    val companyIdentifier: Int?,
    val hexPayload: String
)
