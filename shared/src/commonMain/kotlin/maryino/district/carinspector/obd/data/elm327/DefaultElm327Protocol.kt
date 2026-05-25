package maryino.district.carinspector.obd.data.elm327

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.data.transport.ObdByteChannelEvent
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdOperation
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Command
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Exchange
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Info
import maryino.district.carinspector.obd.domain.model.elm327.Elm327Response
import maryino.district.carinspector.obd.domain.model.elm327.Elm327ResponseStatus
import kotlin.time.Duration.Companion.seconds

class DefaultElm327Protocol : Elm327Protocol {
    override suspend fun openSession(channel: ObdByteChannel): ObdResult<Elm327ProtocolSession> {
        val session = DefaultElm327ProtocolSession(channel)

        // A transport-level connection only proves that Bluetooth/GATT/TCP is
        // open. The app should expose an ELM327 session only after the adapter
        // has answered the initialization AT commands with prompt-terminated
        // responses, so the handshake is part of session creation.
        return when (val handshakeResult = session.performHandshake()) {
            is ObdResult.Success -> {
                session.info = handshakeResult.value
                ObdResult.Success(session)
            }
            is ObdResult.Failure -> {
                // openSession owns the channel until it returns a live session.
                // If initialization fails, close immediately so a caller does
                // not leak an opened Bluetooth/GATT/TCP transport.
                session.close()
                handshakeResult
            }
        }
    }
}

private class DefaultElm327ProtocolSession(
    private val channel: ObdByteChannel
) : Elm327ProtocolSession {
    private val sendMutex = Mutex()
    private var closed = false

    override var info: Elm327Info = Elm327Info(identity = null)
        internal set

    suspend fun performHandshake(): ObdResult<Elm327Info> {
        val exchanges = mutableListOf<Elm327Exchange>()

        // Run the canonical initialization sequence through send(), not through
        // a separate write/read path. That keeps reset/setup behavior identical
        // to runtime commands: the same prompt parser, echo stripping, timeout
        // handling, and command serialization are exercised during connect.
        for (step in HANDSHAKE_COMMANDS) {
            val response = when (val result = send(step.command)) {
                is ObdResult.Failure -> return result
                is ObdResult.Success -> result.value
            }

            exchanges += response.toExchange()

            // Any non-OK status during initialization means we cannot trust the
            // channel as a usable ELM327 command session. Runtime PID commands
            // may legitimately return NO DATA, but AT setup commands should not.
            if (response.status !is Elm327ResponseStatus.Ok) {
                return ObdResult.Failure(response.toHandshakeError())
            }
        }

        // ATI is the only command in the first handshake that carries stable
        // adapter identity. Other metadata such as voltage/protocol can be
        // added later without changing the session creation contract.
        val identity = exchanges
            .firstOrNull { it.command.equals(IDENTITY_COMMAND, ignoreCase = true) }
            ?.normalizedLines
            ?.firstOrNull()

        if (!isPlausibleHandshake(exchanges)) {
            return ObdResult.Failure(ObdError.CandidateIsNotElm327(targetLabel = null))
        }

        return ObdResult.Success(
            Elm327Info(
                identity = identity,
                rawHandshake = exchanges
            )
        )
    }

    override suspend fun send(command: Elm327Command): ObdResult<Elm327Response> = sendMutex.withLock {
        if (closed) {
            return@withLock ObdResult.Failure(ObdError.TransportClosed(transportType = null, reason = "ELM327 session is closed"))
        }

        coroutineScope {
            // Start collecting before write(): transport incoming is hot, and
            // some adapters answer quickly enough that write-then-subscribe can
            // miss the first bytes.
            val readResult = async(start = CoroutineStart.UNDISPATCHED) {
                readUntilPrompt(command)
            }

            when (val writeResult = channel.write((command.value + COMMAND_TERMINATOR).encodeToByteArray())) {
                is ObdResult.Failure -> {
                    readResult.cancel()
                    writeResult
                }
                is ObdResult.Success -> when (val result = readResult.await()) {
                    is ReadResult.Closed -> ObdResult.Failure(
                        result.error ?: ObdError.TransportClosed(transportType = null, reason = "ELM327 channel closed")
                    )
                    // Command timeout is a protocol-level response status.
                    // Transport errors remain ObdResult.Failure.
                    ReadResult.Timeout -> ObdResult.Success(
                        Elm327Response(
                            command = command,
                            raw = "",
                            normalizedLines = emptyList(),
                            status = Elm327ResponseStatus.Timeout
                        )
                    )
                    is ReadResult.Response -> ObdResult.Success(
                        Elm327Response(
                            command = command,
                            raw = result.raw,
                            normalizedLines = normalizeLines(command.value, result.raw),
                            status = resolveStatus(normalizeLines(command.value, result.raw))
                        )
                    )
                }
            }
        }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        channel.close()
    }

    private suspend fun readUntilPrompt(command: Elm327Command): ReadResult {
        val raw = StringBuilder()
        var closedError: ObdError? = null
        var closedByChannel = false

        val receivedPrompt = withTimeoutOrNull(command.timeout) {
            channel.incoming.first { event ->
                when (event) {
                    is ObdByteChannelEvent.Bytes -> {
                        raw.append(event.value.decodeToString())
                        // ELM327 responses are frame-delimited by the prompt.
                        // Until ">" arrives the response may span many chunks.
                        raw.contains(PROMPT)
                    }
                    is ObdByteChannelEvent.Closed -> {
                        closedByChannel = true
                        closedError = event.error
                        true
                    }
                }
            }
        } != null

        return when {
            closedByChannel -> ReadResult.Closed(closedError)
            receivedPrompt -> ReadResult.Response(raw.toString())
            else -> ReadResult.Timeout
        }
    }

    private fun normalizeLines(commandValue: String, raw: String): List<String> {
        // Keep raw response untouched for diagnostics, but normalize caller-facing
        // lines by removing prompt, blank lines, and optional command echo.
        val lines = raw
            .replace(PROMPT, "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return if (lines.firstOrNull()?.equals(commandValue.trim(), ignoreCase = true) == true) {
            lines.drop(1)
        } else {
            lines
        }
    }

    private fun resolveStatus(lines: List<String>): Elm327ResponseStatus {
        val upperLines = lines.map { it.uppercase() }
        return when {
            upperLines.any { it == "NO DATA" } -> Elm327ResponseStatus.NoData
            upperLines.any { it == "UNABLE TO CONNECT" } -> Elm327ResponseStatus.UnableToConnect
            upperLines.any { it == "?" } -> Elm327ResponseStatus.UnknownCommand
            else -> {
                val busyMarker = BUSY_MARKERS.firstOrNull { marker -> upperLines.any { it == marker } }
                if (busyMarker != null) {
                    Elm327ResponseStatus.BusyProcessing(busyMarker)
                } else {
                    Elm327ResponseStatus.Ok
                }
            }
        }
    }

    private sealed interface ReadResult {
        data class Response(val raw: String) : ReadResult
        data class Closed(val error: ObdError?) : ReadResult
        data object Timeout : ReadResult
    }

    private fun Elm327Response.toExchange(): Elm327Exchange =
        Elm327Exchange(
            command = command.value,
            rawResponse = raw,
            normalizedLines = normalizedLines,
            status = status
        )

    private fun Elm327Response.toHandshakeError(): ObdError =
        if (status is Elm327ResponseStatus.Timeout) {
            ObdError.Timeout(
                operation = ObdOperation.ElmHandshake,
                transportType = null,
                targetLabel = null
            )
        } else {
            // Protocol has no transport/target context by design. The attempt
            // runner can wrap or enrich this failure with adapter details at
            // the connection boundary.
            ObdError.CandidateIsNotElm327(targetLabel = null)
        }

    private fun isPlausibleHandshake(exchanges: List<Elm327Exchange>): Boolean {
        val byCommand = exchanges.associateBy { it.command.uppercase() }
        val reset = byCommand[RESET_COMMAND]
        val identity = byCommand[IDENTITY_COMMAND]

        if (identity?.normalizedLines.orEmpty().any { it.hasNegativeIdentityMarker() }) {
            return false
        }

        val setupOkCount = SETUP_COMMANDS.count { command ->
            byCommand[command]?.normalizedLines.orEmpty().any { it.isOkLine() }
        }
        val identityScore = identity.scoreIdentityLine(weightMarker = 3, weightStandaloneVersion = 2)

        val score =
            identityScore +
                reset.scoreIdentityLine(weightMarker = 2, weightStandaloneVersion = 1) +
                (if (setupOkCount >= 4) 1 else 0) +
                (if (byCommand[PROTOCOL_COMMAND]?.normalizedLines.orEmpty().any { it.isOkLine() }) 1 else 0)

        return identityScore > 0 && score >= MIN_HANDSHAKE_SCORE
    }

    private fun Elm327Exchange?.scoreIdentityLine(
        weightMarker: Int,
        weightStandaloneVersion: Int
    ): Int {
        val lines = this?.normalizedLines.orEmpty()
        return when {
            lines.any { it.hasAdapterMarker() } -> weightMarker
            lines.any { it.isStandaloneVersion() } -> weightStandaloneVersion
            else -> 0
        }
    }

    private fun String.hasAdapterMarker(): Boolean {
        val normalized = normalizeIdentityText()
        return ADAPTER_IDENTITY_MARKERS.any { marker -> normalized.contains(marker) }
    }

    private fun String.hasNegativeIdentityMarker(): Boolean {
        val normalized = normalizeIdentityText()
        return NEGATIVE_IDENTITY_MARKERS.any { marker -> normalized.contains(marker) }
    }

    private fun String.isStandaloneVersion(): Boolean =
        VERSION_PATTERN.matches(trim().uppercase())

    private fun String.isOkLine(): Boolean =
        trim().equals("OK", ignoreCase = true)

    private fun String.normalizeIdentityText(): String =
        uppercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")

    private companion object {
        const val COMMAND_TERMINATOR = "\r"
        const val PROMPT = ">"
        const val RESET_COMMAND = "ATZ"
        const val IDENTITY_COMMAND = "ATI"
        const val PROTOCOL_COMMAND = "ATSP0"
        const val MIN_HANDSHAKE_SCORE = 3

        // ATZ is reset and can take noticeably longer on cheap adapters than
        // setup commands that only change parser flags.
        val RESET_TIMEOUT = 5.seconds
        val INIT_TIMEOUT = 2.seconds

        val SETUP_COMMANDS = setOf(
            "ATE0",
            "ATL0",
            "ATS0",
            "ATH0",
            PROTOCOL_COMMAND
        )

        val HANDSHAKE_COMMANDS = listOf(
            HandshakeStep(Elm327Command(value = RESET_COMMAND, timeout = RESET_TIMEOUT)),
            HandshakeStep(Elm327Command(value = "ATE0", timeout = INIT_TIMEOUT)),
            HandshakeStep(Elm327Command(value = "ATL0", timeout = INIT_TIMEOUT)),
            HandshakeStep(Elm327Command(value = "ATS0", timeout = INIT_TIMEOUT)),
            HandshakeStep(Elm327Command(value = "ATH0", timeout = INIT_TIMEOUT)),
            HandshakeStep(Elm327Command(value = "ATSP0", timeout = INIT_TIMEOUT)),
            HandshakeStep(Elm327Command(value = IDENTITY_COMMAND, timeout = INIT_TIMEOUT))
        )

        val BUSY_MARKERS = setOf(
            "BUFFULL",
            "BUS BUSY",
            "FB ERROR",
            "DATA ERROR"
        )

        // Keep the matcher intentionally broad: different ELM-compatible
        // adapters brand ATI differently, but a plain shell/debug greeting
        // such as "hello>" must not be enough to create an OBD session.
        val ADAPTER_IDENTITY_MARKERS = setOf(
            "ELM",
            "OBD",
            "OBDLINK",
            "STN",
            "VGATE",
            "VLINK",
            "ICAR",
            "VIECAR",
            "KONNWEI",
            "CARISTA",
            "LELINK",
            "VEEPEAK",
            "READY"
        )

        val NEGATIVE_IDENTITY_MARKERS = setOf(
            "HTTP",
            "HTML",
            "SSH",
            "TELNET",
            "LOGIN",
            "JSON"
        )

        val VERSION_PATTERN = Regex("""V?\d+(\.\d+){1,2}""")
    }

    private data class HandshakeStep(
        val command: Elm327Command
    )
}
