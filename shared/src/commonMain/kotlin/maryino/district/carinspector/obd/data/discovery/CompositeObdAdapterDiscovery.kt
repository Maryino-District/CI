package maryino.district.carinspector.obd.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class CompositeObdAdapterDiscovery(
    private val discoveries: List<ObdAdapterDiscovery>
) : ObdAdapterDiscovery {
    override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
        discoveries.forEach { discovery ->
            discovery.scan(request).collect { event -> emit(event) }
        }
    }
}

class WifiTcpObdAdapterDiscovery(
    private val scanner: WifiTcpCandidateScanner,
    private val remembered: () -> AdapterFingerprint? = { null }
) : ObdAdapterDiscovery {
    override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
        if (ObdTransportType.WifiTcp !in request.transportTypes) return@flow

        if (request.includeWifiTcpCandidates) {
            scanner.scan(remembered()).forEach { adapter ->
                emit(ObdDiscoveryEvent.CandidateFound(adapter))
            }
        }

        emit(ObdDiscoveryEvent.TransportFinished(ObdTransportType.WifiTcp))
    }
}

class FailingObdAdapterDiscovery(
    private val type: ObdTransportType,
    private val error: ObdError,
    private val isEnabled: (ObdScanRequest) -> Boolean = { true }
) : ObdAdapterDiscovery {
    override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
        if (type !in request.transportTypes) return@flow

        if (isEnabled(request)) {
            emit(ObdDiscoveryEvent.TransportFailed(type = type, error = error))
        }

        emit(ObdDiscoveryEvent.TransportFinished(type))
    }
}
