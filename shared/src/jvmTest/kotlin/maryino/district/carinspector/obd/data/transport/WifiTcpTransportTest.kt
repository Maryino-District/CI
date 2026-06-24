package maryino.district.carinspector.obd.data.transport

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdOperation
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class WifiTcpTransportTest {
    @Test
    fun openConnectsToServerAndExchangesBytes() = runBlocking {
        val receivedWrites = ArrayBlockingQueue<String>(1)

        FakeTcpServer { socket ->
            socket.getOutputStream().write("ELM327>".encodeToByteArray())
            socket.getOutputStream().flush()

            val buffer = ByteArray(16)
            val bytesRead = socket.getInputStream().read(buffer)
            if (bytesRead > 0) {
                receivedWrites.offer(buffer.copyOf(bytesRead).decodeToString())
            }
        }.use { server ->
            val channel = openChannel(server.port)

            val event = withTimeout(1.seconds) {
                channel.incoming.first { it is ObdByteChannelEvent.Bytes }
            }
            assertEquals("ELM327>", assertIs<ObdByteChannelEvent.Bytes>(event).value.decodeToString())

            assertEquals(ObdResult.Success(Unit), channel.write("ATI\r".encodeToByteArray()))
            assertEquals("ATI\r", receivedWrites.poll(1, TimeUnit.SECONDS))

            channel.close()
        }
    }

    @Test
    fun serverSideCloseEmitsTransportClosed() = runBlocking {
        FakeTcpServer {
            // The socket is closed by use{} immediately after accept.
        }.use { server ->
            val channel = openChannel(server.port)

            val event = withTimeout(1.seconds) {
                channel.incoming.first { it is ObdByteChannelEvent.Closed }
            }

            val error = assertIs<ObdByteChannelEvent.Closed>(event).error
            assertIs<ObdError.TransportClosed>(error)
            assertEquals(ObdTransportType.WifiTcp, error.transportType)
        }
    }

    @Test
    fun writeAfterCloseReturnsTransportClosed() = runBlocking {
        val releaseServer = CountDownLatch(1)

        FakeTcpServer {
            releaseServer.await(2, TimeUnit.SECONDS)
        }.use { server ->
            val channel = openChannel(server.port)

            channel.close()
            val result = channel.write("ATI\r".encodeToByteArray())
            releaseServer.countDown()

            val error = assertFailure(result)
            assertIs<ObdError.TransportClosed>(error)
            assertEquals(ObdTransportType.WifiTcp, error.transportType)
        }
    }

    @Test
    fun closeIsIdempotentAndEmitsSingleClosedEvent() = runBlocking {
        val releaseServer = CountDownLatch(1)

        FakeTcpServer {
            releaseServer.await(2, TimeUnit.SECONDS)
        }.use { server ->
            val channel = openChannel(server.port)

            channel.close()
            channel.close()
            releaseServer.countDown()

            val event = withTimeout(1.seconds) {
                channel.incoming.first { it is ObdByteChannelEvent.Closed }
            }

            assertEquals(null, assertIs<ObdByteChannelEvent.Closed>(event).error)
        }
    }

    @Test
    fun unavailableEndpointMapsToTcpEndpointUnavailable() = runBlocking {
        val port = unusedLocalPort()

        val result = transport().open(wifiTarget(port = port))

        assertEquals(
            ObdError.TcpEndpointUnavailable(host = LoopbackHost, port = port),
            assertFailure(result)
        )
    }

    @Test
    fun connectTimeoutMapsToTcpConnectTimeout() = runTest {
        val result = transport(
            connectTimeout = 10.milliseconds,
            socketConnector = TcpSocketConnector { _, _, _ ->
                awaitCancellation()
            }
        ).open(wifiTarget())

        assertEquals(
            ObdError.Timeout(
                operation = ObdOperation.TcpConnect,
                transportType = ObdTransportType.WifiTcp,
                targetLabel = "$LoopbackHost:35000"
            ),
            assertFailure(result)
        )
    }

    @Test
    fun connectCancellationIsRethrown() = runTest {
        val connectorStarted = CompletableDeferred<Unit>()
        val transport = transport(
            connectTimeout = 1.seconds,
            socketConnector = TcpSocketConnector { _, _, _ ->
                connectorStarted.complete(Unit)
                awaitCancellation()
            }
        )

        val deferred = async {
            transport.open(wifiTarget())
        }
        connectorStarted.await()
        deferred.cancel(CancellationException("cancelled by test"))

        assertFailsWith<CancellationException> {
            deferred.await()
        }
    }

    private suspend fun openChannel(port: Int): ObdByteChannel {
        val result = transport().open(wifiTarget(port = port))
        return assertIs<ObdResult.Success<ObdByteChannel>>(result).value
    }

    private fun transport(connectTimeout: kotlin.time.Duration = 500.milliseconds): WifiTcpTransport =
        WifiTcpTransport(
            connectTimeout = connectTimeout,
            dispatcher = Dispatchers.Default,
            readBufferSize = 64
        )

    private fun transport(
        connectTimeout: kotlin.time.Duration,
        socketConnector: TcpSocketConnector
    ): WifiTcpTransport =
        WifiTcpTransport(
            connectTimeout = connectTimeout,
            dispatcher = Dispatchers.Default,
            readBufferSize = 64,
            socketConnector = socketConnector
        )

    private fun wifiTarget(
        host: String = LoopbackHost,
        port: Int = 35000
    ): ObdConnectionTarget.WifiTcp =
        ObdConnectionTarget.WifiTcp(
            host = host,
            port = port,
            source = WifiCandidateSource.StaticKnown(host)
        )

    private fun <T> assertFailure(result: ObdResult<T>): ObdError =
        assertIs<ObdResult.Failure>(result).error

    private fun unusedLocalPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(LoopbackHost)).use { it.localPort }

    private class FakeTcpServer(
        private val onClient: (Socket) -> Unit
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName(LoopbackHost))
        private val serverFailure = CompletableFuture<Throwable?>()

        val port: Int = serverSocket.localPort

        private val serverThread = thread(
            start = true,
            isDaemon = true,
            name = "fake-obd-tcp-server-$port"
        ) {
            try {
                serverSocket.accept().use { socket ->
                    socket.tcpNoDelay = true
                    onClient(socket)
                }
                serverFailure.complete(null)
            } catch (throwable: SocketException) {
                serverFailure.complete(null)
            } catch (throwable: IOException) {
                serverFailure.complete(null)
            } catch (throwable: Throwable) {
                serverFailure.complete(throwable)
            }
        }

        override fun close() {
            runCatching { serverSocket.close() }
            serverThread.join(1000)
            serverFailure.getNow(null)?.let { throw AssertionError("Fake TCP server failed", it) }
        }
    }

    private companion object {
        const val LoopbackHost = "127.0.0.1"
    }
}
