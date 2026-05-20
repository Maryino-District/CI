package maryino.district.carinspector.obd.domain.model.transport

import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction

/**
 * Combines transport support state with an optional user-facing recovery action.
 *
 * This lets the repository expose one availability stream while keeping the UI
 * free from platform checks and low-level permission logic.
 */
data class ObdTransportAvailability(
    val type: ObdTransportType,
    val status: ObdTransportStatus,
    val userAction: ObdRequiredSetupAction?
)
