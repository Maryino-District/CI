package maryino.district.carinspector.obd.data.connection

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import maryino.district.carinspector.obd.data.elm327.Elm327Protocol
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.session.ConnectedObdAdapter
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.model.session.ObdSessionId
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionStep
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

data class ObdConnectionAttemptResult(
    val session: ObdSession,
    val protocolSession: Elm327ProtocolSession
)

internal data class WifiEndpointAttemptGroup(
    val selected: ObdConnectionTarget.WifiTcp,
    val endpoints: List<ObdConnectionTarget.WifiTcp>
) {
    val orderedEndpoints: List<ObdConnectionTarget.WifiTcp> =
        buildList {
            add(selected)
            endpoints.forEach { endpoint ->
                if (none { existing -> existing.sameEndpoint(endpoint) }) {
                    add(endpoint)
                }
            }
        }
}

fun interface ObdConnectionAttemptObserver {
    suspend fun onStep(step: ObdConnectionStep)

    companion object {
        val NoOp = ObdConnectionAttemptObserver { }
    }
}

class ObdConnectionAttemptRunner(
    private val transportFactory: ObdTransportFactory,
    private val elm327Protocol: Elm327Protocol,
    private val now: () -> Instant = { Clock.System.now() },
    private val maxParallelWifiAttempts: Int = DefaultMaxParallelWifiAttempts
) {
    init {
        require(maxParallelWifiAttempts > 0) {
            "maxParallelWifiAttempts must be positive"
        }
    }

    suspend fun connect(
        target: ObdConnectionTarget,
        observer: ObdConnectionAttemptObserver = ObdConnectionAttemptObserver.NoOp
    ): ObdResult<ObdConnectionAttemptResult> =
        attemptTarget(
            target = target,
            observer = observer
        )

    internal suspend fun connect(
        group: WifiEndpointAttemptGroup,
        observer: ObdConnectionAttemptObserver = ObdConnectionAttemptObserver.NoOp
    ): ObdResult<ObdConnectionAttemptResult> {
        val endpoints = group.orderedEndpoints
        return if (endpoints.size == 1) {
            connect(group.selected, observer)
        } else {
            attemptWifiEndpointGroup(
                selected = group.selected,
                endpoints = endpoints,
                observer = observer
            )
        }
    }

    private suspend fun attemptTarget(
        target: ObdConnectionTarget,
        observer: ObdConnectionAttemptObserver,
        emitOpeningStep: Boolean = true,
        onHandshakeStep: suspend () -> Unit = { observer.onStep(ObdConnectionStep.SendingElmHandshake) }
    ): ObdResult<ObdConnectionAttemptResult> {
        if (emitOpeningStep) {
            observer.onStep(ObdConnectionStep.OpeningTransport)
        }

        val channel = when (val openResult = transportFactory.open(target)) {
            is ObdResult.Failure -> return openResult
            is ObdResult.Success -> openResult.value
        }

        return try {
            onHandshakeStep()
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
        } catch (throwable: Throwable) {
            channel.closeQuietly()
            throw throwable
        }
    }

    private suspend fun attemptWifiEndpointGroup(
        selected: ObdConnectionTarget.WifiTcp,
        endpoints: List<ObdConnectionTarget.WifiTcp>,
        observer: ObdConnectionAttemptObserver
    ): ObdResult<ObdConnectionAttemptResult> = coroutineScope {
        observer.onStep(ObdConnectionStep.OpeningTransport)

        val outcomes = Channel<WifiAttemptOutcome>(Channel.UNLIMITED)
        val semaphore = Semaphore(permits = minOf(maxParallelWifiAttempts, endpoints.size))
        val handshakeStep = SingleStepEmitter {
            observer.onStep(ObdConnectionStep.SendingElmHandshake)
        }
        val jobs = endpoints.map { endpoint ->
            launch {
                semaphore.withPermit {
                    attemptWifiEndpoint(
                        endpoint = endpoint,
                        outcomes = outcomes,
                        onHandshakeStep = { handshakeStep.emit() }
                    )
                }
            }
        }
        val failures = mutableListOf<WifiAttemptOutcome.Failure>()
        var winner: WifiAttemptOutcome.Success? = null
        var returningWinner = false

        try {
            var completedAttempts = 0
            while (completedAttempts < endpoints.size && winner == null) {
                when (val outcome = outcomes.receive()) {
                    is WifiAttemptOutcome.Failure -> failures += outcome
                    is WifiAttemptOutcome.Success -> {
                        winner = outcome
                        jobs.forEach { job -> job.cancel() }
                    }
                }
                completedAttempts += 1
            }

            val winningOutcome = winner
            if (winningOutcome != null) {
                jobs.joinAll()
                closeQueuedSuccessfulLosers(outcomes, winningOutcome)
                returningWinner = true
                return@coroutineScope ObdResult.Success(winningOutcome.result)
            }

            jobs.joinAll()
            return@coroutineScope ObdResult.Failure(
                failures.selectedFailure(selected)?.error
                    ?: failures.firstOrNull()?.error
                    ?: ObdError.TcpEndpointUnavailable(host = selected.host, port = selected.port)
            )
        } finally {
            withContext(NonCancellable) {
                if (!returningWinner) {
                    jobs.forEach { job -> job.cancel() }
                    jobs.joinAll()
                    winner?.result?.protocolSession?.closeQuietly()
                    closeQueuedSuccessfulLosers(outcomes, winner)
                }
                outcomes.close()
            }
        }
    }

    private suspend fun attemptWifiEndpoint(
        endpoint: ObdConnectionTarget.WifiTcp,
        outcomes: Channel<WifiAttemptOutcome>,
        onHandshakeStep: suspend () -> Unit
    ) {
        var pendingSuccess: WifiAttemptOutcome.Success? = null
        try {
            when (
                val result = attemptTarget(
                    target = endpoint,
                    observer = ObdConnectionAttemptObserver.NoOp,
                    emitOpeningStep = false,
                    onHandshakeStep = onHandshakeStep
                )
            ) {
                is ObdResult.Failure -> outcomes.send(
                    WifiAttemptOutcome.Failure(
                        target = endpoint,
                        error = result.error
                    )
                )

                is ObdResult.Success -> {
                    val outcome = WifiAttemptOutcome.Success(
                        target = endpoint,
                        result = result.value
                    )
                    pendingSuccess = outcome
                    outcomes.send(outcome)
                    pendingSuccess = null
                }
            }
        } finally {
            pendingSuccess?.result?.protocolSession?.closeQuietly()
        }
    }

    private suspend fun closeQueuedSuccessfulLosers(
        outcomes: Channel<WifiAttemptOutcome>,
        winner: WifiAttemptOutcome.Success?
    ) {
        while (true) {
            val outcome = outcomes.tryReceive().getOrNull() ?: return
            if (outcome is WifiAttemptOutcome.Success && outcome !== winner) {
                outcome.result.protocolSession.closeQuietly()
            }
        }
    }

    private suspend fun ObdByteChannel.closeQuietly() {
        withContext(NonCancellable) {
            runCatching { close() }
        }
    }

    private suspend fun Elm327ProtocolSession.closeQuietly() {
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

    private sealed interface WifiAttemptOutcome {
        data class Success(
            val target: ObdConnectionTarget.WifiTcp,
            val result: ObdConnectionAttemptResult
        ) : WifiAttemptOutcome

        data class Failure(
            val target: ObdConnectionTarget.WifiTcp,
            val error: ObdError
        ) : WifiAttemptOutcome
    }

    private class SingleStepEmitter(
        private val emitStep: suspend () -> Unit
    ) {
        private val mutex = Mutex()
        private var emitted = false

        suspend fun emit() {
            mutex.withLock {
                if (!emitted) {
                    emitted = true
                    emitStep()
                }
            }
        }
    }

    private fun List<WifiAttemptOutcome.Failure>.selectedFailure(
        selected: ObdConnectionTarget.WifiTcp
    ): WifiAttemptOutcome.Failure? =
        firstOrNull { failure -> failure.target.sameEndpoint(selected) }

    private companion object {
        const val DefaultMaxParallelWifiAttempts = 4
    }
}

private fun ObdConnectionTarget.WifiTcp.sameEndpoint(other: ObdConnectionTarget.WifiTcp): Boolean =
    host == other.host && port == other.port
