package maryino.district.carinspector.obd.domain.model.session

import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info

/**
 * Domain fact that the app is connected to an ELM327-compatible adapter.
 *
 * The session deliberately contains no transport handle. Commands after
 * connection should go through the repository command gateway.
 */
data class ObdSession(
    val id: ObdSessionId,
    val adapter: ConnectedObdAdapter,
    val elmInfo: Elm327Info,
    val connectedAt: Instant
)
