package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.ValueType

/** A template's own row, before [com.loosecannon.notenfc.core.usecase.ApplyTemplate] mints an id for it. */
data class TemplateDefinition(
    val key: String, val label: String, val unit: String,
    val valueType: ValueType, val decimals: Int,
    val rangeLow: Double?, val rangeHigh: Double?,
    val isMeter: Boolean,
)

data class TemplateConsumable(val name: String, val defaultQuantity: Double?, val unit: String)

data class TemplateProfile(
    val name: String, val eventKind: EventKind, val defaultTitle: String,
    val fields: List<Pair<String, Boolean>>,   // definition key to required
    val consumables: List<TemplateConsumable>,
)

data class Template(
    val key: String, val name: String,
    val definitions: List<TemplateDefinition>,
    val profiles: List<TemplateProfile>,
)

/**
 * The five starter templates (spec §7). Typed Kotlin, not JSON, so a typo is a compile error.
 * Keys are stable identifiers a template's rows carry as provenance; labels are what the user
 * sees. Starter data only — no code past [com.loosecannon.notenfc.core.usecase.ApplyTemplate] may
 * branch on a template key.
 */
object SeedTemplates {

    private val hotTubConsumables = listOf(
        TemplateConsumable("Chlorine", null, "oz"),
        TemplateConsumable("pH reducer", null, "oz"),
        TemplateConsumable("pH increaser", null, "oz"),
        TemplateConsumable("Alkalinity increaser", null, "oz"),
    )

    private val hotTub = Template(
        key = "hot_tub",
        name = "Hot tub",
        definitions = listOf(
            TemplateDefinition("ph", "pH", "", ValueType.NUMBER, 1, 7.2, 7.8, isMeter = false),
            TemplateDefinition("free_chlorine", "Free chlorine", "ppm", ValueType.NUMBER, 1, 1.0, 3.0, isMeter = false),
            TemplateDefinition("alkalinity", "Alkalinity", "ppm", ValueType.NUMBER, 0, 80.0, 120.0, isMeter = false),
            TemplateDefinition("calcium_hardness", "Calcium hardness", "ppm", ValueType.NUMBER, 0, 150.0, 250.0, isMeter = false),
            TemplateDefinition("water_temp", "Water temperature", "°F", ValueType.NUMBER, 0, null, null, isMeter = false),
        ),
        profiles = listOf(
            TemplateProfile(
                name = "Water test", eventKind = EventKind.MEASUREMENT, defaultTitle = "Water test",
                fields = listOf(
                    "ph" to true,
                    "free_chlorine" to true,
                    "alkalinity" to false,
                    "calcium_hardness" to false,
                    "water_temp" to false,
                ),
                consumables = hotTubConsumables,
            ),
            TemplateProfile(
                name = "Treatment", eventKind = EventKind.TREATMENT, defaultTitle = "Treatment",
                fields = listOf("ph" to false, "free_chlorine" to false),
                consumables = hotTubConsumables,
            ),
        ),
    )

    private val powerEquipment = Template(
        key = "power_equipment",
        name = "Power equipment",
        definitions = listOf(
            TemplateDefinition("engine_hours", "Engine hours", "h", ValueType.NUMBER, 1, null, null, isMeter = true),
        ),
        profiles = listOf(
            TemplateProfile(
                name = "Oil change", eventKind = EventKind.MAINTENANCE, defaultTitle = "Oil change",
                fields = listOf("engine_hours" to true),
                consumables = listOf(
                    TemplateConsumable("Engine oil", null, "qt"),
                    TemplateConsumable("Oil filter", null, "pcs"),
                ),
            ),
            TemplateProfile(
                name = "Service", eventKind = EventKind.MAINTENANCE, defaultTitle = "Service",
                fields = listOf("engine_hours" to false),
                consumables = emptyList(),
            ),
            TemplateProfile(
                name = "Season start", eventKind = EventKind.SEASON_START, defaultTitle = "Season start",
                fields = listOf("engine_hours" to false),
                consumables = emptyList(),
            ),
            TemplateProfile(
                name = "Season end", eventKind = EventKind.SEASON_END, defaultTitle = "Season end",
                fields = listOf("engine_hours" to false),
                consumables = emptyList(),
            ),
        ),
    )

    private val ups = Template(
        key = "ups",
        name = "UPS",
        definitions = listOf(
            TemplateDefinition("battery_voltage", "Battery voltage", "V", ValueType.NUMBER, 1, null, null, isMeter = false),
            TemplateDefinition("load_percent", "Load", "%", ValueType.NUMBER, 0, null, null, isMeter = false),
            TemplateDefinition("runtime_minutes", "Runtime", "min", ValueType.NUMBER, 0, null, null, isMeter = false),
            TemplateDefinition("test_passed", "Passed", "", ValueType.BOOLEAN, 0, null, null, isMeter = false),
        ),
        profiles = listOf(
            TemplateProfile(
                name = "Load test", eventKind = EventKind.INSPECTION, defaultTitle = "Load test",
                fields = listOf(
                    "battery_voltage" to false,
                    "load_percent" to false,
                    "runtime_minutes" to false,
                    "test_passed" to true,
                ),
                consumables = emptyList(),
            ),
            TemplateProfile(
                name = "Battery replacement", eventKind = EventKind.REPLACEMENT, defaultTitle = "Battery replacement",
                fields = listOf("battery_voltage" to false),
                consumables = listOf(TemplateConsumable("Battery", null, "pcs")),
            ),
        ),
    )

    private val roWater = Template(
        key = "ro_water",
        name = "RO water",
        definitions = listOf(
            TemplateDefinition("tds_prefilter", "Pre-filter TDS", "ppm", ValueType.NUMBER, 0, null, null, isMeter = false),
            TemplateDefinition("tds_post_membrane", "Post-membrane TDS", "ppm", ValueType.NUMBER, 0, null, null, isMeter = false),
            TemplateDefinition("tds_output", "Output TDS", "ppm", ValueType.NUMBER, 0, null, null, isMeter = false),
        ),
        profiles = listOf(
            TemplateProfile(
                name = "TDS test", eventKind = EventKind.MEASUREMENT, defaultTitle = "TDS test",
                fields = listOf(
                    "tds_prefilter" to true,
                    "tds_post_membrane" to true,
                    "tds_output" to true,
                ),
                consumables = emptyList(),
            ),
        ),
    )

    private val generic = Template(
        key = "generic",
        name = "Generic",
        definitions = emptyList(),
        profiles = listOf(
            TemplateProfile(
                name = "Note", eventKind = EventKind.NOTE, defaultTitle = "Note",
                fields = emptyList(),
                consumables = emptyList(),
            ),
        ),
    )

    val all: List<Template> = listOf(hotTub, powerEquipment, ups, roWater, generic)

    fun byKey(key: String): Template? = all.firstOrNull { it.key == key }
}
