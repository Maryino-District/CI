package maryino.district.carinspector.obd.data.transport

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult

/**
 * Serial-like byte channel opened by a concrete OBD transport.
 *
 * Implementations hide platform details such as RFCOMM streams, BLE
 * notification/write characteristics, or TCP sockets.
 */
interface ObdByteChannel {
    val incoming: Flow<ObdByteChannelEvent>

    suspend fun write(bytes: ByteArray): ObdResult<Unit>

    /**
     * Closes the opened transport resources.
     *
     * Implementations must make this operation idempotent because cancellation,
     * protocol failure, and validation cleanup can race at ownership boundaries.
     */
    suspend fun close()
}

sealed interface ObdByteChannelEvent {
    data class Bytes(val value: ByteArray) : ObdByteChannelEvent
    data class Closed(val error: ObdError?) : ObdByteChannelEvent
}
