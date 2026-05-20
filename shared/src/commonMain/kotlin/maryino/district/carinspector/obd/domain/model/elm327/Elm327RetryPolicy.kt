package maryino.district.carinspector.obd.domain.model.elm327

import kotlin.time.Duration

sealed interface Elm327RetryPolicy {
    data object None : Elm327RetryPolicy

    data class FixedDelay(
        val maxAttempts: Int,
        val delay: Duration
    ) : Elm327RetryPolicy
}
