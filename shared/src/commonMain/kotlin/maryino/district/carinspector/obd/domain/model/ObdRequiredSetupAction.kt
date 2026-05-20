package maryino.district.carinspector.obd.domain.model

sealed interface ObdRequiredSetupAction {
    data object OpenAndroidBluetoothSettings : ObdRequiredSetupAction
    data object GrantBluetoothPermission : ObdRequiredSetupAction
    data object EnableBluetooth : ObdRequiredSetupAction
    data object ConnectToAdapterWifi : ObdRequiredSetupAction
    data object GrantLocalNetworkPermission : ObdRequiredSetupAction
}
