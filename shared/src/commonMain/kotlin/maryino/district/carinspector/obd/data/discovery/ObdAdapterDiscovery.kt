package maryino.district.carinspector.obd.data.discovery

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Aggregates transport-specific scanners into one internal discovery stream.
 *
 * Discovery emits possible candidates only. ELM327 probing and long-lived
 * sessions belong to validation/connection components.
 */
interface ObdAdapterDiscovery {
    fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent>
}

sealed interface ObdDiscoveryEvent {
    data class CandidateFound(val adapter: DiscoveredObdAdapter) : ObdDiscoveryEvent
    data class CandidateUpdated(val adapter: DiscoveredObdAdapter) : ObdDiscoveryEvent
    data class TransportFailed(
        val type: ObdTransportType,
        val error: ObdError
    ) : ObdDiscoveryEvent

    data class TransportFinished(val type: ObdTransportType) : ObdDiscoveryEvent
}
