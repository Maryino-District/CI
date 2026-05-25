package maryino.district.carinspector.obd.domain.usecase

import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

/** Disconnects and lets the repository close all active scan/connection resources. */
class DisconnectObdAdapterUseCase(
    private val repository: ObdConnectionRepository
) {
    suspend operator fun invoke() {
        repository.disconnect()
    }
}
