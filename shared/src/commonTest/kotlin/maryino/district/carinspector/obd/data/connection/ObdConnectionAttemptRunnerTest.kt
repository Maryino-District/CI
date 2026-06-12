package maryino.district.carinspector.obd.data.connection

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    private fun runnerForSuccess(): ObdConnectionAttemptRunner =
        runner(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel())),
            elm327Protocol = FakeElm327Protocol(ObdResult.Success(FakeElm327ProtocolSession()))
        )

    private fun runner(
        transportFactory: ObdTransportFactory,
        elm327Protocol: Elm327Protocol
    ): ObdConnectionAttemptRunner =
        ObdConnectionAttemptRunner(
            transportFactory = transportFactory,
            elm327Protocol = elm327Protocol,
            now = { NOW }
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
        override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> =
            result
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

    private class FakeObdByteChannel : ObdByteChannel {
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
