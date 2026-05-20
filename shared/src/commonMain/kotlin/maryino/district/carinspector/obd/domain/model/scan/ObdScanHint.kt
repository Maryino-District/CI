package maryino.district.carinspector.obd.domain.model.scan

/**
 * Represents non-blocking guidance that can be shown during adapter discovery.
 *
 * Hints are not errors: scanning can continue and candidates can still appear
 * while the UI presents an optional next step to the user.
 */
sealed interface ObdScanHint {
    /** Suggests pairing a Bluetooth Classic adapter in Android settings before retrying scan. */
    data object PairBluetoothClassicInAndroidSettings : ObdScanHint
}
