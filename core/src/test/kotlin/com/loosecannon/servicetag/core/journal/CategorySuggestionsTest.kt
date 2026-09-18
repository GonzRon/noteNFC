package com.loosecannon.servicetag.core.journal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategorySuggestionsTest {

    @Test fun catalogHasTheTwelveLabels() {
        val expected = listOf(
            "Generator", "Lawn mower", "Snowblower", "UPS", "Battery", "Inverter / charger",
            "Solar charge controller", "RO system", "Hot tub", "HVAC", "Pump", "Other",
        )
        assertEquals(expected, CategorySuggestions.all.map { it.label })
    }

    @Test fun hintsMatchTheSeeds() {
        assertEquals("ro_water", CategorySuggestions.templateFor("RO system"))
        assertEquals("hot_tub", CategorySuggestions.templateFor("Hot tub"))
        assertEquals("power_equipment", CategorySuggestions.templateFor("Generator"))
        assertEquals("power_equipment", CategorySuggestions.templateFor("Lawn mower"))
        assertEquals("power_equipment", CategorySuggestions.templateFor("Snowblower"))
        assertEquals("ups", CategorySuggestions.templateFor("UPS"))
        assertEquals("generic", CategorySuggestions.templateFor("Other"))
        assertNull(CategorySuggestions.templateFor("Battery"))
    }

    @Test fun templateForIsCaseInsensitiveAndNullForFreeText() {
        assertEquals("ro_water", CategorySuggestions.templateFor("ro system"))
        assertEquals("ups", CategorySuggestions.templateFor("ups"))
        assertNull(CategorySuggestions.templateFor("Whatever I felt like typing"))
    }
}
