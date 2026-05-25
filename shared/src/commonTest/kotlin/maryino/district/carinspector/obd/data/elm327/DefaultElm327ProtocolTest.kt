package maryino.district.carinspector.obd.data.elm327

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdByteChannelEvent
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdOperation
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.elm327.Elm327ResponseStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class DefaultElm327ProtocolTest {
    @Test
    fun openSessionWritesHandshakeCommandsInOrder() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses())

        val result = DefaultElm327Protocol().openSession(channel)

        assertIs<ObdResult.Success<Elm327ProtocolSession>>(result)
        assertEquals(
            listOf("ATZ\r", "ATE0\r", "ATL0\r", "ATS0\r", "ATH0\r", "ATSP0\r", "ATI\r"),
            channel.writes
        )
    }

    @Test
    fun openSessionReturnsElmInfoFromHandshake() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses(identity = "ELM327 v1.5>"))

        val session = assertIs<ObdResult.Success<Elm327ProtocolSession>>(
            DefaultElm327Protocol().openSession(channel)
        ).value

        assertEquals("ELM327 v1.5", session.info.identity)
        assertEquals(
            listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATI"),
            session.info.rawHandshake.map { it.command }
        )
        assertTrue(session.info.rawHandshake.all { it.status is Elm327ResponseStatus.Ok })
    }

    @Test
    fun openSessionAcceptsBroadAdapterIdentityMarkers() = runTest {
        val identities = listOf(
            "OBDLink CX>",
            "STN2120 v4.2.1>",
            "Vgate iCar Pro>",
            "V-LINK v2.2>",
            "Viecar BLE>",
            "KONNWEI KW902>",
            "Carista>",
            "LELink>",
            "Veepeak BLE+>",
            "READY>",
            "READY 1.0>",
            "v1.5>"
        )

        identities.forEach { identity ->
            val result = DefaultElm327Protocol().openSession(FakeObdByteChannel(handshakeResponses(identity = identity)))

            assertIs<ObdResult.Success<Elm327ProtocolSession>>(result, "identity=$identity")
        }
    }

    @Test
    fun openSessionRejectsPromptOnlyNonObdIdentity() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses(identity = "hello>"))

        val result = DefaultElm327Protocol().openSession(channel)

        assertEquals(ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null)), result)
        assertTrue(channel.closed)
    }

    @Test
    fun openSessionRejectsNetworkAndGenericVersionBanners() = runTest {
        val identities = listOf(
            "HTTP/1.1>",
            "login:>",
            "{\"version\":\"1.0\"}>"
        )

        identities.forEach { identity ->
            val channel = FakeObdByteChannel(handshakeResponses(identity = identity))

            val result = DefaultElm327Protocol().openSession(channel)

            assertEquals(
                ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null)),
                result,
                "identity=$identity"
            )
            assertTrue(channel.closed, "identity=$identity")
        }
    }

    @Test
    fun openSessionAcceptsStandaloneVersionOnlyWithSetupEvidence() = runTest {
        val session = assertIs<ObdResult.Success<Elm327ProtocolSession>>(
            DefaultElm327Protocol().openSession(FakeObdByteChannel(handshakeResponses(identity = "v1.5>")))
        ).value

        assertEquals("v1.5", session.info.identity)
    }

    @Test
    fun openSessionRejectsStandaloneVersionWithoutSetupEvidence() = runTest {
        val channel = FakeObdByteChannel(
            listOf(
                listOf("hello>"),
                listOf("done>"),
                listOf("done>"),
                listOf("done>"),
                listOf("done>"),
                listOf("done>"),
                listOf("v1.5>")
            )
        )

        val result = DefaultElm327Protocol().openSession(channel)

        assertEquals(ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null)), result)
        assertTrue(channel.closed)
    }

    @Test
    fun openSessionClosesChannelWhenHandshakeFails() = runTest {
        val channel = FakeObdByteChannel(
            handshakeResponses().take(3) + listOf(listOf("NO DATA>"))
        )

        val result = DefaultElm327Protocol().openSession(channel)

        assertEquals(ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null)), result)
        assertTrue(channel.closed)
    }

    @Test
    fun openSessionReturnsTimeoutWhenHandshakePromptDoesNotArrive() = runTest {
        val channel = FakeObdByteChannel(
            handshakeResponses().take(2) + listOf(emptyList())
        )

        val result = DefaultElm327Protocol().openSession(channel)

        assertEquals(
            ObdResult.Failure(
                ObdError.Timeout(
                    operation = ObdOperation.ElmHandshake,
                    transportType = null,
                    targetLabel = null
                )
            ),
            result
        )
        assertTrue(channel.closed)
    }

    @Test
    fun sendWritesCommandWithCarriageReturn() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses() + listOf(listOf("OK>")))
        val session = openSession(channel)

        session.send(Elm327Command(value = "ATE0", timeout = 100.milliseconds))

        assertEquals("ATE0\r", channel.writes.last())
    }

    @Test
    fun sendReadsFragmentedBytesUntilPrompt() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses() + listOf(listOf("41 0", "C 1A F8", ">")))
        val session = openSession(channel)

        val result = session.send(Elm327Command(value = "010C", timeout = 100.milliseconds))

        val response = assertResponse(result)
        assertIs<Elm327ResponseStatus.Ok>(response.status)
        assertEquals("41 0C 1A F8>", response.raw)
        assertEquals(listOf("41 0C 1A F8"), response.normalizedLines)
    }

    @Test
    fun sendRemovesEchoAndNormalizesWhitespace() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses() + listOf(listOf("ATI\r\r\nELM327 v1.5\r\n>")))
        val session = openSession(channel)

        val result = session.send(Elm327Command(value = "ATI", timeout = 100.milliseconds))

        val response = assertResponse(result)
        assertEquals(listOf("ELM327 v1.5"), response.normalizedLines)
    }

    @Test
    fun sendMapsKnownElmStatuses() = runTest {
        val cases = listOf(
            "NO DATA>" to Elm327ResponseStatus.NoData,
            "UNABLE TO CONNECT>" to Elm327ResponseStatus.UnableToConnect,
            "?>" to Elm327ResponseStatus.UnknownCommand,
            "BUFFULL>" to Elm327ResponseStatus.BusyProcessing("BUFFULL"),
            "BUS BUSY>" to Elm327ResponseStatus.BusyProcessing("BUS BUSY"),
            "FB ERROR>" to Elm327ResponseStatus.BusyProcessing("FB ERROR"),
            "DATA ERROR>" to Elm327ResponseStatus.BusyProcessing("DATA ERROR")
        )

        cases.forEach { (rawResponse, expectedStatus) ->
            val result = openSession(FakeObdByteChannel(handshakeResponses() + listOf(listOf(rawResponse))))
                .send(Elm327Command(value = "010C", timeout = 100.milliseconds))

            assertEquals(expectedStatus, assertResponse(result).status)
        }
    }

    @Test
    fun sendReturnsTimeoutStatusWhenPromptDoesNotArrive() = runTest {
        val channel = FakeObdByteChannel(handshakeResponses() + listOf(emptyList()))
        val session = openSession(channel)

        val result = session.send(Elm327Command(value = "ATI", timeout = 10.milliseconds))

        val response = assertResponse(result)
        assertIs<Elm327ResponseStatus.Timeout>(response.status)
        assertEquals(emptyList(), response.normalizedLines)
    }

    @Test
    fun sendReturnsFailureWhenTransportCloses() = runTest {
        val error = ObdError.TransportClosed(transportType = null, reason = "closed by test")
        val channel = FakeObdByteChannel(
            responses = handshakeResponses() + listOf(emptyList()),
            closeAfterWrite = error,
            closeOnWriteNumber = 8
        )
        val session = openSession(channel)

        val result = session.send(Elm327Command(value = "ATI", timeout = 100.milliseconds))

        assertEquals(ObdResult.Failure(error), result)
    }

    private suspend fun openSession(channel: ObdByteChannel): Elm327ProtocolSession {
        val result = DefaultElm327Protocol().openSession(channel)
        return assertIs<ObdResult.Success<Elm327ProtocolSession>>(result).value
    }

    private fun assertResponse(result: ObdResult<Elm327Response>): Elm327Response =
        assertIs<ObdResult.Success<Elm327Response>>(result).value

    private fun handshakeResponses(identity: String = "ELM327 v1.5>"): List<List<String>> =
        listOf(
            listOf("ELM327 v1.5>"),
            listOf("OK>"),
            listOf("OK>"),
            listOf("OK>"),
            listOf("OK>"),
            listOf("OK>"),
            listOf(identity)
        )

    private class FakeObdByteChannel(
        responses: List<List<String>>,
        private val closeAfterWrite: ObdError? = null,
        private val closeOnWriteNumber: Int? = null
    ) : ObdByteChannel {
        private val events = Channel<ObdByteChannelEvent>(capacity = Channel.UNLIMITED)
        private val pendingResponses = ArrayDeque(responses)
        private var writeCount = 0

        val writes = mutableListOf<String>()
        var closed = false
            private set

        override val incoming: Flow<ObdByteChannelEvent> = events.receiveAsFlow()

        override suspend fun write(bytes: ByteArray): ObdResult<Unit> {
            writeCount += 1
            writes += bytes.decodeToString()
            val responses = pendingResponses.removeFirstOrNull().orEmpty()
            responses.forEach { response ->
                events.send(ObdByteChannelEvent.Bytes(response.encodeToByteArray()))
            }
            if (closeAfterWrite != null && writeCount == closeOnWriteNumber) {
                events.send(ObdByteChannelEvent.Closed(closeAfterWrite))
            }
            return ObdResult.Success(Unit)
        }

        override suspend fun close() {
            closed = true
            events.close()
        }
    }
}
