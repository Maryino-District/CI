package maryino.district.carinspector.obd.data.connection

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import maryino.district.carinspector.obd.data.elm327.Elm327Protocol
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.session.ConnectedObdAdapter
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.model.session.ObdSessionId
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

data class ObdConnectionAttemptResult(
    val session: ObdSession,
    val protocolSession: Elm327ProtocolSession
)

class ObdConnectionAttemptRunner(
    private val transportFactory: ObdTransportFactory,
    private val elm327Protocol: Elm327Protocol,
    private val now: () -> Instant = { Clock.System.now() }
) {
    suspend fun connect(target: ObdConnectionTarget): ObdResult<ObdConnectionAttemptResult> {
        val channel = when (val openResult = transportFactory.open(target)) {
            is ObdResult.Failure -> return openResult
            is ObdResult.Success -> openResult.value
        }

        return try {
            when (val sessionResult = elm327Protocol.openSession(channel)) {
                is ObdResult.Failure -> {
                    channel.closeQuietly()
                    sessionResult
                }

                is ObdResult.Success -> {
                    val protocolSession = sessionResult.value
                    val adapter = target.toConnectedAdapter()
                    ObdResult.Success(
                        ObdConnectionAttemptResult(
                            session = ObdSession(
                                id = ObdSessionId("session:${adapter.id.value}"),
                                adapter = adapter,
                                elmInfo = protocolSession.info,
                                connectedAt = now()
                            ),
                            protocolSession = protocolSession
                        )
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            channel.closeQuietly()
            throw cancellation
        }
    }

    private suspend fun ObdByteChannel.closeQuietly() {
        withContext(NonCancellable) {
            runCatching { close() }
        }
    }

    private fun ObdConnectionTarget.toConnectedAdapter(): ConnectedObdAdapter {
        val identity = toAdapterIdentity()
        return ConnectedObdAdapter(
            id = ObdAdapterId(identity.id),
            displayName = identity.displayName,
            transportType = identity.transportType,
            target = this
        )
    }

    private fun ObdConnectionTarget.toAdapterIdentity(): AdapterIdentity =
        when (this) {
            is ObdConnectionTarget.BluetoothClassic -> AdapterIdentity(
                id = deviceAddress,
                displayName = deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress,
                transportType = ObdTransportType.BluetoothClassic
            )

            is ObdConnectionTarget.Ble -> AdapterIdentity(
                id = peripheralId,
                displayName = deviceName?.takeIf { it.isNotBlank() } ?: peripheralId,
                transportType = ObdTransportType.BluetoothLowEnergy
            )

            is ObdConnectionTarget.WifiTcp -> {
                val endpoint = "$host:$port"
                AdapterIdentity(
                    id = endpoint,
                    displayName = endpoint,
                    transportType = ObdTransportType.WifiTcp
                )
            }
        }

    private data class AdapterIdentity(
        val id: String,
        val displayName: String,
        val transportType: ObdTransportType
    )
}
