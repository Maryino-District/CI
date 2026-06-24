package maryino.district.carinspector.obd.data.repository

import com.russhwolf.settings.Settings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.data.connection.ObdConnectionAttemptRunner
import maryino.district.carinspector.obd.data.discovery.ObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.ObdCandidateRanker
import maryino.district.carinspector.obd.data.discovery.ObdDiscoveryEvent
import maryino.district.carinspector.obd.data.elm327.Elm327Protocol
import maryino.district.carinspector.obd.data.elm327.Elm327ProtocolSession
import maryino.district.carinspector.obd.data.memory.AdapterMemory
import maryino.district.carinspector.obd.data.platform.ObdTransportAvailabilityProvider
import maryino.district.carinspector.obd.data.session.ObdSessionManager
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdByteChannelEvent
import maryino.district.carinspector.obd.data.transport.ObdTransportFactory
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.scan.ObdScanEvent
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionState
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportStatus
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import maryino.district.carinspector.obd.test.FakeObdAdapterDiscovery
import maryino.district.carinspector.obd.test.wifiCandidate

class DefaultObdConnectionRepositoryTest {
    @Test
    fun initialStateIsIdle() = runTest {
        val repository = repository()

        assertEquals(ObdConnectionState.Idle, repository.connectionState.first())
    }

    @Test
    fun observeSupportedTransportsDelegatesToAvailabilityProvider() = runTest {
        val availability = listOf(
            ObdTransportAvailability(
                type = ObdTransportType.BluetoothClassic,
                status = ObdTransportStatus.UnsupportedOnPlatform,
                userAction = null
            ),
            ObdTransportAvailability(
                type = ObdTransportType.WifiTcp,
                status = ObdTransportStatus.Available,
                userAction = null
            )
        )
        val repository = repository(
            transportAvailabilityProvider = FakeObdTransportAvailabilityProvider(availability)
        )

        assertEquals(availability, repository.observeSupportedTransports().first())
    }

    @Test
    fun scanEmitsEventsAndUpdatesFindingState() = runTest {
        val candidate = wifiCandidate()
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(candidate)
            )
        )

        val events = repository.scan(ObdScanRequest()).toList()

        assertTrue(events[0] is ObdScanEvent.Started)
        assertEquals(ObdScanEvent.CandidateFound(candidate), events[1])
        assertEquals(ObdScanEvent.Finished(listOf(candidate)), events[2])

        val state = repository.connectionState.first()
        assertTrue(state is ObdConnectionState.FindingAdapters)
        assertEquals(listOf(candidate), state.candidates)
        assertTrue(state.activeTransports.isEmpty())
    }

    @Test
    fun successfulConnectMovesThroughConnectingInitializingAndConnected() = runTest {
        val target = wifiCandidate().target
        val protocolSession = FakeElm327ProtocolSession()
        val repository = repository(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel())),
            elm327Protocol = FakeElm327Protocol(ObdResult.Success(protocolSession))
        )
        val states = collectStatesInBackground(repository)

        val result = repository.connect(target)

        assertTrue(result is ObdResult.Success)
        assertTrue(states.any { state -> state is ObdConnectionState.Connecting })
        assertTrue(states.any { state -> state is ObdConnectionState.InitializingElm327 })
        assertEquals(ObdConnectionState.Connected(result.value), states.last())
        assertFalse(protocolSession.closed)
    }

    @Test
    fun failedConnectWithoutKnownCandidateMovesToFailed() = runTest {
        val error = ObdError.TcpEndpointUnavailable(host = "192.168.0.10", port = 35000)
        val repository = repository(
            transportFactory = FakeObdTransportFactory(ObdResult.Failure(error))
        )

        val result = repository.connect(wifiCandidate().target)

        assertEquals(ObdResult.Failure(error), result)
        assertEquals(
            ObdConnectionState.Failed(error = error, recoverAction = null),
            repository.connectionState.first()
        )
    }

    @Test
    fun failedConnectForKnownCandidateReturnsToFindingAndMarksRejected() = runTest {
        val candidate = wifiCandidate()
        val error = ObdError.TcpEndpointUnavailable(host = "192.168.0.10", port = 35000)
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(candidate)
            ),
            transportFactory = FakeObdTransportFactory(ObdResult.Failure(error))
        )
        repository.scan(ObdScanRequest()).collect()

        val result = repository.connect(candidate.target)

        assertEquals(ObdResult.Failure(error), result)
        val state = repository.connectionState.first()
        assertTrue(state is ObdConnectionState.FindingAdapters)
        assertEquals(
            ObdCandidateProbeState.Rejected(error),
            state.candidates.single().probeState
        )
    }

    @Test
    fun failedConnectDuringActiveScanKeepsCandidatesAndScanAlive() = runTest {
        val firstCandidate = wifiCandidate(id = "wifi-first", host = "192.168.0.10")
        val secondCandidate = wifiCandidate(id = "wifi-second", host = "192.168.0.11")
        val error = ObdError.TcpEndpointUnavailable(host = "192.168.0.10", port = 35000)
        val discovery = ControllableObdAdapterDiscovery()
        val repository = repository(
            discovery = discovery,
            transportFactory = FakeObdTransportFactory(ObdResult.Failure(error))
        )
        val scanEvents = mutableListOf<ObdScanEvent>()
        val scanJob = launch {
            repository.scan(ObdScanRequest()).collect { event -> scanEvents += event }
        }
        discovery.started.await()
        discovery.emit(ObdDiscoveryEvent.CandidateFound(firstCandidate))
        repository.awaitFindingState { state ->
            state.candidates.any { candidate -> candidate.id == firstCandidate.id }
        }

        val result = repository.connect(firstCandidate.target)

        assertEquals(ObdResult.Failure(error), result)
        val rejectedState = repository.awaitFindingState { state ->
            state.candidates.any { candidate ->
                candidate.id == firstCandidate.id &&
                    candidate.probeState == ObdCandidateProbeState.Rejected(error)
            }
        }
        assertTrue(scanJob.isActive)
        assertEquals(
            ObdCandidateProbeState.Rejected(error),
            rejectedState.candidates.single { candidate -> candidate.id == firstCandidate.id }.probeState
        )

        discovery.emit(ObdDiscoveryEvent.CandidateFound(secondCandidate))

        val stateWithNewCandidate = repository.awaitFindingState { state ->
            state.candidates.any { candidate -> candidate.id == secondCandidate.id }
        }
        val candidatesById = stateWithNewCandidate.candidates.associateBy { candidate -> candidate.id }
        assertEquals(ObdCandidateProbeState.Rejected(error), candidatesById[firstCandidate.id]?.probeState)
        assertEquals(secondCandidate.probeState, candidatesById[secondCandidate.id]?.probeState)

        discovery.finish()
        scanJob.join()
        assertTrue(scanEvents.last() is ObdScanEvent.Finished)
    }

    @Test
    fun disconnectFromConnectedClosesProtocolSessionAndReturnsIdle() = runTest {
        val target = wifiCandidate().target
        val protocolSession = FakeElm327ProtocolSession()
        val repository = repository(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel())),
            elm327Protocol = FakeElm327Protocol(ObdResult.Success(protocolSession))
        )
        val states = collectStatesInBackground(repository)
        repository.connect(target)

        repository.disconnect()

        assertTrue(protocolSession.closed)
        assertTrue(states.any { state -> state is ObdConnectionState.Disconnecting })
        assertEquals(ObdConnectionState.Idle, states.last())
    }

    @Test
    fun disconnectDuringActiveAttemptCancelsAttemptAndClosesOpenedChannel() = runTest {
        val target = wifiCandidate().target
        val channel = FakeObdByteChannel()
        val protocol = FakeElm327Protocol(cancellationMode = true)
        val repository = repository(
            transportFactory = FakeObdTransportFactory(ObdResult.Success(channel)),
            elm327Protocol = protocol
        )
        val connectError = CompletableDeferred<Throwable?>()
        launch {
            try {
                repository.connect(target)
                connectError.complete(null)
            } catch (throwable: Throwable) {
                connectError.complete(throwable)
            }
        }
        protocol.openStarted.await()

        repository.disconnect()

        assertTrue(connectError.await() is CancellationException)
        assertTrue(channel.closed)
        assertEquals(ObdConnectionState.Idle, repository.connectionState.first())
    }

    @Test
    fun scanMarksRememberedCandidateAndRanksItFirst() = runTest {
        val unknownCandidate = wifiCandidate(
            id = "unknown",
            host = "192.168.0.10",
            confidence = ObdAdapterConfidence.High
        )
        val rememberedCandidate = wifiCandidate(
            id = "remembered",
            host = "192.168.4.1",
            confidence = ObdAdapterConfidence.Medium
        )
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(unknownCandidate),
                ObdDiscoveryEvent.CandidateFound(rememberedCandidate)
            ),
            adapterMemory = memoryWith(rememberedCandidate.toFingerprint())
        )

        val events = repository.scan(ObdScanRequest()).toList()

        val rememberedEvent = events.filterIsInstance<ObdScanEvent.CandidateFound>()
            .single { event -> event.adapter.id == rememberedCandidate.id }
        val unknownEvent = events.filterIsInstance<ObdScanEvent.CandidateFound>()
            .single { event -> event.adapter.id == unknownCandidate.id }
        assertTrue(rememberedEvent.adapter.isRemembered)
        assertFalse(unknownEvent.adapter.isRemembered)

        val state = repository.connectionState.first()
        assertTrue(state is ObdConnectionState.FindingAdapters)
        assertEquals(rememberedCandidate.id, state.candidates.first().id)
        assertTrue(state.candidates.first().isRemembered)
    }

    @Test
    fun scanWithRememberedCandidateDoesNotOpenTransport() = runTest {
        val rememberedCandidate = wifiCandidate(id = "remembered", host = "192.168.4.1")
        val transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel()))
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(rememberedCandidate)
            ),
            transportFactory = transportFactory,
            adapterMemory = memoryWith(rememberedCandidate.toFingerprint())
        )

        repository.scan(ObdScanRequest()).collect()

        assertTrue(transportFactory.openedTargets.isEmpty())
        val state = repository.connectionState.first()
        assertTrue(state is ObdConnectionState.FindingAdapters)
        assertTrue(state.candidates.single().isRemembered)
    }

    @Test
    fun scanWithoutRememberedMatchKeepsCandidateUnmarkedAndDoesNotFail() = runTest {
        val unknownCandidate = wifiCandidate(id = "unknown", host = "192.168.0.10")
        val rememberedFingerprint = wifiCandidate(id = "remembered", host = "192.168.4.1").toFingerprint()
        val transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel()))
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(unknownCandidate)
            ),
            transportFactory = transportFactory,
            adapterMemory = memoryWith(rememberedFingerprint)
        )

        val events = repository.scan(ObdScanRequest()).toList()

        assertTrue(transportFactory.openedTargets.isEmpty())
        assertTrue(events.last() is ObdScanEvent.Finished)
        val state = repository.connectionState.first()
        assertTrue(state is ObdConnectionState.FindingAdapters)
        assertTrue(state.activeTransports.isEmpty())
        assertFalse(state.candidates.single().isRemembered)
    }

    @Test
    fun explicitConnectStillOpensRememberedTargetAfterUserSelection() = runTest {
        val rememberedCandidate = wifiCandidate(id = "remembered", host = "192.168.4.1")
        val transportFactory = FakeObdTransportFactory(ObdResult.Success(FakeObdByteChannel()))
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(rememberedCandidate)
            ),
            transportFactory = transportFactory,
            adapterMemory = memoryWith(rememberedCandidate.toFingerprint())
        )
        repository.scan(ObdScanRequest()).collect()

        val result = repository.connect(rememberedCandidate.target)

        assertTrue(result is ObdResult.Success)
        assertEquals(listOf(rememberedCandidate.target), transportFactory.openedTargets)
    }

    @Test
    fun wifiConnectUsesEndpointGroupSnapshotAndPublishesConnectedOnce() = runTest {
        val selectedCandidate = wifiCandidate(id = "selected", host = "192.168.0.10", port = 35000)
        val duplicateSelectedCandidate = wifiCandidate(id = "selected-duplicate", host = "192.168.0.10", port = 35000)
        val winnerCandidate = wifiCandidate(id = "winner", host = "192.168.4.1", port = 23)
        val selectedTarget = selectedCandidate.target as ObdConnectionTarget.WifiTcp
        val winnerTarget = winnerCandidate.target as ObdConnectionTarget.WifiTcp
        val selectedChannel = FakeObdByteChannel()
        val winnerChannel = FakeObdByteChannel()
        val winnerSession = FakeElm327ProtocolSession()
        val selectedError = ObdError.CandidateIsNotElm327(targetLabel = selectedTarget.endpointKey)
        val transportFactory = TargetedObdTransportFactory(
            channels = mapOf(
                selectedTarget.endpointKey to selectedChannel,
                winnerTarget.endpointKey to winnerChannel
            )
        )
        val repository = repository(
            discovery = FakeObdAdapterDiscovery.scripted(
                ObdDiscoveryEvent.CandidateFound(selectedCandidate),
                ObdDiscoveryEvent.CandidateFound(duplicateSelectedCandidate),
                ObdDiscoveryEvent.CandidateFound(winnerCandidate)
            ),
            transportFactory = transportFactory,
            elm327Protocol = ChannelResultElm327Protocol(
                results = mapOf(
                    selectedChannel to ObdResult.Failure(selectedError),
                    winnerChannel to ObdResult.Success(winnerSession)
                )
            )
        )
        repository.scan(ObdScanRequest()).collect()
        val states = collectStatesInBackground(repository)

        val result = repository.connect(selectedCandidate.target)

        assertTrue(result is ObdResult.Success)
        assertEquals(winnerCandidate.target, result.value.adapter.target)
        assertEquals(1, states.count { state -> state is ObdConnectionState.Connected })
        assertTrue(selectedChannel.closed)
        assertFalse(winnerSession.closed)
        assertEquals(
            setOf(selectedTarget.endpointKey, winnerTarget.endpointKey),
            transportFactory.openedTargets
                .map { target -> (target as ObdConnectionTarget.WifiTcp).endpointKey }
                .toSet()
        )
    }

    private fun TestScope.repository(
        scope: CoroutineScope = backgroundScope,
        discovery: ObdAdapterDiscovery = FakeObdAdapterDiscovery.noOp(),
        transportFactory: ObdTransportFactory = FakeObdTransportFactory(
            ObdResult.Success(FakeObdByteChannel())
        ),
        elm327Protocol: Elm327Protocol = FakeElm327Protocol(
            ObdResult.Success(FakeElm327ProtocolSession())
        ),
        adapterMemory: AdapterMemory = AdapterMemory(FakeSettings()),
        transportAvailabilityProvider: ObdTransportAvailabilityProvider =
            FakeObdTransportAvailabilityProvider(DefaultAvailability)
    ): DefaultObdConnectionRepository =
        DefaultObdConnectionRepository(
            scope = scope,
            discovery = discovery,
            candidateRanker = ObdCandidateRanker(),
            attemptRunner = ObdConnectionAttemptRunner(
                transportFactory = transportFactory,
                elm327Protocol = elm327Protocol,
                now = { NOW }
            ),
            sessionManager = ObdSessionManager(),
            adapterMemory = adapterMemory,
            transportAvailabilityProvider = transportAvailabilityProvider,
            now = { NOW }
        )

    private fun memoryWith(fingerprint: AdapterFingerprint): AdapterMemory =
        AdapterMemory(FakeSettings()).also { memory -> memory.save(fingerprint) }

    private fun wifiCandidateFingerprint(
        host: String,
        port: Int
    ): AdapterFingerprint =
        AdapterFingerprint(
            transportType = ObdTransportType.WifiTcp,
            stableId = "$host:$port",
            displayName = "Wi-Fi OBD $host:$port",
            bleProfileId = null,
            wifiHost = host,
            wifiPort = port,
            lastSuccessfulAt = NOW
        )

    private fun DiscoveredObdAdapter.toFingerprint(): AdapterFingerprint {
        val target = target as ObdConnectionTarget.WifiTcp
        return wifiCandidateFingerprint(host = target.host, port = target.port)
    }

    private suspend fun DefaultObdConnectionRepository.awaitFindingState(
        predicate: (ObdConnectionState.FindingAdapters) -> Boolean
    ): ObdConnectionState.FindingAdapters {
        val state = connectionState.first { state ->
            state is ObdConnectionState.FindingAdapters && predicate(state)
        }
        return state as ObdConnectionState.FindingAdapters
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.collectStatesInBackground(
        repository: DefaultObdConnectionRepository
    ): MutableList<ObdConnectionState> {
        val states = mutableListOf<ObdConnectionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.connectionState.collect { state -> states += state }
        }
        return states
    }

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
        private val result: ObdResult<Elm327ProtocolSession> = ObdResult.Success(FakeElm327ProtocolSession()),
        private val cancellationMode: Boolean = false
    ) : Elm327Protocol {
        val openStarted = CompletableDeferred<Unit>()

        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
            openStarted.complete(Unit)
            if (cancellationMode) {
                awaitCancellation()
            }
            return result
        }
    }

    private class ChannelResultElm327Protocol(
        private val results: Map<ObdByteChannel, ObdResult<Elm327ProtocolSession>>
    ) : Elm327Protocol {
        override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> =
            results[channel] ?: error("No protocol result configured for $channel")
    }

    private class TargetedObdTransportFactory(
        private val channels: Map<String, ObdByteChannel>
    ) : ObdTransportFactory {
        val openedTargets = mutableListOf<ObdConnectionTarget>()

        override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
            openedTargets += target
            val endpoint = target as? ObdConnectionTarget.WifiTcp
                ?: error("TargetedObdTransportFactory only supports Wi-Fi targets")
            return ObdResult.Success(
                channels[endpoint.endpointKey]
                    ?: error("No channel configured for ${endpoint.endpointKey}")
            )
        }
    }

    private class FakeObdTransportAvailabilityProvider(
        private val availability: List<ObdTransportAvailability>
    ) : ObdTransportAvailabilityProvider {
        override fun observeAvailability(): Flow<List<ObdTransportAvailability>> =
            flowOf(availability)
    }

    private class ControllableObdAdapterDiscovery : ObdAdapterDiscovery {
        private val events = Channel<ObdDiscoveryEvent>(Channel.UNLIMITED)

        val started = CompletableDeferred<Unit>()
        var lastRequest: ObdScanRequest? = null
            private set

        override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
            lastRequest = request
            started.complete(Unit)
            for (event in events) {
                emit(event)
            }
        }

        suspend fun emit(event: ObdDiscoveryEvent) {
            events.send(event)
        }

        fun finish() {
            events.close()
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
            error("FakeElm327ProtocolSession.send is not used by repository tests")

        override suspend fun close() {
            closed = true
        }
    }

    private class FakeSettings : Settings {
        private val values = mutableMapOf<String, Any>()

        override val keys: Set<String>
            get() = values.keys

        override val size: Int
            get() = values.size

        override fun clear() {
            values.clear()
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun hasKey(key: String): Boolean =
            values.containsKey(key)

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            getIntOrNull(key) ?: defaultValue

        override fun getIntOrNull(key: String): Int? =
            values[key] as? Int

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }

        override fun getLong(key: String, defaultValue: Long): Long =
            getLongOrNull(key) ?: defaultValue

        override fun getLongOrNull(key: String): Long? =
            values[key] as? Long

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun getString(key: String, defaultValue: String): String =
            getStringOrNull(key) ?: defaultValue

        override fun getStringOrNull(key: String): String? =
            values[key] as? String

        override fun putFloat(key: String, value: Float) {
            values[key] = value
        }

        override fun getFloat(key: String, defaultValue: Float): Float =
            getFloatOrNull(key) ?: defaultValue

        override fun getFloatOrNull(key: String): Float? =
            values[key] as? Float

        override fun putDouble(key: String, value: Double) {
            values[key] = value
        }

        override fun getDouble(key: String, defaultValue: Double): Double =
            getDoubleOrNull(key) ?: defaultValue

        override fun getDoubleOrNull(key: String): Double? =
            values[key] as? Double

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            getBooleanOrNull(key) ?: defaultValue

        override fun getBooleanOrNull(key: String): Boolean? =
            values[key] as? Boolean
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-01T00:00:00Z")
        val DefaultAvailability = ObdTransportType.entries.map { type ->
            ObdTransportAvailability(
                type = type,
                status = ObdTransportStatus.Available,
                userAction = null
            )
        }
    }
}

private val ObdConnectionTarget.WifiTcp.endpointKey: String
    get() = "$host:$port"
