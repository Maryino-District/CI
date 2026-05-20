package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Normalized signal information for adapter candidates.
 *
 * RSSI is kept when available because BLE and Wi-Fi diagnostics often need the
 * raw value, while level gives UI a transport-neutral scale.
 */
data class ObdSignalStrength(
    /** Raw RSSI in dBm, usually a negative value; null for transports that cannot report it. */
    val rssiDbm: Int?,
    /** Normalized signal level from 0 (weak) to 100 (strong). */
    val level: Int
) {
    init {
        require(level in MIN_LEVEL..MAX_LEVEL) {
            "Signal level must be in $MIN_LEVEL..$MAX_LEVEL, was $level"
        }
    }

    companion object {
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 100
    }
}

