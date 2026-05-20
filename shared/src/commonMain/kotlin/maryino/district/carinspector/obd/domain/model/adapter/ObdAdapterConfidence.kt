package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Confidence assigned to a discovered candidate before or after ELM327 probing.
 *
 * The ranking layer can sort by this value without forcing the UI to expose
 * transport-specific discovery details.
 */
enum class ObdAdapterConfidence {
    /** Candidate is weakly inferred, for example by a loose name or subnet match. */
    Low,

    /** Candidate has a known OBD-like name, service, bonded record, or endpoint. */
    Medium,

    /** Candidate matches a known OBD profile or remembered adapter fingerprint. */
    High,

    /** Candidate has already answered as an ELM327-compatible adapter. */
    Confirmed
}

