package maryino.district.carinspector.obd.data.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.repository.ObdCommandGateway

class ObdSessionManager {
    private val mutex = Mutex()
    private var activeSession: ActiveSession? = null

    val commandGateway: ObdCommandGateway = SessionCommandGateway()

    suspend fun activate(
        session: ObdSession,
        protocolSession: Elm327ProtocolSession
    ) {
        mutex.withLock {
            activeSession?.protocolSession?.close()
            activeSession = ActiveSession(session, protocolSession)
        }
    }

    suspend fun closeActiveSession() {
        mutex.withLock {
            activeSession?.protocolSession?.close()
            activeSession = null
        }
    }

    suspend fun currentSession(): ObdSession? =
        mutex.withLock { activeSession?.session }

    private inner class SessionCommandGateway : ObdCommandGateway {
        override suspend fun send(command: Elm327Command): ObdResult<Elm327Response> =
            mutex.withLock {
                val protocolSession = activeSession?.protocolSession
                    ?: return@withLock ObdResult.Failure(noActiveSessionError())

                protocolSession.send(command)
            }
    }

    private data class ActiveSession(
        val session: ObdSession,
        val protocolSession: Elm327ProtocolSession
    )

    private fun noActiveSessionError(): ObdError =
        ObdError.TransportClosed(
            transportType = null,
            reason = "No active OBD session"
        )
}
