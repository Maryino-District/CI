package maryino.district.carinspector.obd.domain.model.scan

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Defines one adapter discovery pass across all eligible transports.
 *
 * Defaults match the main product flow: one "Find adapter" action runs Classic,
 * BLE, and Wi-Fi discovery together without asking the user to choose a type.
 */
data class ObdScanRequest(
    val timeout: Duration = 10.seconds,
    val transportTypes: Set<ObdTransportType> = ObdTransportType.entries.toSet(),
    val includeRememberedAdapters: Boolean = true,
    val includeBluetoothClassicCandidates: Boolean = true,
    val includeHeuristicBleCandidates: Boolean = true,
    val includeWifiTcpCandidates: Boolean = true,
    val showClassicPairingHintAfter: Duration = 1.seconds
)
