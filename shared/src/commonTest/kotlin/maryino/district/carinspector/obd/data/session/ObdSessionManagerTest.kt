package maryino.district.carinspector.obd.data.session

import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.elm327.Elm327ResponseStatus
import maryino.district.carinspector.obd.domain.model.session.ConnectedObdAdapter
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.model.session.ObdSessionId
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class ObdSessionManagerTest {
    @Test
    fun sendWithoutActiveSessionReturnsTypedFailure() = runTest {
        val manager = ObdSessionManager()

        val result = manager.commandGateway.send(command())

        assertEquals(
            ObdResult.Failure(
                ObdError.TransportClosed(
                    transportType = null,
                    reason = "No active OBD session"
                )
            ),
            result
        )
    }

    @Test
    fun sendWithActiveSessionDelegatesToProtocolSession() = runTest {
        val manager = ObdSessionManager()
        val protocolSession = FakeElm327ProtocolSession()
        manager.activate(session(), protocolSession)

        val result = manager.commandGateway.send(command("010C"))

        assertEquals(listOf(command("010C")), protocolSession.commands)
        assertEquals(ObdResult.Success(response(command("010C"))), result)
        assertEquals(session(), manager.currentSession())
    }

    @Test
    fun closeActiveSessionClosesProtocolSessionAndGatewayReturnsFailure() = runTest {
        val manager = ObdSessionManager()
        val protocolSession = FakeElm327ProtocolSession()
        manager.activate(session(), protocolSession)

        manager.closeActiveSession()
        val result = manager.commandGateway.send(command())

        assertTrue(protocolSession.closed)
        assertNull(manager.currentSession())
        assertEquals(
            ObdResult.Failure(
                ObdError.TransportClosed(
                    transportType = null,
                    reason = "No active OBD session"
                )
            ),
            result
        )
    }

    @Test
    fun activateClosesPreviousProtocolSession() = runTest {
        val manager = ObdSessionManager()
        val firstProtocolSession = FakeElm327ProtocolSession()
        val secondProtocolSession = FakeElm327ProtocolSession()

        manager.activate(session(id = "first"), firstProtocolSession)
        manager.activate(session(id = "second"), secondProtocolSession)

        assertTrue(firstProtocolSession.closed)
        assertEquals(session(id = "second"), manager.currentSession())
        assertEquals(ObdResult.Success(response(command())), manager.commandGateway.send(command()))
        assertTrue(secondProtocolSession.commands.isNotEmpty())
    }

    private fun command(value: String = "ATI"): Elm327Command =
        Elm327Command(value = value, timeout = 100.milliseconds)

    private fun response(command: Elm327Command): Elm327Response =
        Elm327Response(
            command = command,
            raw = "OK>",
            normalizedLines = listOf("OK"),
            status = Elm327ResponseStatus.Ok
        )

    private fun session(id: String = "session"): ObdSession =
        ObdSession(
            id = ObdSessionId(id),
            adapter = ConnectedObdAdapter(
                id = ObdAdapterId("adapter"),
                displayName = "OBD Adapter",
                transportType = ObdTransportType.WifiTcp,
                target = ObdConnectionTarget.WifiTcp(
                    host = "192.168.0.10",
                    port = 35000,
                    source = WifiCandidateSource.StaticKnown("192.168.0.10")
                )
            ),
            elmInfo = Elm327Info(identity = "ELM327 v1.5"),
            connectedAt = Instant.parse("2026-05-28T00:00:00Z")
        )

    private class FakeElm327ProtocolSession : Elm327ProtocolSession {
        override val info: Elm327Info = Elm327Info(identity = "ELM327 v1.5")
        val commands = mutableListOf<Elm327Command>()
        var closed = false
            private set

        override suspend fun send(command: Elm327Command): ObdResult<Elm327Response> {
            commands += command
            return ObdResult.Success(response(command))
        }

        override suspend fun close() {
            closed = true
        }

        private fun response(command: Elm327Command): Elm327Response =
            Elm327Response(
                command = command,
                raw = "OK>",
                normalizedLines = listOf("OK"),
                status = Elm327ResponseStatus.Ok
            )
    }
}
