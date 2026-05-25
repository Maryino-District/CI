package maryino.district.carinspector.obd.domain.usecase

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.scan.ObdScanEvent
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Starts a transport-neutral OBD adapter discovery pass. */
class ScanObdAdaptersUseCase(
    private val repository: ObdConnectionRepository
) {
    operator fun invoke(request: ObdScanRequest): Flow<ObdScanEvent> =
        repository.scan(request)
}
