package maryino.district.carinspector.obd.domain.model.elm327

/**
 * ELM327 metadata collected during a successful connection handshake.
 */
data class Elm327Info(
    val identity: String?,
    val voltage: String? = null,
    val selectedProtocol: String? = null,
    /**
     * Exchanges captured only during connection initialization.
     *
     * Runtime diagnostic or PID commands are returned as [Elm327Response] and
     * are not accumulated in this session metadata.
     */
    val rawHandshake: List<Elm327Exchange> = emptyList()
)
