package maryino.district.carinspector.obd.domain.model.elm327

/**
 * Runtime result of executing one command through an active ELM327 session.
 *
 * Unlike [Elm327Exchange], this keeps the original [Elm327Command], including
 * timeout and retry policy, because callers use it while command execution is
 * still part of the current control flow.
 */
data class Elm327Response(
    val command: Elm327Command,
    val raw: String,
    val normalizedLines: List<String>,
    val status: Elm327ResponseStatus
)
