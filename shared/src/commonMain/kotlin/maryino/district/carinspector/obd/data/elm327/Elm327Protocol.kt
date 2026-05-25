package maryino.district.carinspector.obd.data.elm327

import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response

/**
 * Creates ELM327 command sessions over an already opened byte channel.
 *
 * Transport discovery, Bluetooth/GATT/TCP setup, and adapter selection stay
 * outside this layer. The protocol only knows how to speak AT commands over a
 * serial-like stream.
 */
interface Elm327Protocol {
    suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession>
}

/**
 * Runtime command executor for one opened ELM327 byte channel.
 *
 * A session owns parser state and command serialization. It is intentionally
 * lower-level than ObdSession: callers should expose ObdSession as the domain
 * connection fact, while keeping this object inside data/session management.
 */
interface Elm327ProtocolSession {
    val info: Elm327Info

    suspend fun send(command: Elm327Command): ObdResult<Elm327Response>

    suspend fun close()
}
