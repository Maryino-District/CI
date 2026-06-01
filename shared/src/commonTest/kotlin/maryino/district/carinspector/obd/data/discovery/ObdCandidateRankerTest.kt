package maryino.district.carinspector.obd.data.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.ObdSignalStrength
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class ObdCandidateRankerTest {
    private val ranker = ObdCandidateRanker()

    @Test
    fun rememberedFingerprintMatchWinsAcrossCandidateTypes() {
        val rememberedBle = ble(id = "remembered", peripheralId = "ble-remembered")
        val knownBle = ble(id = "known", knownProfileId = "generic_fff0")
        val classic = classic(id = "classic", name = "ELM327")
        val remembered = fingerprint(
            transportType = ObdTransportType.BluetoothLowEnergy,
            stableId = "ble-remembered"
        )

        val ranked = ranker.rank(listOf(knownBle, classic, rememberedBle), remembered)

        assertEquals(listOf("remembered", "known", "classic"), ranked.ids())
    }

    @Test
    fun probeConfirmedRanksBelowRememberedAndAboveDiscoveryHeuristics() {
        val rememberedBle = ble(id = "remembered", peripheralId = "ble-remembered")
        val confirmedWifi = wifi(
            id = "confirmed",
            source = WifiCandidateSource.StaticKnown("192.168.0.10"),
            probeState = ObdCandidateProbeState.ProbeConfirmed
        )
        val knownBle = ble(id = "known", knownProfileId = "generic_fff0")
        val classic = classic(id = "classic", name = "ELM327")
        val gatewayWifi = wifi(id = "gateway", source = WifiCandidateSource.Gateway("192.168.0.1"))
        val heuristic = ble(
            id = "heuristic",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile)
        )
        val remembered = fingerprint(
            transportType = ObdTransportType.BluetoothLowEnergy,
            stableId = "ble-remembered"
        )

        val ranked = ranker.rank(
            listOf(heuristic, gatewayWifi, classic, knownBle, confirmedWifi, rememberedBle),
            remembered
        )

        assertEquals(listOf("remembered", "confirmed", "known", "classic", "gateway", "heuristic"), ranked.ids())
    }

    @Test
    fun knownBleProfileRanksAboveHeuristicBleAndCanComeFromCapability() {
        val heuristic = ble(
            id = "heuristic",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile),
            confidence = ObdAdapterConfidence.High
        )
        val knownByProfileId = ble(id = "known-profile-id", knownProfileId = "generic_fff0")
        val knownByCapability = ble(
            id = "known-capability",
            capabilities = setOf(ObdAdapterCapability.KnownBleUartProfile)
        )

        val ranked = ranker.rank(listOf(heuristic, knownByProfileId, knownByCapability), remembered = null)

        assertEquals(listOf("known-capability", "known-profile-id", "heuristic"), ranked.ids())
    }

    @Test
    fun classicObdLikeNameRanksAbovePlainClassicAndNormalizesName() {
        val plainClassic = classic(id = "plain", name = "Headset")
        val obdClassic = classic(id = "obd", name = "obd ii")

        val ranked = ranker.rank(listOf(plainClassic, obdClassic), remembered = null)

        assertEquals(listOf("obd", "plain"), ranked.ids())
    }

    @Test
    fun classicObdLikeNameUsesCandidateDisplayNameWhenTargetDeviceNameIsMissing() {
        val plainClassic = classic(id = "plain", name = "Headset")
        val obdClassic = adapter(
            id = "obd",
            displayName = "ELM327",
            transportType = ObdTransportType.BluetoothClassic,
            target = ObdConnectionTarget.BluetoothClassic(
                deviceAddress = "00:11:22:33:44:55",
                deviceName = null
            ),
            confidence = ObdAdapterConfidence.Medium,
            capabilities = setOf(ObdAdapterCapability.BluetoothClassicSpp)
        )

        val ranked = ranker.rank(listOf(plainClassic, obdClassic), remembered = null)

        assertEquals(listOf("obd", "plain"), ranked.ids())
    }

    @Test
    fun blankBleProfileIdDoesNotRankAsKnownProfile() {
        val blankProfile = ble(
            id = "blank-profile",
            knownProfileId = "   "
        )
        val heuristic = ble(
            id = "heuristic",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile)
        )

        val ranked = ranker.rank(listOf(blankProfile, heuristic), remembered = null)

        assertEquals(listOf("heuristic", "blank-profile"), ranked.ids())
    }

    @Test
    fun wifiRememberedAndGatewaySourcesRankAboveStaticAndSubnetSources() {
        val subnet = wifi(id = "aaa-subnet", source = WifiCandidateSource.SubnetScan("192.168.0.20"))
        val static = wifi(id = "zzz-static", source = WifiCandidateSource.StaticKnown("192.168.0.10"))
        val gateway = wifi(id = "gateway", source = WifiCandidateSource.Gateway("192.168.0.1"))
        val rememberedSource = wifi(id = "remembered-source", source = WifiCandidateSource.Remembered)

        val ranked = ranker.rank(listOf(subnet, static, gateway, rememberedSource), remembered = null)

        assertEquals(listOf("remembered-source", "gateway", "zzz-static", "aaa-subnet"), ranked.ids())
    }

    @Test
    fun rejectedCandidatesAlwaysSinkBelowRememberedMatches() {
        val rejectedRemembered = ble(
            id = "rejected-remembered",
            peripheralId = "ble-remembered",
            probeState = ObdCandidateProbeState.Rejected(ObdError.Unknown("failed"))
        )
        val weakCandidate = ble(id = "weak")
        val remembered = fingerprint(
            transportType = ObdTransportType.BluetoothLowEnergy,
            stableId = "ble-remembered"
        )

        val ranked = ranker.rank(listOf(rejectedRemembered, weakCandidate), remembered)

        assertEquals(listOf("weak", "rejected-remembered"), ranked.ids())
    }

    @Test
    fun rejectedProbeConfirmedCandidateStillSinksToBottom() {
        val rejectedConfirmed = ble(
            id = "rejected-confirmed",
            knownProfileId = "generic_fff0",
            probeState = ObdCandidateProbeState.Rejected(ObdError.Unknown("failed"))
        )
        val weakCandidate = ble(id = "weak")

        val ranked = ranker.rank(listOf(rejectedConfirmed, weakCandidate), remembered = null)

        assertEquals(listOf("weak", "rejected-confirmed"), ranked.ids())
    }

    @Test
    fun rememberedClassicMatchIgnoresMacAddressCase() {
        val classic = classic(
            id = "remembered",
            name = "Headset",
            deviceAddress = "aa:bb:cc:dd:ee:ff"
        )
        val knownBle = ble(id = "known", knownProfileId = "generic_fff0")
        val remembered = fingerprint(
            transportType = ObdTransportType.BluetoothClassic,
            stableId = "AA:BB:CC:DD:EE:FF"
        )

        val ranked = ranker.rank(listOf(knownBle, classic), remembered)

        assertEquals(listOf("remembered", "known"), ranked.ids())
    }

    @Test
    fun equalPriorityCandidatesUseConfidenceSignalNameAndIdTieBreakers() {
        val lowSignal = ble(
            id = "b-low-signal",
            name = "B",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile),
            confidence = ObdAdapterConfidence.Medium,
            signalLevel = 30
        )
        val highSignal = ble(
            id = "a-high-signal",
            name = "A",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile),
            confidence = ObdAdapterConfidence.Medium,
            signalLevel = 80
        )
        val highConfidence = ble(
            id = "c-high-confidence",
            name = "C",
            capabilities = setOf(ObdAdapterCapability.HeuristicBleUartProfile),
            confidence = ObdAdapterConfidence.High,
            signalLevel = 10
        )

        val ranked = ranker.rank(listOf(lowSignal, highSignal, highConfidence), remembered = null)

        assertEquals(listOf("c-high-confidence", "a-high-signal", "b-low-signal"), ranked.ids())
    }

    private fun List<DiscoveredObdAdapter>.ids(): List<String> =
        map { it.id.value }

    private fun fingerprint(
        transportType: ObdTransportType,
        stableId: String
    ): AdapterFingerprint =
        AdapterFingerprint(
            transportType = transportType,
            stableId = stableId,
            displayName = null,
            bleProfileId = null,
            wifiHost = null,
            wifiPort = null,
            lastSuccessfulAt = NOW
        )

    private fun classic(
        id: String,
        name: String,
        deviceAddress: String = "00:11:22:33:44:${id.takeLast(2)}",
        confidence: ObdAdapterConfidence = ObdAdapterConfidence.Medium,
        probeState: ObdCandidateProbeState = ObdCandidateProbeState.AdvertisementOnly
    ): DiscoveredObdAdapter =
        adapter(
            id = id,
            displayName = name,
            transportType = ObdTransportType.BluetoothClassic,
            target = ObdConnectionTarget.BluetoothClassic(
                deviceAddress = deviceAddress,
                deviceName = name
            ),
            confidence = confidence,
            probeState = probeState,
            capabilities = setOf(ObdAdapterCapability.BluetoothClassicSpp)
        )

    private fun ble(
        id: String,
        peripheralId: String = id,
        name: String = id,
        knownProfileId: String? = null,
        capabilities: Set<ObdAdapterCapability> = emptySet(),
        confidence: ObdAdapterConfidence = ObdAdapterConfidence.Medium,
        signalLevel: Int? = null,
        probeState: ObdCandidateProbeState = ObdCandidateProbeState.ServiceDiscovered
    ): DiscoveredObdAdapter =
        adapter(
            id = id,
            displayName = name,
            transportType = ObdTransportType.BluetoothLowEnergy,
            target = ObdConnectionTarget.Ble(
                peripheralId = peripheralId,
                deviceName = name,
                knownProfileId = knownProfileId,
                discoveredServiceUuids = emptyList(),
                discoveredAt = NOW
            ),
            confidence = confidence,
            signalLevel = signalLevel,
            probeState = probeState,
            capabilities = capabilities
        )

    private fun wifi(
        id: String,
        source: WifiCandidateSource,
        confidence: ObdAdapterConfidence = ObdAdapterConfidence.Medium,
        probeState: ObdCandidateProbeState = ObdCandidateProbeState.AdvertisementOnly
    ): DiscoveredObdAdapter =
        adapter(
            id = id,
            displayName = id,
            transportType = ObdTransportType.WifiTcp,
            target = ObdConnectionTarget.WifiTcp(
                host = "192.168.0.${id.length}",
                port = 35000,
                source = source
            ),
            confidence = confidence,
            probeState = probeState,
            capabilities = setOf(ObdAdapterCapability.WifiTcpEndpoint)
        )

    private fun adapter(
        id: String,
        displayName: String,
        transportType: ObdTransportType,
        target: ObdConnectionTarget,
        confidence: ObdAdapterConfidence,
        signalLevel: Int? = null,
        probeState: ObdCandidateProbeState = ObdCandidateProbeState.AdvertisementOnly,
        capabilities: Set<ObdAdapterCapability> = emptySet()
    ): DiscoveredObdAdapter =
        DiscoveredObdAdapter(
            id = ObdAdapterId(id),
            displayName = displayName,
            transportType = transportType,
            target = target,
            signal = signalLevel?.let { ObdSignalStrength(rssiDbm = null, level = it) },
            confidence = confidence,
            probeState = probeState,
            capabilities = capabilities,
            lastSeenAt = NOW
        )

    private companion object {
        val NOW = Instant.parse("2026-05-31T00:00:00Z")
    }
}
