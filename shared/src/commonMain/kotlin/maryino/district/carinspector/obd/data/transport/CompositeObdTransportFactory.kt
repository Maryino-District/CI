package maryino.district.carinspector.obd.data.transport

import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget

class CompositeObdTransportFactory(
    private val bluetoothClassicFactory: ObdTransportFactory,
    private val bleFactory: ObdTransportFactory,
    private val wifiTcpFactory: ObdTransportFactory
) : ObdTransportFactory {
    override suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel> =
        when (target) {
            is ObdConnectionTarget.BluetoothClassic -> bluetoothClassicFactory.open(target)
            is ObdConnectionTarget.Ble -> bleFactory.open(target)
            is ObdConnectionTarget.WifiTcp -> wifiTcpFactory.open(target)
        }
}
