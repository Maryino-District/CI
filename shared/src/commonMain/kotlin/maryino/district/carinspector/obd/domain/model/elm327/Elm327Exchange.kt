package maryino.district.carinspector.obd.domain.model.elm327

/**
 * Compact command/response record captured for handshake history.
 *
 * This intentionally stores only the command value and parsed response data.
 * Timeout and retry policy belong to runtime execution, so they stay in
 * [Elm327Response] and are not copied into long-lived session metadata.
 */
data class Elm327Exchange(
    val command: String,
    val rawResponse: String,
    val normalizedLines: List<String> = emptyList(),
    val status: Elm327ResponseStatus
)
