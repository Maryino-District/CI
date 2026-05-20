package maryino.district.carinspector.obd.domain.model.transport

/**
 * Explains whether a transport can participate in scan/connect flows right now.
 *
 * The status keeps platform and permission details below the UI while still
 * giving presentation enough information to show the next useful state.
 */
sealed interface ObdTransportStatus {
    /** The transport can be used for discovery or connection attempts. */
    data object Available : ObdTransportStatus

    /** The current platform cannot support this transport at all. */
    data object UnsupportedOnPlatform : ObdTransportStatus

    /** The app needs a runtime permission before this transport can be used. */
    data object PermissionRequired : ObdTransportStatus

    /** The OS-level radio or network capability is disabled. */
    data object DisabledBySystem : ObdTransportStatus

    /** The user must complete setup outside the app, such as pairing or Wi-Fi selection. */
    data object RequiresExternalSetup : ObdTransportStatus
}
