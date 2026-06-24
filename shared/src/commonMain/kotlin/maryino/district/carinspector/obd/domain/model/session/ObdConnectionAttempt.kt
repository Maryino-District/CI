package maryino.district.carinspector.obd.domain.model.session

import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget

/**
 * Current progress snapshot for one attempt to connect to an adapter target.
 */
data class ObdConnectionAttempt(
    /** Attempt id used to correlate state updates. */
    val id: ObdConnectionAttemptId,
    /** Target currently being opened and validated. */
    val target: ObdConnectionTarget,
    /** Current high-level connection step. */
    val step: ObdConnectionStep,
    /** One-based attempt counter within the current connect flow. */
    val attemptNumber: Int,
    /** Moment when this attempt started. */
    val startedAt: Instant
)
