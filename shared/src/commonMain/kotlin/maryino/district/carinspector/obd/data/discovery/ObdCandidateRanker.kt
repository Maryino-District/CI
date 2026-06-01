package maryino.district.carinspector.obd.data.discovery

import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Orders scan candidates by connection usefulness without mutating discovery state.
 */
class ObdCandidateRanker {
    fun rank(
        candidates: List<DiscoveredObdAdapter>,
        remembered: AdapterFingerprint?
    ): List<DiscoveredObdAdapter> =
        candidates
            .map { candidate ->
                RankedCandidate(
                    candidate = candidate,
                    rejected = candidate.probeState is ObdCandidateProbeState.Rejected,
                    priority = candidate.priority(remembered),
                    confidence = candidate.confidence.rank,
                    signalLevel = candidate.signal?.level ?: NO_SIGNAL_LEVEL
                )
            }
            .sortedWith(RANKING_COMPARATOR)
            .map { it.candidate }

    private fun DiscoveredObdAdapter.priority(remembered: AdapterFingerprint?): Int =
        when {
            matchesRemembered(remembered) -> PRIORITY_REMEMBERED
            probeState is ObdCandidateProbeState.ProbeConfirmed -> PRIORITY_PROBE_CONFIRMED
            isKnownBleProfile() -> PRIORITY_KNOWN_BLE_PROFILE
            isClassicObdLikeName() -> PRIORITY_CLASSIC_OBD_LIKE_NAME
            isWifiRememberedSource() -> PRIORITY_WIFI_REMEMBERED_SOURCE
            isWifiGatewaySource() -> PRIORITY_WIFI_GATEWAY_SOURCE
            isWifiStaticKnownSource() -> PRIORITY_WIFI_STATIC_KNOWN_SOURCE
            isBleHeuristic() -> PRIORITY_BLE_HEURISTIC
            else -> PRIORITY_OTHER
        }

    private fun DiscoveredObdAdapter.matchesRemembered(remembered: AdapterFingerprint?): Boolean {
        if (remembered == null || remembered.transportType != transportType) return false
        return target.normalizedStableId() == remembered.stableId.normalizedStableId(transportType)
    }

    private fun DiscoveredObdAdapter.isKnownBleProfile(): Boolean =
        transportType == ObdTransportType.BluetoothLowEnergy &&
            (ObdAdapterCapability.KnownBleUartProfile in capabilities ||
                !(target as? ObdConnectionTarget.Ble)?.knownProfileId.isNullOrBlank())

    private fun DiscoveredObdAdapter.isClassicObdLikeName(): Boolean =
        transportType == ObdTransportType.BluetoothClassic &&
            OBD_LIKE_NAME_MARKERS.any { marker ->
                displayNameForClassicRanking().normalizedName().contains(marker)
            }

    private fun DiscoveredObdAdapter.isWifiRememberedSource(): Boolean =
        (target as? ObdConnectionTarget.WifiTcp)?.source is WifiCandidateSource.Remembered

    private fun DiscoveredObdAdapter.isWifiGatewaySource(): Boolean =
        (target as? ObdConnectionTarget.WifiTcp)?.source is WifiCandidateSource.Gateway

    private fun DiscoveredObdAdapter.isWifiStaticKnownSource(): Boolean =
        (target as? ObdConnectionTarget.WifiTcp)?.source is WifiCandidateSource.StaticKnown

    private fun DiscoveredObdAdapter.isBleHeuristic(): Boolean =
        transportType == ObdTransportType.BluetoothLowEnergy &&
            ObdAdapterCapability.HeuristicBleUartProfile in capabilities

    private fun ObdConnectionTarget.normalizedStableId(): String =
        when (this) {
            is ObdConnectionTarget.BluetoothClassic -> deviceAddress.normalizedStableId(ObdTransportType.BluetoothClassic)
            is ObdConnectionTarget.Ble -> peripheralId
            is ObdConnectionTarget.WifiTcp -> "$host:$port"
        }

    private fun DiscoveredObdAdapter.displayNameForClassicRanking(): String =
        (target as? ObdConnectionTarget.BluetoothClassic)?.deviceName ?: displayName

    private data class RankedCandidate(
        val candidate: DiscoveredObdAdapter,
        val rejected: Boolean,
        val priority: Int,
        val confidence: Int,
        val signalLevel: Int
    )

    private companion object {
        const val PRIORITY_REMEMBERED = 700
        const val PRIORITY_PROBE_CONFIRMED = 650
        const val PRIORITY_KNOWN_BLE_PROFILE = 600
        const val PRIORITY_CLASSIC_OBD_LIKE_NAME = 500
        const val PRIORITY_WIFI_REMEMBERED_SOURCE = 450
        const val PRIORITY_WIFI_GATEWAY_SOURCE = 400
        const val PRIORITY_WIFI_STATIC_KNOWN_SOURCE = 350
        const val PRIORITY_BLE_HEURISTIC = 300
        const val PRIORITY_OTHER = 100
        const val NO_SIGNAL_LEVEL = -1

        val ObdAdapterConfidence.rank: Int
            get() = when (this) {
                ObdAdapterConfidence.Low -> 0
                ObdAdapterConfidence.Medium -> 1
                ObdAdapterConfidence.High -> 2
            }

        fun String.normalizedName(): String =
            lowercase().filterNot { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '-' || it == '_' }

        fun String.normalizedStableId(transportType: ObdTransportType): String =
            when (transportType) {
                ObdTransportType.BluetoothClassic -> uppercase()
                ObdTransportType.BluetoothLowEnergy,
                ObdTransportType.WifiTcp -> this
            }

        val OBD_LIKE_NAME_MARKERS = listOf(
            "OBDII",
            "OBD-II",
            "ELM327",
            "V-LINK",
            "Vgate",
            "OBDLink",
            "Viecar",
            "Car Scanner"
        ).map { it.normalizedName() }

        val RANKING_COMPARATOR = compareBy<RankedCandidate> { it.rejected }
            .thenByDescending { it.priority }
            .thenByDescending { it.confidence }
            .thenByDescending { it.signalLevel }
            .thenBy { it.candidate.displayName.lowercase() }
            .thenBy { it.candidate.id.value }
    }
}
