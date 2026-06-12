package maryino.district.carinspector.obd.test

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import maryino.district.carinspector.obd.data.discovery.ObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.ObdDiscoveryEvent
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.ObdSignalStrength
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class FakeObdAdapterDiscovery(
    private val events: List<ObdDiscoveryEvent> = emptyList()
) : ObdAdapterDiscovery {
    var lastRequest: ObdScanRequest? = null
        private set

    override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
        lastRequest = request
        events.forEach { event -> emit(event) }
    }

    companion object {
        fun noOp(): FakeObdAdapterDiscovery = FakeObdAdapterDiscovery()

        fun scripted(vararg events: ObdDiscoveryEvent): FakeObdAdapterDiscovery =
            FakeObdAdapterDiscovery(events.toList())
    }
}

fun classicCandidate(
    id: String = "classic-obd",
    name: String = "ELM327",
    deviceAddress: String = "00:11:22:33:44:55",
    confidence: ObdAdapterConfidence = ObdAdapterConfidence.Medium,
    probeState: ObdCandidateProbeState = ObdCandidateProbeState.AdvertisementOnly,
    lastSeenAt: Instant = FakeDiscoveryNow
): DiscoveredObdAdapter =
    discoveredAdapter(
        id = id,
        displayName = name,
        transportType = ObdTransportType.BluetoothClassic,
        target = ObdConnectionTarget.BluetoothClassic(
            deviceAddress = deviceAddress,
            deviceName = name
        ),
        confidence = confidence,
        probeState = probeState,
        capabilities = setOf(ObdAdapterCapability.BluetoothClassicSpp),
        lastSeenAt = lastSeenAt
    )

fun bleCandidate(
    id: String = "ble-obd",
    name: String = "OBDLink CX",
    peripheralId: String = id,
    knownProfileId: String? = "obdlink_cx",
    discoveredServiceUuids: List<String> = listOf("0000FFF0-0000-1000-8000-00805F9B34FB"),
    confidence: ObdAdapterConfidence = ObdAdapterConfidence.High,
    signalLevel: Int? = 80,
    probeState: ObdCandidateProbeState = ObdCandidateProbeState.ServiceDiscovered,
    lastSeenAt: Instant = FakeDiscoveryNow
): DiscoveredObdAdapter =
    discoveredAdapter(
        id = id,
        displayName = name,
        transportType = ObdTransportType.BluetoothLowEnergy,
        target = ObdConnectionTarget.Ble(
            peripheralId = peripheralId,
            deviceName = name,
            knownProfileId = knownProfileId,
            discoveredServiceUuids = discoveredServiceUuids,
            discoveredAt = lastSeenAt
        ),
        signalLevel = signalLevel,
        confidence = confidence,
        probeState = probeState,
        capabilities = setOf(ObdAdapterCapability.KnownBleUartProfile),
        lastSeenAt = lastSeenAt
    )

fun wifiCandidate(
    id: String = "wifi-obd",
    host: String = "192.168.0.10",
    port: Int = 35000,
    source: WifiCandidateSource = WifiCandidateSource.StaticKnown(host),
    confidence: ObdAdapterConfidence = ObdAdapterConfidence.Medium,
    probeState: ObdCandidateProbeState = ObdCandidateProbeState.AdvertisementOnly,
    lastSeenAt: Instant = FakeDiscoveryNow
): DiscoveredObdAdapter =
    discoveredAdapter(
        id = id,
        displayName = "Wi-Fi OBD $host:$port",
        transportType = ObdTransportType.WifiTcp,
        target = ObdConnectionTarget.WifiTcp(
            host = host,
            port = port,
            source = source
        ),
        confidence = confidence,
        probeState = probeState,
        capabilities = setOf(ObdAdapterCapability.WifiTcpEndpoint),
        lastSeenAt = lastSeenAt
    )

private fun discoveredAdapter(
    id: String,
    displayName: String,
    transportType: ObdTransportType,
    target: ObdConnectionTarget,
    confidence: ObdAdapterConfidence,
    probeState: ObdCandidateProbeState,
    capabilities: Set<ObdAdapterCapability>,
    lastSeenAt: Instant,
    signalLevel: Int? = null
): DiscoveredObdAdapter =
    DiscoveredObdAdapter(
        id = ObdAdapterId(id),
        displayName = displayName,
        transportType = transportType,
        target = target,
        signal = signalLevel?.let { level -> ObdSignalStrength(rssiDbm = null, level = level) },
        confidence = confidence,
        probeState = probeState,
        capabilities = capabilities,
        lastSeenAt = lastSeenAt
    )

val FakeDiscoveryNow: Instant = Instant.parse("2026-05-31T00:00:00Z")
