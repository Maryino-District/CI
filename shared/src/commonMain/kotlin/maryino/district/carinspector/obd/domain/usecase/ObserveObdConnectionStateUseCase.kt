package maryino.district.carinspector.obd.domain.usecase

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionState
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Observes the repository-owned connection state stream. */
class ObserveObdConnectionStateUseCase(
    private val repository: ObdConnectionRepository
) {
    operator fun invoke(): Flow<ObdConnectionState> = repository.connectionState
}
