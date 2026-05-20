package maryino.district.carinspector.obd.domain.model.session

import kotlin.jvm.JvmInline

/**
 * Stable id for one connection attempt.
 *
 * The repository can use it to correlate progress events without exposing
 * platform-specific operations to presentation.
 */
@JvmInline
value class ObdConnectionAttemptId(val value: String)

