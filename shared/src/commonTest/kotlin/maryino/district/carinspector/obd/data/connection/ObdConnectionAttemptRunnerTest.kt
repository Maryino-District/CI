package maryino.district.carinspector.obd.data.connection

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.data.elm327.Elm327Protocol
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdByteChannelEvent
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionStep
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class ObdConnectionAttemptRunnerTest {
    @Test
    fun connectReturnsSessionAndProtocolSessionAfterHandshake() = runTest {
        val target = wifiTarget()
        val channel = FakeObdByteChannel()
        val protocolSession = FakeElm327ProtocolSession(info = Elm327Info(identity = "ELM327 v1.5"))
        val protocol = FakeElm327Protocol(ObdResult.Success(protocolSession))
        val runner = runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(channel)),
            elm327Protocol = protocol
        )

        val result = runner.connect(target)

        val attemptResult = assertSuccess(result)
        assertSame(protocolSession, attemptResult.protocolSession)
        assertSame(channel, protocol.openedChannels.single())
        assertFalse(channel.closed)
        assertFalse(protocolSession.closed)
        assertEquals("session:192.168.0.10:35000", attemptResult.session.id.value)
        assertEquals("192.168.0.10:35000", attemptResult.session.adapter.id.value)
        assertEquals("192.168.0.10:35000", attemptResult.session.adapter.displayName)
        assertEquals(ObdTransportType.WifiTcp, attemptResult.session.adapter.transportType)
        assertEquals(target, attemptResult.session.adapter.target)
        assertEquals(protocolSession.info, attemptResult.session.elmInfo)
        assertEquals(NOW, attemptResult.session.connectedAt)
    }

    @Test
    fun transportFailureDoesNotOpenProtocol() = runTest {
        val error = ObdError.TcpEndpointUnavailable(host = "192.168.0.10", port = 35000)
        val protocol = FakeElm327Protocol(ObdResult.Success(FakeElm327ProtocolSession()))
        val runner = runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Failure(error)),
            elm327Protocol = protocol
        )

        val result = runner.connect(wifiTarget())

        assertEquals(ObdResult.Failure(error), result)
        assertTrue(protocol.openedChannels.isEmpty())
    }

    @Test
    fun handshakeFailureClosesOpenedChannelAndReturnsTypedFailure() = runTest {
        val error = ObdError.CandidateIsNotElm327(targetLabel = null)
        val channel = FakeObdByteChannel()
        val runner = runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(channel)),
            elm327Protocol = FakeElm327Protocol(ObdResult.Failure(error))
        )

        val result = runner.connect(wifiTarget())

        assertEquals(ObdResult.Failure(error), result)
        assertTrue(channel.closed)
        assertEquals(1, channel.closeCalls)
    }

    @Test
    fun cancellationAfterOpeningChannelClosesChannelAndRethrows() = runTest {
        val channel = FakeObdByteChannel()
        val cancellation = CancellationException("cancelled by test")
        val runner = runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(channel)),
            elm327Protocol = FakeElm327Protocol(cancellation = cancellation)
        )

        val thrown = assertFailsWith<CancellationException> {
            runner.connect(wifiTarget())
        }

        assertSame(cancellation, thrown)
        assertTrue(channel.closed)
        assertEquals(1, channel.closeCalls)
    }

    @Test
    fun observerFailureBeforeOpeningTransportDoesNotOpenTransport() = runTest {
        val error = IllegalStateException("observer failed before open")
        val transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel()))
        val protocol = FakeElm327Protocol(ObdResult.Success(FakeElm327ProtocolSession()))
        val runner = runner(
            transportFactory = transportFactory,
            elm327Protocol = protocol
        )

        val thrown = assertFailsWith<IllegalStateException> {
            runner.connect(
                target = wifiTarget(),
                observer = ObdConnectionAttemptObserver { step ->
                    if (step == ObdConnectionStep.OpeningTransport) throw error
                }
            )
        }

        assertSame(error, thrown)
        assertTrue(transportFactory.openedTargets.isEmpty())
        assertTrue(protocol.openedChannels.isEmpty())
    }

    @Test
    fun observerFailureAfterOpeningTransportClosesChannelAndRethrows() = runTest {
        val error = IllegalStateException("observer failed after open")
        val channel = FakeObdByteChannel()
        val protocol = FakeElm327Protocol(ObdResult.Success(FakeElm327ProtocolSession()))
        val runner = runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(channel)),
            elm327Protocol = protocol
        )

        val thrown = assertFailsWith<IllegalStateException> {
            runner.connect(
                target = wifiTarget(),
                observer = ObdConnectionAttemptObserver { step ->
                    if (step == ObdConnectionStep.SendingElmHandshake) throw error
                }
            )
        }

        assertSame(error, thrown)
        assertTrue(channel.closed)
        assertEquals(1, channel.closeCalls)
        assertTrue(protocol.openedChannels.isEmpty())
    }

    @Test
    fun targetMappingBuildsConnectedAdapterForEachTransport() = runTest {
        val cases = listOf(
            MappingCase(
                target = ObdConnectionTarget.BluetoothClassic(
                    deviceAddress = "00:11:22:33:44:55",
                    deviceName = "OBDII"
                ),
                expectedId = "00:11:22:33:44:55",
                expectedDisplayName = "OBDII",
                expectedTransportType = ObdTransportType.BluetoothClassic
            ),
            MappingCase(
                target = ObdConnectionTarget.Ble(
                    peripheralId = "ble-peripheral",
                    deviceName = " ",
                    knownProfileId = null,
                    discoveredServiceUuids = emptyList(),
                    discoveredAt = NOW
                ),
                expectedId = "ble-peripheral",
                expectedDisplayName = "ble-peripheral",
                expectedTransportType = ObdTransportType.BluetoothLowEnergy
            ),
            MappingCase(
                target = wifiTarget(host = "192.168.4.1", port = 23),
                expectedId = "192.168.4.1:23",
                expectedDisplayName = "192.168.4.1:23",
                expectedTransportType = ObdTransportType.WifiTcp
            )
        )

        cases.forEach { case ->
            val result = runnerForSuccess().connect(case.target)

            val session = assertSuccess(result).session
            assertEquals(case.expectedId, session.adapter.id.value)
            assertEquals(case.expectedDisplayName, session.adapter.displayName)
            assertEquals(case.expectedTransportType, session.adapter.transportType)
            assertEquals(case.target, session.adapter.target)
            assertEquals("session:${case.expectedId}", session.id.value)
        }
    }

    @Test
    fun wifiGroupReturnsFirstSuccessfulHandshakeAndClosesFailedSelectedChannel() = runTest {
        val selected = wifiTarget(host = "192.168.0.10", port = 35000)
        val winner = wifiTarget(host = "192.168.4.1", port = 35000)
        val selectedChannel = FakeObdByteChannel(label = "selected")
        val winnerChannel = FakeObdByteChannel(label = "winner")
        val winnerSession = FakeElm327ProtocolSession()
        val selectedError = ObdError.CandidateIsNotElm327(targetLabel = "192.168.0.10:35000")
        val runner = runner(
            transportFactory = TargetedObdTransportFactory(
                channels = mapOf(
                    selected.endpointKey to selectedChannel,
                    winner.endpointKey to winnerChannel
                )
            ),
            elm327Protocol = ChannelResultElm327Protocol(
                results = mapOf(
                    selectedChannel to ObdResult.Failure(selectedError),
                    winnerChannel to ObdResult.Success(winnerSession)
                )
            ),
            maxParallelWifiAttempts = 2
        )

        val result = runner.connect(
            WifiEndpointAttemptGroup(
                selected = selected,
                endpoints = listOf(selected, winner)
            )
        )

        val attemptResult = assertSuccess(result)
        assertEquals(winner, attemptResult.session.adapter.target)
        assertTrue(selectedChannel.closed)
        assertEquals(1, selectedChannel.closeCalls)
        assertFalse(winnerChannel.closed)
        assertFalse(winnerSession.closed)
    }

    @Test
    fun wifiGroupAllFailedReturnsSelectedEndpointFailure() = runTest {
        val selected = wifiTarget(host = "192.168.0.10", port = 35000)
        val other = wifiTarget(host = "192.168.4.1", port = 23)
        val selectedChannel = FakeObdByteChannel(label = "selected")
        val otherChannel = FakeObdByteChannel(label = "other")
        val selectedError = ObdError.TcpEndpointUnavailable(host = selected.host, port = selected.port)
        val otherError = ObdError.TcpEndpointUnavailable(host = other.host, port = other.port)
        val runner = runner(
            transportFactory = TargetedObdTransportFactory(
                channels = mapOf(
                    selected.endpointKey to selectedChannel,
                    other.endpointKey to otherChannel
                )
            ),
            elm327Protocol = ChannelResultElm327Protocol(
                results = mapOf(
                    selectedChannel to ObdResult.Failure(selectedError),
                    otherChannel to ObdResult.Failure(otherError)
                )
            ),
            maxParallelWifiAttempts = 2
        )

        val result = runner.connect(
            WifiEndpointAttemptGroup(
                selected = selected,
                endpoints = listOf(other, selected)
            )
        )

        assertEquals(ObdResult.Failure(selectedError), result)
        assertTrue(selectedChannel.closed)
        assertTrue(otherChannel.closed)
    }

    @Test
    fun wifiGroupCancellationClosesOpenedChannelsAndRethrows() = runTest {
        val selected = wifiTarget(host = "192.168.0.10", port = 35000)
        val other = wifiTarget(host = "192.168.4.1", port = 35000)
        val selectedChannel = FakeObdByteChannel(label = "selected")
        val otherChannel = FakeObdByteChannel(label = "other")
        val protocol = HangingElm327Protocol(expectedOpenSessions = 2)
        val runner = runner(
            transportFactory = TargetedObdTransportFactory(
                channels = mapOf(
                    selected.endpointKey to selectedChannel,
                    other.endpointKey to otherChannel
                )
            ),
            elm327Protocol = protocol,
            maxParallelWifiAttempts = 2
        )
        var thrown: Throwable? = null

        val job = launch {
            try {
                runner.connect(
                    WifiEndpointAttemptGroup(
                        selected = selected,
                        endpoints = listOf(selected, other)
                    )
                )
            } catch (throwable: Throwable) {
                thrown = throwable
            }
        }
        protocol.allOpenSessionsStarted.await()

        job.cancel()
        job.join()

        assertTrue(thrown is CancellationException)
        assertTrue(selectedChannel.closed)
        assertTrue(otherChannel.closed)
    }

    @Test
    fun wifiGroupDoesNotExceedConfiguredParallelAttemptLimit() = runTest {
        val endpoints = listOf(
            wifiTarget(host = "192.168.0.10", port = 35000),
            wifiTarget(host = "192.168.0.10", port = 23),
            wifiTarget(host = "192.168.4.1", port = 35000),
            wifiTarget(host = "192.168.4.1", port = 23)
        )
        val channels = endpoints.associate { endpoint ->
            endpoint.endpointKey to FakeObdByteChannel(label = endpoint.endpointKey)
        }
        val protocol = ReleasableFailureElm327Protocol(expectedInitialOpenSessions = 2)
        val runner = runner(
            transportFactory = TargetedObdTransportFactory(channels = channels),
            elm327Protocol = protocol,
            maxParallelWifiAttempts = 2
        )

        val job = launch {
            runner.connect(
                WifiEndpointAttemptGroup(
                    selected = endpoints.first(),
                    endpoints = endpoints
                )
            )
        }
        protocol.initialOpenSessionsStarted.await()

        assertEquals(2, protocol.maxActiveOpenSessions)

        protocol.releaseFailures.complete(Unit)
        job.join()

        assertEquals(2, protocol.maxActiveOpenSessions)
    }

    private fun runnerForSuccess(): ObdConnectionAttemptRunner =
        runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel())),
            elm327Protocol = FakeElm327Protocol(ObdResult.Success(FakeElm327ProtocolSession()))
        )

    private fun runner(
        transportFactory: ObdTransportFactory,
        elm327Protocol: Elm327Protocol,
        maxParallelWifiAttempts: Int = 4
    ): ObdConnectionAttemptRunner =
        ObdConnectionAttemptRunner(
            transportFactory = transportFactory,
            elm327Protocol = elm327Protocol,
            now = { NOW },
            maxParallelWifiAttempts = maxParallelWifiAttempts
        )

    private fun wifiTarget(
        host: String = "192.168.0.10",
        port: Int = 35000
    ): ObdConnectionTarget.WifiTcp =
        ObdConnectionTarget.WifiTcp(
            host = host,
            port = port,
            source = WifiCandidateSource.StaticKnown(host)
        )

    private fun assertSuccess(
        result: ObdResult<ObdConnectionAttemptResult>
    ): ObdConnectionAttemptResult {
        assertTrue(result is ObdResult.Success)
        return result.value
    }

    private data class MappingCase(
        val target: ObdConnectionTarget,
        val expectedId: String,
        val expectedDisplayName: String,
        val expectedTransportType: ObdTransportType
    )

    private class FakeObdTransportFactory(
        private val result: ObdResult<ObdByteChannel>
    ) : ObdTransportFactory {
        val openedTargets = mutableListOf<ObdConnectionTarget>()

        override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
            openedTargets += target
            return result
        }
    }

    private class FakeElm327Protocol(
        private val result: ObdResult<Elm327ProtocolSession>? = null,
        private val cancellation: CancellationException? = null
    ) : Elm327Protocol {
        val openedChannels = mutableListOf<ObdByteChannel>()

        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
            openedChannels += channel
            cancellation?.let { throw it }
            return result ?: error("FakeElm327Protocol result was not configured")
        }
    }

    private class ChannelResultElm327Protocol(
        private val results: Map<ObdByteChannel, ObdResult<Elm327ProtocolSession>>
    ) : Elm327Protocol {
        val openedChannels = mutableListOf<ObdByteChannel>()

        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
            openedChannels += channel
            return results[channel] ?: error("No protocol result configured for $channel")
        }
    }

    private class HangingElm327Protocol(
        private val expectedOpenSessions: Int
    ) : Elm327Protocol {
        private var openSessionCount = 0
        val allOpenSessionsStarted = CompletableDeferred<Unit>()

        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
            openSessionCount += 1
            if (openSessionCount == expectedOpenSessions) {
                allOpenSessionsStarted.complete(Unit)
            }
            awaitCancellation()
        }
    }

    private class ReleasableFailureElm327Protocol(
        private val expectedInitialOpenSessions: Int
    ) : Elm327Protocol {
        val initialOpenSessionsStarted = CompletableDeferred<Unit>()
        val releaseFailures = CompletableDeferred<Unit>()
        private var activeOpenSessions = 0
        private var openSessionCount = 0
        var maxActiveOpenSessions = 0
            private set

        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
            activeOpenSessions += 1
            openSessionCount += 1
            maxActiveOpenSessions = maxOf(maxActiveOpenSessions, activeOpenSessions)
            if (openSessionCount == expectedInitialOpenSessions) {
                initialOpenSessionsStarted.complete(Unit)
            }

            return try {
                releaseFailures.await()
                ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null))
            } finally {
                activeOpenSessions -= 1
            }
        }
    }

    private class TargetedObdTransportFactory(
        private val channels: Map<String, ObdByteChannel>,
        private val failures: Map<String, ObdError> = emptyMap()
    ) : ObdTransportFactory {
        val openedTargets = mutableListOf<ObdConnectionTarget>()

        override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
            openedTargets += target
            val endpoint = target as? ObdConnectionTarget.WifiTcp
                ?: error("TargetedObdTransportFactory only supports Wi-Fi targets")
            failures[endpoint.endpointKey]?.let { error -> return ObdResult.Failure(error) }
            return ObdResult.Success(
                channels[endpoint.endpointKey]
                    ?: error("No channel configured for ${endpoint.endpointKey}")
            )
        }
    }

    private class FakeObdByteChannel(
        private val label: String = "channel"
    ) : ObdByteChannel {
        override val incoming: Flow<ObdByteChannelEvent> = emptyFlow()
        var closeCalls = 0
            private set
        val closed: Boolean
            get() = closeCalls > 0

        override suspend fun write(bytes: ByteArray): ObdResult<Unit> =
            ObdResult.Success(Unit)

        override suspend fun close() {
            closeCalls += 1
        }

        override fun toString(): String = label
    }

    private class FakeElm327ProtocolSession(
        override val info: Elm327Info = Elm327Info(identity = "ELM327 v1.5")
    ) : Elm327ProtocolSession {
        var closed = false
            private set

        override suspend fun send(command: Elm327Command): ObdResult<Elm327Response> =
            error("FakeElm327ProtocolSession.send is not used by these tests")

        override suspend fun close() {
            closed = true
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-01T00:00:00Z")
    }
}

private val ObdConnectionTarget.WifiTcp.endpointKey: String
    get() = "$host:$port"
