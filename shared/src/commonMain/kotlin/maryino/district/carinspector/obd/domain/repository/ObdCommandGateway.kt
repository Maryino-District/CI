package maryino.district.carinspector.obd.domain.repository

import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response

/**
 * Transport-neutral command entry point for features that run after connection.
 *
 * The gateway is always injectable and callable. Implementations return a typed
 * failure when there is no active ELM327 session instead of exposing nullable
 * transport handles to diagnostics or metrics features.
 */
interface ObdCommandGateway {
    /** Sends one ELM327 command through the active session command queue. */
    suspend fun send(command: Elm327Command): ObdResult<Elm327Response>
}
