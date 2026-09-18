package com.loosecannon.servicetag.core.journal

/** One dropdown row: a free-text category label with an optional creation-time template hint. */
data class CategorySuggestion(val label: String, val suggestedTemplateKey: String?)

/**
 * The category catalog (spec §8). Category is free text; these are suggestions, never authority
 * — typing anything else is equally valid, and nothing reads the category string after creation.
 */
object CategorySuggestions {

    val all: List<CategorySuggestion> = listOf(
        CategorySuggestion("Generator", "power_equipment"),
        CategorySuggestion("Lawn mower", "power_equipment"),
        CategorySuggestion("Snowblower", "power_equipment"),
        CategorySuggestion("UPS", "ups"),
        CategorySuggestion("Battery", null),
        CategorySuggestion("Inverter / charger", null),
        CategorySuggestion("Solar charge controller", null),
        CategorySuggestion("RO system", "ro_water"),
        CategorySuggestion("Hot tub", "hot_tub"),
        CategorySuggestion("HVAC", null),
        CategorySuggestion("Pump", null),
        CategorySuggestion("Other", "generic"),
    )

    /** Exact label match, case-insensitive; null for free text that matches nothing in the catalog. */
    fun templateFor(category: String): String? =
        all.firstOrNull { it.label.equals(category, ignoreCase = true) }?.suggestedTemplateKey
}
