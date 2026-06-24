package maryino.district.carinspector.obd.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.withTimeoutOrNull
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdByteChannelEvent
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdOperation
import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class AndroidBluetoothClassicSppTransportFactory internal constructor(
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val socketConnector: BluetoothClassicSocketConnector,
    private val connectTimeout: Duration,
    private val dispatcher: CoroutineDispatcher,
    private val readBufferSize: Int
) : ObdTransportFactory {
    constructor(
        adapterProvider: AndroidBluetoothAdapterProvider = DefaultAndroidBluetoothAdapterProvider,
        connectTimeout: Duration = DefaultConnectTimeout,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        readBufferSize: Int = DefaultReadBufferSize
    ) : this(
        adapterProvider = adapterProvider,
        socketConnector = RfcommBluetoothClassicSocketConnector,
        connectTimeout = connectTimeout,
        dispatcher = dispatcher,
        readBufferSize = readBufferSize
    )

    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
        if (target !is ObdConnectionTarget.BluetoothClassic) {
            return ObdResult.Failure(ObdError.UnsupportedTransport(target.transportType()))
        }

        val adapter = adapterProvider.bluetoothAdapter()
            ?: return ObdResult.Failure(ObdError.UnsupportedTransport(ObdTransportType.BluetoothClassic))

        val isEnabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            return ObdResult.Failure(permissionDenied())
        }

        if (!isEnabled) {
            return ObdResult.Failure(ObdError.BluetoothDisabled(ObdRequiredSetupAction.EnableBluetooth))
        }

        if (!BluetoothAdapter.checkBluetoothAddress(target.deviceAddress)) {
            return ObdResult.Failure(transportClosed("Invalid Bluetooth address: ${target.deviceAddress}"))
        }

        val socket = try {
            val device = adapter.getRemoteDevice(target.deviceAddress)
            socketConnector.createRfcommSocket(device, SppUuid)
        } catch (_: SecurityException) {
            return ObdResult.Failure(permissionDenied())
        } catch (throwable: Throwable) {
            return ObdResult.Failure(transportClosed(throwable.reason()))
        }

        return when (val connectResult = connectSocket(socket, target)) {
            is ObdResult.Failure -> {
                socket.closeQuietly()
                connectResult
            }

            is ObdResult.Success -> openChannel(socket)
        }
    }

    private suspend fun connectSocket(
        socket: BluetoothSocket,
        target: ObdConnectionTarget.BluetoothClassic
    ): ObdResult<Unit> = kotlinx.coroutines.coroutineScope {
        val connectResult = CompletableDeferred<Result<Unit>>()
        val connectJob = launch(dispatcher) {
            connectResult.complete(runCatching { socket.connect() })
        }

        try {
            val result = withTimeoutOrNull(connectTimeout) {
                connectResult.await()
            }

            if (result == null) {
                socket.closeQuietly()
                connectJob.cancelAndJoinQuietly()
                return@coroutineScope ObdResult.Failure(
                    ObdError.Timeout(
                        operation = ObdOperation.OpenTransport,
                        transportType = ObdTransportType.BluetoothClassic,
                        targetLabel = target.targetLabel
                    )
                )
            }

            result.fold(
                onSuccess = { ObdResult.Success(Unit) },
                onFailure = { throwable ->
                    when (throwable) {
                        is CancellationException -> throw throwable
                        is SecurityException -> ObdResult.Failure(permissionDenied())
                        else -> ObdResult.Failure(transportClosed(throwable.reason()))
                    }
                }
            )
        } catch (throwable: CancellationException) {
            socket.closeQuietly()
            connectJob.cancelAndJoinQuietly()
            throw throwable
        }
    }

    private fun openChannel(socket: BluetoothSocket): ObdResult<ObdByteChannel> {
        val inputStream = try {
            socket.inputStream
        } catch (_: SecurityException) {
            socket.closeQuietlyBlocking()
            return ObdResult.Failure(permissionDenied())
        } catch (throwable: Throwable) {
            socket.closeQuietlyBlocking()
            return ObdResult.Failure(transportClosed(throwable.reason()))
        }

        val outputStream = try {
            socket.outputStream
        } catch (_: SecurityException) {
            socket.closeQuietlyBlocking()
            return ObdResult.Failure(permissionDenied())
        } catch (throwable: Throwable) {
            socket.closeQuietlyBlocking()
            return ObdResult.Failure(transportClosed(throwable.reason()))
        }

        return ObdResult.Success(
            BluetoothClassicSppByteChannel(
                socket = socket,
                inputStream = inputStream,
                outputStream = outputStream,
                dispatcher = dispatcher,
                readBufferSize = readBufferSize.coerceAtLeast(MinReadBufferSize)
            )
        )
    }

    private suspend fun BluetoothSocket.closeQuietly() {
        withContext(NonCancellable) {
            closeQuietlyBlocking()
        }
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        withContext(NonCancellable) {
            runCatching {
                cancel()
                join()
            }
        }
    }

    private fun ObdConnectionTarget.transportType(): ObdTransportType =
        when (this) {
            is ObdConnectionTarget.BluetoothClassic -> ObdTransportType.BluetoothClassic
            is ObdConnectionTarget.Ble -> ObdTransportType.BluetoothLowEnergy
            is ObdConnectionTarget.WifiTcp -> ObdTransportType.WifiTcp
        }

    private companion object {
        val DefaultConnectTimeout: Duration = 10.seconds
        const val DefaultReadBufferSize = 4096
        const val MinReadBufferSize = 1
        val SppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

internal fun interface BluetoothClassicSocketConnector {
    fun createRfcommSocket(
        device: BluetoothDevice,
        uuid: UUID
    ): BluetoothSocket
}

private object RfcommBluetoothClassicSocketConnector : BluetoothClassicSocketConnector {
    override fun createRfcommSocket(
        device: BluetoothDevice,
        uuid: UUID
    ): BluetoothSocket =
        device.createRfcommSocketToServiceRecord(uuid)
}

private object DefaultAndroidBluetoothAdapterProvider : AndroidBluetoothAdapterProvider {
    @Suppress("DEPRECATION")
    override fun bluetoothAdapter(): BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()
}

private class BluetoothClassicSppByteChannel(
    private val socket: BluetoothSocket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val dispatcher: CoroutineDispatcher,
    private val readBufferSize: Int
) : ObdByteChannel {
    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private val events = Channel<ObdByteChannelEvent>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var closed = false
    private var closedEventSent = false

    private val readJob: Job = scope.launch {
        readLoop()
    }

    override val incoming: Flow<ObdByteChannelEvent> = events.receiveAsFlow()

    override suspend fun write(bytes: ByteArray): ObdResult<Unit> =
        writeMutex.withLock {
            if (isClosed()) {
                return@withLock ObdResult.Failure(transportClosed("Bluetooth Classic SPP channel is closed"))
            }

            try {
                withContext(dispatcher) {
                    outputStream.write(bytes)
                    outputStream.flush()
                }
                ObdResult.Success(Unit)
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (_: SecurityException) {
                val error = permissionDenied()
                closeWithEvent(error, cancelReader = true)
                ObdResult.Failure(error)
            } catch (throwable: Throwable) {
                val error = transportClosed(throwable.reason())
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
                val bytesRead = inputStream.read(buffer)
                when {
                    bytesRead < 0 -> {
                        if (!isClosed()) {
                            closeWithEvent(
                                error = transportClosed("Bluetooth Classic SPP socket closed by peer"),
                                cancelReader = false
                            )
                        }
                        return
                    }

                    bytesRead > 0 && !isClosed() -> {
                        events.send(ObdByteChannelEvent.Bytes(buffer.copyOf(bytesRead)))
                    }
                }
            }
        } catch (throwable: CancellationException) {
            // Local close cancels the reader. Cancellation is lifecycle, not a transport failure.
        } catch (_: SecurityException) {
            if (!isClosed()) {
                closeWithEvent(error = permissionDenied(), cancelReader = false)
            }
        } catch (throwable: Throwable) {
            if (!isClosed()) {
                closeWithEvent(
                    error = transportClosed(throwable.reason()),
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
            socket.closeQuietlyBlocking()
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
            if (cancelReader) {
                readJob.cancel()
            }
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
}

private fun permissionDenied(): ObdError =
    ObdError.PermissionDenied(ObdRequiredSetupAction.GrantBluetoothPermission)

private fun transportClosed(reason: String?): ObdError =
    ObdError.TransportClosed(
        transportType = ObdTransportType.BluetoothClassic,
        reason = reason
    )

private val ObdConnectionTarget.BluetoothClassic.targetLabel: String
    get() = deviceName?.takeIf { it.isNotBlank() }?.let { name -> "$name ($deviceAddress)" }
        ?: deviceAddress

private fun Throwable.reason(): String =
    message ?: this::class.simpleName ?: "Unknown Bluetooth Classic SPP failure"

private fun BluetoothSocket.closeQuietlyBlocking() {
    runCatching { close() }
}
