package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Confidence assigned to a discovered candidate before ELM327 probing.
 *
 * The ranking layer can sort by this value without forcing the UI to expose
 * transport-specific discovery details. Confirmed ELM327 validation is modeled
 * separately by [ObdCandidateProbeState.ProbeConfirmed].
 */
enum class ObdAdapterConfidence {
    /** Candidate is weakly inferred, for example by a loose name or subnet match. */
    Low,

    /** Candidate has a known OBD-like name, service, bonded record, or endpoint. */
    Medium,

    /** Candidate has strong discovery evidence such as a known OBD profile. */
    High
}
