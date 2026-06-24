package maryino.district.carinspector.obd.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AndroidBluetoothPermissionChecker(
    context: Context
) {
    private val appContext = context.applicationContext

    fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
