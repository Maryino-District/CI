package maryino.district.carinspector.obd.data.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.adapter.WifiCandidateSource

class CompositeObdTransportFactoryTest {
    @Test
    fun routesEachTargetToMatchingTransportFactory() = runTest {
        val classicChannel = FakeObdByteChannel()
        val bleChannel = FakeObdByteChannel()
        val wifiChannel = FakeObdByteChannel()
        val classicFactory = RecordingTransportFactory(ObdResult.Success(classicChannel))
        val bleFactory = RecordingTransportFactory(ObdResult.Success(bleChannel))
        val wifiFactory = RecordingTransportFactory(ObdResult.Success(wifiChannel))
        val factory = CompositeObdTransportFactory(
            bluetoothClassicFactory = classicFactory,
            bleFactory = bleFactory,
            wifiTcpFactory = wifiFactory
        )
        val classicTarget = ObdConnectionTarget.BluetoothClassic(
            deviceAddress = "00:11:22:33:44:55",
            deviceName = "ELM327"
        )
        val bleTarget = ObdConnectionTarget.Ble(
            peripheralId = "ble-1",
            deviceName = "OBD BLE",
            knownProfileId = null,
            discoveredServiceUuids = emptyList(),
            discoveredAt = Instant.parse("2026-06-01T00:00:00Z")
        )
        val wifiTarget = ObdConnectionTarget.WifiTcp(
            host = "192.168.0.10",
            port = 35000,
            source = WifiCandidateSource.StaticKnown("192.168.0.10")
        )

        val classicResult = factory.open(classicTarget)
        val bleResult = factory.open(bleTarget)
        val wifiResult = factory.open(wifiTarget)

        assertTrue(classicChannel === classicResult.successValue())
        assertTrue(bleChannel === bleResult.successValue())
        assertTrue(wifiChannel === wifiResult.successValue())
        assertEquals(listOf<ObdConnectionTarget>(classicTarget), classicFactory.openedTargets)
        assertEquals(listOf<ObdConnectionTarget>(bleTarget), bleFactory.openedTargets)
        assertEquals(listOf<ObdConnectionTarget>(wifiTarget), wifiFactory.openedTargets)
    }

    private class RecordingTransportFactory(
        private val result: ObdResult<ObdByteChannel>
    ) : ObdTransportFactory {
        val openedTargets = mutableListOf<ObdConnectionTarget>()

        override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> {
            openedTargets += target
            return result
        }
    }

    private class FakeObdByteChannel : ObdByteChannel {
        override val incoming: Flow<ObdByteChannelEvent> = emptyFlow()

        override suspend fun write(bytes: ByteArray): ObdResult<Unit> =
            ObdResult.Success(Unit)

        override suspend fun close() = Unit
    }

    private fun ObdResult<ObdByteChannel>.successValue(): ObdByteChannel {
        assertTrue(this is ObdResult.Success)
        return value
    }
}
