package maryino.district.carinspector.obd.domain.usecase

import maryino.district.carinspector.obd.domain.model.ObdAutoConnectPolicy
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Runs remembered-adapter auto-connect without silently trusting new candidates. */
class AutoConnectObdAdapterUseCase(
    private val repository: ObdConnectionRepository
) {
    suspend operator fun invoke(policy: ObdAutoConnectPolicy): ObdResult<ObdSession> =
        repository.autoConnect(policy)
}
