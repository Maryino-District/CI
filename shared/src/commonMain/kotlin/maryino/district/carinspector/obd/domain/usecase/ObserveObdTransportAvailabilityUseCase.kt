package maryino.district.carinspector.obd.domain.usecase

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Observes platform transport availability and setup requirements. */
class ObserveObdTransportAvailabilityUseCase(
    private val repository: ObdConnectionRepository
) {
    operator fun invoke(): Flow<List<ObdTransportAvailability>> =
        repository.observeSupportedTransports()
}
