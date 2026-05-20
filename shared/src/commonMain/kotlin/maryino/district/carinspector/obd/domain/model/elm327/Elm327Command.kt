package maryino.district.carinspector.obd.domain.model.elm327

import kotlin.time.Duration

/**
 * ELM327 command without a trailing carriage return.
 *
 * The protocol layer owns command termination and serial execution.
 */
data class Elm327Command(
    val value: String,
    val timeout: Duration,
    val retryPolicy: Elm327RetryPolicy = Elm327RetryPolicy.None
)
