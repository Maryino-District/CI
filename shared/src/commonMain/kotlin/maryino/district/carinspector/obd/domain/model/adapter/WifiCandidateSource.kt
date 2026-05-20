package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Explains how a Wi-Fi TCP endpoint candidate was produced.
 *
 * The source is used for ranking, diagnostics, and remembered-adapter matching.
 */
sealed interface WifiCandidateSource {
    /** Endpoint came from a previously successful adapter fingerprint. */
    data object Remembered : WifiCandidateSource

    /** Endpoint was derived from the current Wi-Fi gateway address. */
    data class Gateway(val gatewayHost: String) : WifiCandidateSource

    /** Endpoint came from the static list of common OBD Wi-Fi addresses. */
    data class StaticKnown(val host: String) : WifiCandidateSource

    /** Endpoint was found by a bounded subnet scan. */
    data class SubnetScan(val host: String) : WifiCandidateSource
}

