package maryino.district.carinspector.obd.domain.model.session

/**
 * Stable id for one successful OBD session.
 *
 * The id is a domain token for state updates and disconnect requests; it is not
 * a transport handle.
 */
@JvmInline
value class ObdSessionId(val value: String)

