package maryino.district.carinspector.obd.data.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdOperation
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class WifiTcpTransport internal constructor(
    private val connectTimeout: Duration,
    private val dispatcher: CoroutineDispatcher,
    private val readBufferSize: Int,
    private val socketConnector: TcpSocketConnector
) : ObdTransportFactory {
    constructor(
        connectTimeout: Duration = DefaultConnectTimeout,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        readBufferSize: Int = DefaultReadBufferSize
    ) : this(
        connectTimeout = connectTimeout,
        dispatcher = dispatcher,
        readBufferSize = readBufferSize,
        socketConnector = KtorTcpSocketConnector
    )

    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
        if (target !is ObdConnectionTarget.WifiTcp) {
            return ObdResult.Failure(ObdError.UnsupportedTransport(target.transportType()))
        }

        val selectorManager = SelectorManager(dispatcher)
        val socket = try {
            withTimeout(connectTimeout) {
                socketConnector.connect(selectorManager, target.host, target.port)
            }
        } catch (throwable: TimeoutCancellationException) {
            selectorManager.closeQuietly()
            return ObdResult.Failure(
                ObdError.Timeout(
                    operation = ObdOperation.TcpConnect,
                    transportType = ObdTransportType.WifiTcp,
                    targetLabel = target.endpointLabel
                )
            )
        } catch (throwable: CancellationException) {
            selectorManager.closeQuietly()
            throw throwable
        } catch (throwable: Throwable) {
            selectorManager.closeQuietly()
            return ObdResult.Failure(
                ObdError.TcpEndpointUnavailable(
                    host = target.host,
                    port = target.port
                )
            )
        }

        return ObdResult.Success(
            TcpObdByteChannel(
                target = target,
                socket = socket,
                selectorManager = selectorManager,
                dispatcher = dispatcher,
                readBufferSize = readBufferSize.coerceAtLeast(MinReadBufferSize)
            )
        )
    }

    private suspend fun SelectorManager.closeQuietly() {
        withContext(NonCancellable) {
            runCatching { close() }
        }
    }

    private fun ObdConnectionTarget.transportType(): ObdTransportType =
        when (this) {
            is ObdConnectionTarget.BluetoothClassic -> ObdTransportType.BluetoothClassic
            is ObdConnectionTarget.Ble -> ObdTransportType.BluetoothLowEnergy
            is ObdConnectionTarget.WifiTcp -> ObdTransportType.WifiTcp
        }

    private companion object {
        val DefaultConnectTimeout: Duration = 1.seconds
        const val DefaultReadBufferSize = 4096
        const val MinReadBufferSize = 1
    }
}

internal fun interface TcpSocketConnector {
    suspend fun connect(
        selectorManager: SelectorManager,
        host: String,
        port: Int
    ): Socket
}

private object KtorTcpSocketConnector : TcpSocketConnector {
    override suspend fun connect(
        selectorManager: SelectorManager,
        host: String,
        port: Int
    ): Socket =
        aSocket(selectorManager).tcp().connect(host, port)
}

private class TcpObdByteChannel(
    private val target: ObdConnectionTarget.WifiTcp,
    private val socket: Socket,
    private val selectorManager: SelectorManager,
    dispatcher: CoroutineDispatcher,
    private val readBufferSize: Int
) : ObdByteChannel {
    private val stateMutex = Mutex()
    private val events = Channel<ObdByteChannelEvent>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val readChannel: ByteReadChannel = socket.openReadChannel()
    private val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
    private var closed = false
    private var closedEventSent = false

    private val readJob: Job = scope.launch {
        readLoop()
    }

    override val incoming: Flow<ObdByteChannelEvent> = events.receiveAsFlow()

    override suspend fun write(bytes: ByteArray): ObdResult<Unit> {
        if (isClosed()) {
            return ObdResult.Failure(transportClosed("TCP channel is closed"))
        }

        return try {
            writeChannel.writeFully(bytes)
            writeChannel.flush()
            ObdResult.Success(Unit)
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            val error = transportClosed(throwable.message ?: throwable::class.simpleName)
            closeWithEvent(error, cancelReader = true)
            ObdResult.Failure(error)
        }
    }

    override suspend fun close() {
        closeWithEvent(error = null, cancelReader = true)
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(readBufferSize)

        try {
            while (scope.isActive) {
                val bytesRead = readChannel.readAvailable(buffer, 0, buffer.size)
                when {
                    bytesRead < 0 -> {
                        if (!isClosed()) {
                            closeWithEvent(
                                error = transportClosed("TCP socket closed by peer"),
                                cancelReader = false
                            )
                        }
                        return
                    }

                    bytesRead > 0 -> {
                        events.send(ObdByteChannelEvent.Bytes(buffer.copyOf(bytesRead)))
                    }
                }
            }
        } catch (throwable: CancellationException) {
            // Local close cancels the reader. Cancellation is a lifecycle signal,
            // not a typed TCP transport failure.
        } catch (throwable: Throwable) {
            if (!isClosed()) {
                closeWithEvent(
                    error = transportClosed(throwable.message ?: throwable::class.simpleName),
                    cancelReader = false
                )
            }
        }
    }

    private suspend fun closeWithEvent(
        error: ObdError?,
        cancelReader: Boolean
    ) {
        if (!markClosed()) return

        withContext(NonCancellable) {
            if (cancelReader) {
                readJob.cancel()
            }
            runCatching { socket.close() }
            runCatching { selectorManager.close() }
            emitClosedOnce(error)
            if (cancelReader) {
                runCatching { readJob.cancelAndJoin() }
            }
            scope.cancel()
        }
    }

    private suspend fun markClosed(): Boolean =
        stateMutex.withLock {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }

    private suspend fun isClosed(): Boolean =
        stateMutex.withLock { closed }

    private suspend fun emitClosedOnce(error: ObdError?) {
        val shouldEmit = stateMutex.withLock {
            if (closedEventSent) {
                false
            } else {
                closedEventSent = true
                true
            }
        }

        if (shouldEmit) {
            events.send(ObdByteChannelEvent.Closed(error))
            events.close()
        }
    }

    private fun transportClosed(reason: String?): ObdError =
        ObdError.TransportClosed(
            transportType = ObdTransportType.WifiTcp,
            reason = reason
        )
}

private val ObdConnectionTarget.WifiTcp.endpointLabel: String
    get() = "$host:$port"
