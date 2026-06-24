package maryino.district.carinspector.obd.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maryino.district.carinspector.obd.data.connection.ObdConnectionAttemptObserver
import maryino.district.carinspector.obd.data.connection.ObdConnectionAttemptRunner
import maryino.district.carinspector.obd.data.connection.WifiEndpointAttemptGroup
import maryino.district.carinspector.obd.data.discovery.ObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.ObdCandidateRanker
import maryino.district.carinspector.obd.data.discovery.ObdDiscoveryEvent
import maryino.district.carinspector.obd.data.memory.AdapterMemory
import maryino.district.carinspector.obd.data.platform.ObdTransportAvailabilityProvider
import maryino.district.carinspector.obd.data.session.ObdSessionManager
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.scan.ObdScanEvent
import maryino.district.carinspector.obd.domain.model.scan.ObdScanHint
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionAttempt
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionAttemptId
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionState
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionStep
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import maryino.district.carinspector.obd.domain.repository.ObdCommandGateway
import maryino.district.carinspector.obd.domain.repository.ObdConnectionRepository

class DefaultObdConnectionRepository(
    private val scope: CoroutineScope,
    private val discovery: ObdAdapterDiscovery,
    private val candidateRanker: ObdCandidateRanker,
    private val attemptRunner: ObdConnectionAttemptRunner,
    private val sessionManager: ObdSessionManager,
    private val adapterMemory: AdapterMemory,
    private val transportAvailabilityProvider: ObdTransportAvailabilityProvider,
    private val now: () -> Instant = { Clock.System.now() }
) : ObdConnectionRepository {
    private val mutex = Mutex()
    private val mutableConnectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Idle)
    private val currentCandidates = mutableMapOf<ObdAdapterId, DiscoveredObdAdapter>()
    private var currentHint: ObdScanHint? = null
    private var currentActiveTransports: Set<ObdTransportType> = emptySet()
    private var activeScanJob: Job? = null
    private var activeConnectJob: Job? = null
    private var attemptCounter = 0

    override val connectionState: Flow<ObdConnectionState> = mutableConnectionState.asStateFlow()

    override val commandGateway: ObdCommandGateway = sessionManager.commandGateway

    override fun observeSupportedTransports(): Flow<List<ObdTransportAvailability>> =
        transportAvailabilityProvider.observeAvailability()

    override fun scan(request: ObdScanRequest): Flow<ObdScanEvent> = flow {
        val scanJob = currentCoroutineContext()[Job]
            ?: error("scan() must run inside a coroutine with a Job")

        startScan(scanJob, request)
        emit(ObdScanEvent.Started(request))

        try {
            discovery.scan(request).collect { discoveryEvent ->
                applyDiscoveryEvent(discoveryEvent)?.let { scanEvent -> emit(scanEvent) }
            }
            emit(ObdScanEvent.Finished(finishScan()))
        } finally {
            withContext(NonCancellable) {
                clearActiveScan(scanJob)
            }
        }
    }

    override suspend fun connect(target: ObdConnectionTarget): ObdResult<ObdSession> {
        val connectJob = scope.async(start = CoroutineStart.LAZY) {
            runConnect(target)
        }

        mutex.withLock {
            activeConnectJob?.cancel()
            activeConnectJob = connectJob
        }

        connectJob.start()
        return try {
            connectJob.await()
        } catch (cancellation: CancellationException) {
            connectJob.cancel()
            throw cancellation
        } finally {
            withContext(NonCancellable) {
                clearActiveConnect(connectJob)
            }
        }
    }

    override suspend fun disconnect() {
        val currentJob = currentCoroutineContext()[Job]
        val session = sessionManager.currentSession()
        val jobs = mutex.withLock {
            val scanJob = activeScanJob
            val connectJob = activeConnectJob
            activeScanJob = null
            activeConnectJob = null

            if (session != null) {
                mutableConnectionState.value = ObdConnectionState.Disconnecting(session.id)
            }

            JobsToCancel(scanJob, connectJob)
        }

        jobs.scanJob.cancelAndJoinUnlessCurrent(currentJob)
        jobs.connectJob.cancelAndJoinUnlessCurrent(currentJob)
        sessionManager.closeActiveSession()

        mutex.withLock {
            currentActiveTransports = emptySet()
            currentHint = null
            mutableConnectionState.value = ObdConnectionState.Idle
        }
    }

    private suspend fun runConnect(target: ObdConnectionTarget): ObdResult<ObdSession> {
        val wifiGroup = wifiEndpointGroupFor(target)
        var attempt = startAttempt(target)
        val observer = ObdConnectionAttemptObserver { step ->
            attempt = attempt.copy(step = step)
            updateAttemptState(attempt)
        }

        val result = if (wifiGroup != null) {
            attemptRunner.connect(wifiGroup, observer)
        } else {
            attemptRunner.connect(target, observer)
        }
        currentCoroutineContext().ensureActive()

        return when (result) {
            is ObdResult.Failure -> {
                applyConnectFailure(target, result.error)
                result
            }

            is ObdResult.Success -> {
                val attemptResult = result.value
                sessionManager.activate(
                    session = attemptResult.session,
                    protocolSession = attemptResult.protocolSession
                )
                adapterMemory.save(attemptResult.session.toFingerprint())
                publishConnected(attemptResult.session)
                ObdResult.Success(attemptResult.session)
            }
        }
    }

    private suspend fun wifiEndpointGroupFor(target: ObdConnectionTarget): WifiEndpointAttemptGroup? {
        val selected = target as? ObdConnectionTarget.WifiTcp ?: return null
        val endpoints = mutex.withLock {
            val orderedTargets = mutableListOf(selected)
            rankCandidatesLocked()
                .forEach { candidate ->
                    val candidateTarget = candidate.target as? ObdConnectionTarget.WifiTcp
                        ?: return@forEach
                    if (
                        candidateTarget.sameEndpoint(selected) ||
                        candidate.probeState !is ObdCandidateProbeState.Rejected
                    ) {
                        orderedTargets.addIfAbsentEndpoint(candidateTarget)
                    }
                }
            orderedTargets.toList()
        }

        return if (endpoints.size > 1) {
            WifiEndpointAttemptGroup(
                selected = selected,
                endpoints = endpoints
            )
        } else {
            null
        }
    }

    private suspend fun startScan(scanJob: Job, request: ObdScanRequest) {
        mutex.withLock {
            activeScanJob?.cancel()
            activeScanJob = scanJob
            currentCandidates.clear()
            currentHint = null
            currentActiveTransports = request.transportTypes
            publishFindingStateLocked(force = true)
        }
    }

    private suspend fun clearActiveScan(scanJob: Job) {
        mutex.withLock {
            if (activeScanJob === scanJob) {
                activeScanJob = null
            }
        }
    }

    private suspend fun clearActiveConnect(connectJob: Deferred<ObdResult<ObdSession>>) {
        mutex.withLock {
            if (activeConnectJob === connectJob) {
                activeConnectJob = null
            }
        }
    }

    private suspend fun applyDiscoveryEvent(event: ObdDiscoveryEvent): ObdScanEvent? =
        mutex.withLock {
            when (event) {
                is ObdDiscoveryEvent.CandidateFound -> {
                    val adapter = markRememberedLocked(event.adapter)
                    currentCandidates[adapter.id] = adapter
                    publishFindingStateLocked()
                    ObdScanEvent.CandidateFound(adapter)
                }

                is ObdDiscoveryEvent.CandidateUpdated -> {
                    val adapter = markRememberedLocked(event.adapter)
                    currentCandidates[adapter.id] = adapter
                    publishFindingStateLocked()
                    ObdScanEvent.CandidateUpdated(adapter)
                }

                is ObdDiscoveryEvent.TransportFailed -> {
                    currentActiveTransports = currentActiveTransports - event.type
                    publishFindingStateLocked()
                    ObdScanEvent.Failed(type = event.type, error = event.error)
                }

                is ObdDiscoveryEvent.TransportFinished -> {
                    currentActiveTransports = currentActiveTransports - event.type
                    publishFindingStateLocked()
                    null
                }
            }
        }

    private suspend fun finishScan(): List<DiscoveredObdAdapter> =
        mutex.withLock {
            currentActiveTransports = emptySet()
            publishFindingStateLocked()
            rankCandidatesLocked()
        }

    private suspend fun startAttempt(target: ObdConnectionTarget): ObdConnectionAttempt =
        mutex.withLock {
            attemptCounter += 1
            ObdConnectionAttempt(
                id = ObdConnectionAttemptId("attempt:$attemptCounter"),
                target = target,
                step = ObdConnectionStep.OpeningTransport,
                attemptNumber = attemptCounter,
                startedAt = now()
            ).also { attempt ->
                mutableConnectionState.value = ObdConnectionState.Connecting(attempt)
            }
        }

    private suspend fun updateAttemptState(attempt: ObdConnectionAttempt) {
        mutex.withLock {
            mutableConnectionState.value = when (attempt.step) {
                ObdConnectionStep.SendingElmHandshake,
                ObdConnectionStep.WaitingForElmPrompt,
                ObdConnectionStep.ValidatingElmResponse -> ObdConnectionState.InitializingElm327(
                    adapter = currentCandidateForTargetLocked(attempt.target)
                )

                else -> ObdConnectionState.Connecting(attempt)
            }
        }
    }

    private suspend fun applyConnectFailure(
        target: ObdConnectionTarget,
        error: ObdError
    ) {
        mutex.withLock {
            val rejectedCandidate = markCandidateRejectedLocked(target, error)
            if (rejectedCandidate) {
                publishFindingStateLocked(force = true)
            } else {
                mutableConnectionState.value = ObdConnectionState.Failed(
                    error = error,
                    recoverAction = error.recoverAction()
                )
            }
        }
    }

    private suspend fun publishConnected(session: ObdSession) {
        val scanJob = mutex.withLock {
            val job = activeScanJob
            activeScanJob = null
            currentActiveTransports = emptySet()
            mutableConnectionState.value = ObdConnectionState.Connected(session)
            job
        }

        scanJob?.cancel()
    }

    private fun markCandidateRejectedLocked(
        target: ObdConnectionTarget,
        error: ObdError
    ): Boolean {
        val entry = currentCandidates.entries.firstOrNull { (_, candidate) -> candidate.target == target }
            ?: return false

        currentCandidates[entry.key] = entry.value.copy(
            probeState = ObdCandidateProbeState.Rejected(error)
        )
        return true
    }

    private fun publishFindingStateLocked(force: Boolean = false) {
        if (force || mutableConnectionState.value is ObdConnectionState.FindingAdapters) {
            mutableConnectionState.value = ObdConnectionState.FindingAdapters(
                activeTransports = currentActiveTransports,
                candidates = rankCandidatesLocked(),
                hint = currentHint
            )
        }
    }

    private fun rankCandidatesLocked(): List<DiscoveredObdAdapter> =
        rankCandidatesLocked(adapterMemory.load())

    private fun rankCandidatesLocked(remembered: AdapterFingerprint?): List<DiscoveredObdAdapter> =
        candidateRanker.rank(
            candidates = currentCandidates.values.toList(),
            remembered = remembered
        )

    private fun markRememberedLocked(candidate: DiscoveredObdAdapter): DiscoveredObdAdapter {
        val remembered = adapterMemory.load()
        val isRemembered = remembered?.let { fingerprint ->
            candidateRanker.matchesRemembered(candidate, fingerprint)
        } ?: false

        return if (candidate.isRemembered == isRemembered) {
            candidate
        } else {
            candidate.copy(isRemembered = isRemembered)
        }
    }

    private fun currentCandidateForTargetLocked(target: ObdConnectionTarget): DiscoveredObdAdapter? =
        currentCandidates.values.firstOrNull { candidate -> candidate.target == target }

    private fun ObdSession.toFingerprint(): AdapterFingerprint {
        val target = adapter.target
        return AdapterFingerprint(
            transportType = adapter.transportType,
            stableId = target.stableId(),
            displayName = adapter.displayName,
            bleProfileId = (target as? ObdConnectionTarget.Ble)?.knownProfileId,
            wifiHost = (target as? ObdConnectionTarget.WifiTcp)?.host,
            wifiPort = (target as? ObdConnectionTarget.WifiTcp)?.port,
            lastSuccessfulAt = connectedAt
        )
    }

    private fun ObdConnectionTarget.stableId(): String =
        when (this) {
            is ObdConnectionTarget.BluetoothClassic -> deviceAddress
            is ObdConnectionTarget.Ble -> peripheralId
            is ObdConnectionTarget.WifiTcp -> "$host:$port"
        }

    private fun ObdError.recoverAction(): ObdRequiredSetupAction? =
        when (this) {
            is ObdError.PermissionDenied -> action
            is ObdError.BluetoothDisabled -> action
            is ObdError.NoBondedClassicDevices -> action
            ObdError.WifiNetworkNotConnected -> ObdRequiredSetupAction.ConnectToAdapterWifi
            else -> null
        }

    private suspend fun Job?.cancelAndJoinUnlessCurrent(currentJob: Job?) {
        if (this == null) return
        cancel()
        if (this !== currentJob) {
            cancelAndJoin()
        }
    }

    private data class JobsToCancel(
        val scanJob: Job?,
        val connectJob: Job?
    )
}

private fun MutableList<ObdConnectionTarget.WifiTcp>.addIfAbsentEndpoint(
    target: ObdConnectionTarget.WifiTcp
) {
    if (none { existing -> existing.sameEndpoint(target) }) {
        add(target)
    }
}

private fun ObdConnectionTarget.WifiTcp.sameEndpoint(other: ObdConnectionTarget.WifiTcp): Boolean =
    host == other.host && port == other.port
