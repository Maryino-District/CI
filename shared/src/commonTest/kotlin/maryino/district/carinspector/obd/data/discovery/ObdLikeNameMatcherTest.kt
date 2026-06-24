package maryino.district.carinspector.obd.data.discovery

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdLikeNameMatcherTest {
    private val matcher = ObdLikeNameMatcher.Default

    @Test
    fun matchesObdNamesIgnoringCaseAndSeparators() {
        assertTrue(matcher.matches("obd ii"))
        assertTrue(matcher.matches("OBD-II"))
        assertTrue(matcher.matches("elm_327"))
        assertTrue(matcher.matches("V LINK"))
    }

    @Test
    fun matchesKnownVendorNames() {
        assertTrue(matcher.matches("Vgate iCar Pro"))
        assertTrue(matcher.matches("OBDLink CX"))
        assertTrue(matcher.matches("Viecar BLE"))
        assertTrue(matcher.matches("Car Scanner Adapter"))
    }

    @Test
    fun doesNotMatchUnknownBlankOrNullNames() {
        assertFalse(matcher.matches("Headset"))
        assertFalse(matcher.matches("Keyboard"))
        assertFalse(matcher.matches("   "))
        assertFalse(matcher.matches(null))
    }
}
