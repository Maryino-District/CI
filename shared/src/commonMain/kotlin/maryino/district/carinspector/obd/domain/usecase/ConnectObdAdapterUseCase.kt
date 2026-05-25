package maryino.district.carinspector.obd.domain.usecase

import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Connects to a selected candidate and verifies it with ELM327 handshake. */
class ConnectObdAdapterUseCase(
    private val repository: ObdConnectionRepository
) {
    suspend operator fun invoke(target: ObdConnectionTarget): ObdResult<ObdSession> =
        repository.connect(target)
}
