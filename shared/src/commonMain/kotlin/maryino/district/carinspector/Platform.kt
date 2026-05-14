package maryino.district.carinspector

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform