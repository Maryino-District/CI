package maryino.district.carinspector.obd.data.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability

interface ObdTransportAvailabilityProvider {
    fun observeAvailability(): Flow<List<ObdTransportAvailability>>
}

class StaticObdTransportAvailabilityProvider(
    private val availability: List<ObdTransportAvailability>
) : ObdTransportAvailabilityProvider {
    override fun observeAvailability(): Flow<List<ObdTransportAvailability>> =
        flowOf(availability)
}
