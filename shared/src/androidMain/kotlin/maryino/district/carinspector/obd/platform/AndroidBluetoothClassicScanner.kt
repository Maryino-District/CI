package maryino.district.carinspector.obd.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import maryino.district.carinspector.obd.data.discovery.BluetoothClassicBondedDevice
import maryino.district.carinspector.obd.data.discovery.BluetoothClassicBondedDeviceMapper
import maryino.district.carinspector.obd.data.discovery.ObdAdapterDiscovery
import maryino.district.carinspector.obd.data.discovery.ObdDiscoveryEvent
import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class AndroidBluetoothClassicScanner(
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val permissionChecker: AndroidBluetoothPermissionChecker,
    private val mapper: BluetoothClassicBondedDeviceMapper = BluetoothClassicBondedDeviceMapper()
) : ObdAdapterDiscovery {
    constructor(
        context: Context,
        mapper: BluetoothClassicBondedDeviceMapper = BluetoothClassicBondedDeviceMapper()
    ) : this(
        adapterProvider = SystemAndroidBluetoothAdapterProvider(context),
        permissionChecker = AndroidBluetoothPermissionChecker(context),
        mapper = mapper
    )

    override fun scan(request: ObdScanRequest): Flow<ObdDiscoveryEvent> = flow {
        if (ObdTransportType.BluetoothClassic !in request.transportTypes) return@flow

        if (!request.includeBluetoothClassicCandidates) {
            emitFinished()
            return@flow
        }

        val adapter = adapterProvider.bluetoothAdapter()
        if (adapter == null) {
            emitFailure(ObdError.UnsupportedTransport(ObdTransportType.BluetoothClassic))
            emitFinished()
            return@flow
        }

        if (!permissionChecker.hasBluetoothConnectPermission()) {
            emitPermissionDenied()
            emitFinished()
            return@flow
        }

        val isEnabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            emitPermissionDenied()
            emitFinished()
            return@flow
        }

        if (!isEnabled) {
            emitFailure(ObdError.BluetoothDisabled(ObdRequiredSetupAction.EnableBluetooth))
            emitFinished()
            return@flow
        }

        val bondedDevices = try {
            adapter.bondedDevices.orEmpty().map { device ->
                BluetoothClassicBondedDevice(
                    address = device.address,
                    name = device.name
                )
            }
        } catch (_: SecurityException) {
            emitPermissionDenied()
            emitFinished()
            return@flow
        }

        if (bondedDevices.isEmpty()) {
            emitFailure(
                ObdError.NoBondedClassicDevices(
                    action = ObdRequiredSetupAction.OpenAndroidBluetoothSettings
                )
            )
            emitFinished()
            return@flow
        }

        bondedDevices
            .mapNotNull { device -> mapper.map(device) }
            .forEach { adapterCandidate ->
                emit(ObdDiscoveryEvent.CandidateFound(adapterCandidate))
            }

        emitFinished()
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ObdDiscoveryEvent>.emitPermissionDenied() {
        emitFailure(
            ObdError.PermissionDenied(
                action = ObdRequiredSetupAction.GrantBluetoothPermission
            )
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ObdDiscoveryEvent>.emitFailure(error: ObdError) {
        emit(
            ObdDiscoveryEvent.TransportFailed(
                type = ObdTransportType.BluetoothClassic,
                error = error
            )
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ObdDiscoveryEvent>.emitFinished() {
        emit(ObdDiscoveryEvent.TransportFinished(ObdTransportType.BluetoothClassic))
    }
}

fun interface AndroidBluetoothAdapterProvider {
    fun bluetoothAdapter(): BluetoothAdapter?
}

class SystemAndroidBluetoothAdapterProvider(
    context: Context
) : AndroidBluetoothAdapterProvider {
    private val appContext = context.applicationContext

    override fun bluetoothAdapter(): BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
}
