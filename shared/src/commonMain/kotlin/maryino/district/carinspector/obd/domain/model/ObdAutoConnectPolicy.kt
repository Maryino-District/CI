package maryino.district.carinspector.obd.domain.model

import kotlin.time.Duration

/**
 * Limits and product rules for automatic connection attempts.
 *
 * Auto-connect may silently connect only to a previously confirmed adapter.
 * New candidates discovered during the scan must still wait for explicit user selection.
 */
data class ObdAutoConnectPolicy(
    val scanWindow: Duration,
    val timeoutPerCandidate: Duration,
    val maxParallelWifiAttempts: Int,
    val rememberSuccessfulAdapter: Boolean,
    val allowUnknownBleHeuristics: Boolean
)
