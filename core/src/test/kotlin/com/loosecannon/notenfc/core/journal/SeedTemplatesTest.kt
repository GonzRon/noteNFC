package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.ValueType
import kotlin.test.*

class SeedTemplatesTest {
    @Test fun fiveTemplatesWithStableKeys() =
        assertEquals(listOf("hot_tub", "power_equipment", "ups", "ro_water", "generic"), SeedTemplates.all.map { it.key })

    @Test fun definitionKeysUniquePerTemplate() = SeedTemplates.all.forEach { t ->
        assertEquals(t.definitions.size, t.definitions.map { it.key }.toSet().size, t.key)
    }

    @Test fun everyProfileFieldReferencesADefinitionInTheSameTemplate() = SeedTemplates.all.forEach { t ->
        val keys = t.definitions.map { it.key }.toSet()
        t.profiles.flatMap { it.fields }.forEach { (key, _) -> assertTrue(key in keys, "${t.key}: $key") }
    }

    @Test fun genericHasNoDefinitionsAndOneNoteProfile() {
        val g = SeedTemplates.byKey("generic")!!
        assertTrue(g.definitions.isEmpty()); assertEquals(listOf("Note"), g.profiles.map { it.name })
    }

    @Test fun roWaterShipsWithoutRangesAndUpsHasABoolean() {
        SeedTemplates.byKey("ro_water")!!.definitions.forEach { assertNull(it.rangeLow); assertNull(it.rangeHigh) }
        assertEquals(ValueType.BOOLEAN, SeedTemplates.byKey("ups")!!.definitions.first { it.key == "test_passed" }.valueType)
        assertTrue(SeedTemplates.byKey("power_equipment")!!.definitions.single().isMeter)
    }
}
