package maryino.district.carinspector.obd.data.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshot
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshotProvider
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class WifiTcpCandidateScannerTest {
    @Test
    fun rememberedWifiEndpointComesFirst() = runTest {
        val scanner = scanner(
            snapshot = WifiNetworkSnapshot(
                ssid = "OBD",
                bssid = null,
                gatewayHost = "192.168.4.1",
                localHost = null,
                subnetPrefix = null
            )
        )

        val candidates = scanner.scan(
            remembered = fingerprint(
                transportType = ObdTransportType.WifiTcp,
                wifiHost = "192.168.0.10",
                wifiPort = 35000
            )
        )

        val firstTarget = candidates.first().wifiTarget()
        assertEquals("192.168.0.10", firstTarget.host)
        assertEquals(35000, firstTarget.port)
        assertEquals(WifiCandidateSource.Remembered, firstTarget.source)
        assertEquals(ObdAdapterConfidence.High, candidates.first().confidence)
    }

    @Test
    fun nonWifiRememberedFingerprintIsIgnored() = runTest {
        val scanner = scanner(snapshot = null)

        val candidates = scanner.scan(
            remembered = fingerprint(
                transportType = ObdTransportType.BluetoothClassic,
                wifiHost = "192.168.0.10",
                wifiPort = 35000
            )
        )

        assertEquals(KnownStaticOrder, candidates.endpointPairs())
        assertTrue(candidates.none { it.wifiTarget().source is WifiCandidateSource.Remembered })
    }

    @Test
    fun gatewayEndpointsComeBeforeStaticEndpoints() = runTest {
        val scanner = scanner(
            snapshot = WifiNetworkSnapshot(
                ssid = null,
                bssid = null,
                gatewayHost = "192.168.4.1",
                localHost = null,
                subnetPrefix = null
            )
        )

        val candidates = scanner.scan(remembered = null)

        assertEquals(
            listOf(
                "192.168.4.1:35000",
                "192.168.4.1:23",
                "192.168.4.1:2000",
                "192.168.4.1:5000"
            ),
            candidates.take(4).endpointPairs()
        )
        assertEquals(WifiCandidateSource.Gateway("192.168.4.1"), candidates.first().wifiTarget().source)
        assertEquals(ObdAdapterConfidence.Medium, candidates.first().confidence)
    }

    @Test
    fun staticEndpointsKeepArchitectureOrderWhenSnapshotIsMissing() = runTest {
        val scanner = scanner(snapshot = null)

        val candidates = scanner.scan(remembered = null)

        assertEquals(KnownStaticOrder, candidates.endpointPairs())
    }

    @Test
    fun duplicateEndpointKeepsFirstSource() = runTest {
        val scanner = scanner(
            snapshot = WifiNetworkSnapshot(
                ssid = null,
                bssid = null,
                gatewayHost = "192.168.4.1",
                localHost = null,
                subnetPrefix = null
            )
        )

        val candidates = scanner.scan(
            remembered = fingerprint(
                transportType = ObdTransportType.WifiTcp,
                wifiHost = "192.168.4.1",
                wifiPort = 35000
            )
        )

        assertEquals(1, candidates.count { it.endpointPair() == "192.168.4.1:35000" })
        assertEquals(WifiCandidateSource.Remembered, candidates.first().wifiTarget().source)

        val staticCollision = candidates.single { it.endpointPair() == "192.168.4.1:23" }
        assertEquals(WifiCandidateSource.Gateway("192.168.4.1"), staticCollision.wifiTarget().source)
    }

    @Test
    fun blankGatewayIsIgnored() = runTest {
        val scanner = scanner(
            snapshot = WifiNetworkSnapshot(
                ssid = null,
                bssid = null,
                gatewayHost = "   ",
                localHost = null,
                subnetPrefix = null
            )
        )

        val candidates = scanner.scan(remembered = null)

        assertEquals(KnownStaticOrder, candidates.endpointPairs())
    }

    @Test
    fun candidatesUseWifiTargetCapabilityAndStableMetadata() = runTest {
        val scanner = scanner(snapshot = null)

        val candidate = scanner.scan(remembered = null).first()
        val target = candidate.wifiTarget()

        assertEquals(ObdTransportType.WifiTcp, candidate.transportType)
        assertEquals("wifi:192.168.0.10:35000", candidate.id.value)
        assertEquals("Wi-Fi OBD 192.168.0.10:35000", candidate.displayName)
        assertEquals(setOf(ObdAdapterCapability.WifiTcpEndpoint), candidate.capabilities)
        assertEquals("192.168.0.10", target.host)
        assertEquals(35000, target.port)
        assertEquals(NOW, candidate.lastSeenAt)
    }

    private fun scanner(snapshot: WifiNetworkSnapshot?): WifiTcpCandidateScanner =
        WifiTcpCandidateScanner(
            snapshotProvider = FakeWifiNetworkSnapshotProvider(snapshot),
            clock = FixedClock
        )

    private fun fingerprint(
        transportType: ObdTransportType,
        wifiHost: String?,
        wifiPort: Int?
    ): AdapterFingerprint =
        AdapterFingerprint(
            transportType = transportType,
            stableId = wifiHost?.let { host -> wifiPort?.let { port -> "$host:$port" } } ?: "stable-id",
            displayName = null,
            bleProfileId = null,
            wifiHost = wifiHost,
            wifiPort = wifiPort,
            lastSuccessfulAt = NOW
        )

    private fun DiscoveredObdAdapter.wifiTarget(): ObdConnectionTarget.WifiTcp =
        assertIs<ObdConnectionTarget.WifiTcp>(target)

    private fun DiscoveredObdAdapter.endpointPair(): String =
        wifiTarget().let { "${it.host}:${it.port}" }

    private fun List<DiscoveredObdAdapter>.endpointPairs(): List<String> =
        map { it.endpointPair() }

    private class FakeWifiNetworkSnapshotProvider(
        private val snapshot: WifiNetworkSnapshot?
    ) : WifiNetworkSnapshotProvider {
        override suspend fun snapshot(): WifiNetworkSnapshot? = snapshot
    }

    private object FixedClock : Clock {
        override fun now(): Instant = NOW
    }

    private companion object {
        val NOW = Instant.parse("2026-05-31T00:00:00Z")

        val KnownStaticOrder = listOf(
            "192.168.0.10:35000",
            "192.168.0.10:23",
            "192.168.4.1:35000",
            "192.168.4.1:23",
            "192.168.1.1:35000",
            "192.168.1.1:23",
            "192.168.10.1:35000",
            "192.168.10.1:23"
        )
    }
}
