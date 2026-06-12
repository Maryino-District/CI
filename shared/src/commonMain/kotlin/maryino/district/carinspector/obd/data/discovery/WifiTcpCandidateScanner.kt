package maryino.district.carinspector.obd.data.discovery

import kotlin.time.Clock
import kotlin.time.Instant
import maryino.district.carinspector.obd.data.wifi.WifiNetworkSnapshotProvider
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class WifiTcpCandidateScanner(
    private val snapshotProvider: WifiNetworkSnapshotProvider,
    private val clock: Clock = Clock.System
) {
    suspend fun scan(remembered: AdapterFingerprint?): List<DiscoveredObdAdapter> {
        val endpoints = LinkedHashMap<EndpointKey, EndpointCandidate>()

        remembered?.toRememberedEndpoint()?.let { endpoints.putFirst(it) }

        val gatewayHost = snapshotProvider.snapshot()?.gatewayHost?.takeIf { it.isNotBlank() }
        gatewayHost?.let { host ->
            GatewayPorts.forEach { port ->
                val endpoint = EndpointCandidate(
                    host = host,
                    port = port,
                    source = WifiCandidateSource.Gateway(host),
                    confidence = ObdAdapterConfidence.Medium
                )
                endpoints.putFirst(endpoint)
            }
        }

        KnownStaticEndpoints.forEach { endpoint ->
            endpoints.putFirst(endpoint)
        }

        val now = clock.now()
        return endpoints.values.map { it.toAdapter(now) }
    }

    private fun AdapterFingerprint.toRememberedEndpoint(): EndpointCandidate? {
        if (transportType != ObdTransportType.WifiTcp) return null

        val host = wifiHost?.takeIf { it.isNotBlank() } ?: return null
        val port = wifiPort ?: return null

        return EndpointCandidate(
            host = host,
            port = port,
            source = WifiCandidateSource.Remembered,
            confidence = ObdAdapterConfidence.High
        )
    }

    private fun EndpointCandidate.toAdapter(now: Instant): DiscoveredObdAdapter =
        DiscoveredObdAdapter(
            id = ObdAdapterId("wifi:$host:$port"),
            displayName = "Wi-Fi OBD $host:$port",
            transportType = ObdTransportType.WifiTcp,
            target = ObdConnectionTarget.WifiTcp(
                host = host,
                port = port,
                source = source
            ),
            signal = null,
            confidence = confidence,
            probeState = ObdCandidateProbeState.AdvertisementOnly,
            capabilities = setOf(ObdAdapterCapability.WifiTcpEndpoint),
            lastSeenAt = now
        )

    private fun MutableMap<EndpointKey, EndpointCandidate>.putFirst(endpoint: EndpointCandidate) {
        if (endpoint.key !in this) {
            this[endpoint.key] = endpoint
        }
    }

    private data class EndpointCandidate(
        val host: String,
        val port: Int,
        val source: WifiCandidateSource,
        val confidence: ObdAdapterConfidence
    ) {
        val key: EndpointKey = EndpointKey(host, port)
    }

    private data class EndpointKey(
        val host: String,
        val port: Int
    )

    private companion object {
        val GatewayPorts = listOf(35000, 23, 2000, 5000)

        val KnownStaticEndpoints = listOf(
            staticEndpoint("192.168.0.10", 35000),
            staticEndpoint("192.168.0.10", 23),
            staticEndpoint("192.168.4.1", 35000),
            staticEndpoint("192.168.4.1", 23),
            staticEndpoint("192.168.1.1", 35000),
            staticEndpoint("192.168.1.1", 23),
            staticEndpoint("192.168.10.1", 35000),
            staticEndpoint("192.168.10.1", 23)
        )

        fun staticEndpoint(host: String, port: Int): EndpointCandidate =
            EndpointCandidate(
                host = host,
                port = port,
                source = WifiCandidateSource.StaticKnown(host),
                confidence = ObdAdapterConfidence.Medium
            )
    }
}
