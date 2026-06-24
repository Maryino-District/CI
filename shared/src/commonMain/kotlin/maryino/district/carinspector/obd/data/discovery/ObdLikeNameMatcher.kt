package maryino.district.carinspector.obd.data.discovery

class ObdLikeNameMatcher(
    markers: List<String> = DefaultMarkers
) {
    private val normalizedMarkers = markers
        .map { marker -> marker.normalizedObdName() }
        .filter { marker -> marker.isNotBlank() }

    fun matches(name: String?): Boolean {
        val normalizedName = name?.normalizedObdName() ?: return false
        if (normalizedName.isBlank()) return false

        return normalizedMarkers.any { marker -> normalizedName.contains(marker) }
    }

    companion object {
        private val DefaultMarkers = listOf(
            "OBDII",
            "OBD-II",
            "OBD2",
            "ELM327",
            "V-LINK",
            "Vgate",
            "OBDLink",
            "Viecar",
            "Car Scanner",
            "iCar"
        )

        val Default = ObdLikeNameMatcher(DefaultMarkers)

        private fun String.normalizedObdName(): String =
            lowercase().filterNot { char ->
                char == ' ' ||
                    char == '\t' ||
                    char == '\n' ||
                    char == '\r' ||
                    char == '-' ||
                    char == '_'
            }
    }
}
