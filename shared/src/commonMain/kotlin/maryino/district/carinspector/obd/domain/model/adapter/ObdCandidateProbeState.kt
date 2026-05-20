package maryino.district.carinspector.obd.domain.model.adapter

import maryino.district.carinspector.obd.domain.model.ObdError

/**
 * Validation phase for a candidate found by scan.
 *
 * Scanners create candidates; only repository-owned validation or connection
 * attempts should move a candidate into probe states.
 */
sealed interface ObdCandidateProbeState {
    /** Candidate is based only on advertisement, name, bonded metadata, or endpoint presence. */
    data object AdvertisementOnly : ObdCandidateProbeState

    /** Candidate has transport metadata such as BLE services, but no ELM327 proof yet. */
    data object ServiceDiscovered : ObdCandidateProbeState

    /** Repository is temporarily opening the transport to verify ELM327 behavior. */
    data object ProbeInProgress : ObdCandidateProbeState

    /** Candidate responded to an ELM327 probe and can be treated as confirmed. */
    data object ProbeConfirmed : ObdCandidateProbeState

    /** Candidate failed validation with a typed domain error. */
    data class Rejected(val error: ObdError) : ObdCandidateProbeState
}

