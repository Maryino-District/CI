package maryino.district.carinspector.obd.domain.model.session

import kotlin.time.Instant

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

/**
 * ELM327 metadata collected during the successful connection handshake.
 *
 * This can grow as the protocol layer starts collecting voltage, selected
 * protocol, and raw exchanges.
 */
data class Elm327Info(
    val identity: String?,
    val voltage: String? = null,
    val selectedProtocol: String? = null,
    val rawHandshake: List<String> = emptyList()
)
