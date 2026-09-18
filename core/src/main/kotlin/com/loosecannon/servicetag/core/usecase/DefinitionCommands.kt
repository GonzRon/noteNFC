package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.journal.DerivedProblem
import com.loosecannon.servicetag.core.journal.derivedProblems
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.DerivedFormula
import com.loosecannon.servicetag.core.model.DerivedSpec
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.model.ValueType

/**
 * The definition form's raw input. [key] is blank when the editor hasn't been given one: on a
 * create it is generated from [label], on an update the stored key is kept. [formula], [sourceA]
 * and [sourceB] are read only when [kind] is DERIVED; a DERIVED command missing any of them is a
 * [DerivedProblem.MissingSpec], not a silently ENTERED definition.
 */
data class DefinitionCommand(
    val assetId: AssetId,
    val key: String,            // "" = generate (create) or keep (update)
    val label: String,
    val unit: String,
    val kind: DefinitionKind,
    val valueType: ValueType,
    val decimals: Int,
    val rangeLow: Double?,
    val rangeHigh: Double?,
    val isMeter: Boolean,
    val formula: DerivedFormula?,
    val sourceA: DefinitionId?,
    val sourceB: DefinitionId?,
)

/** One thing wrong with a [DefinitionCommand], reported so a form can mark the right field. */
sealed interface DefinitionProblem {
    data object LabelRequired : DefinitionProblem
    data object BadKey : DefinitionProblem
    data object KeyTaken : DefinitionProblem
    data object BadDecimals : DefinitionProblem
    data object RangeOrder : DefinitionProblem
    data object RangeOnNonNumber : DefinitionProblem
    data object MeterOnNonNumber : DefinitionProblem

    /** A DERIVED shape invariant from spec §4, straight out of [derivedProblems]. */
    data class Derived(val p: DerivedProblem) : DefinitionProblem
}

/** Field validation failed; every problem found, collected once rather than fail-fast. */
class DefinitionValidation(val problems: List<DefinitionProblem>) :
    IllegalArgumentException("invalid definition: $problems")

/**
 * The definition already has measurements, so the parts a stored measurement is interpreted
 * through — `valueType`, `kind` and `key` — can no longer change.
 */
class DefinitionInUse(val id: DefinitionId, val measurements: Int) :
    IllegalStateException("definition ${id.value} has $measurements measurements")

/** [DeleteDefinition] refused: something still points at the definition. Every referrer is named. */
class DefinitionReferenced(
    val id: DefinitionId,
    val measurements: Int,
    val derivedBy: List<DefinitionId>,
    val profiles: List<ProfileId>,
) : IllegalStateException(
    "definition ${id.value} is referenced by $measurements measurements, " +
        "${derivedBy.size} derived definitions and ${profiles.size} profiles",
)

/**
 * The prospective graph check (spec §6): committing this edit would leave one of the asset's
 * existing DERIVED definitions invalid — a source turning TEXT/BOOLEAN, turning DERIVED, or
 * gaining the meter flag. Nothing was written. Label, unit, range, decimals, key and archive
 * edits of a source never land here.
 */
class DefinitionWouldBreakDerived(val id: DefinitionId, val dependentDerivedIds: List<DefinitionId>) :
    IllegalStateException("definition ${id.value} is a source of ${dependentDerivedIds.map { it.value }}")

/**
 * Turning this ENTERED definition DERIVED would leave the named profiles offering a field that
 * can no longer be typed into (a derived value is computed, never entered). Nothing was written;
 * drop the field from those profiles first, or add a new derived definition instead of converting
 * this one.
 */
class DefinitionWouldBreakProfiles(val id: DefinitionId, val profileIds: List<ProfileId>) :
    IllegalStateException("definition ${id.value} is a field of ${profileIds.map { it.value }}")

/** The definition this edit, archive or delete was aimed at is no longer there. */
class NoSuchDefinition(id: DefinitionId) : IllegalArgumentException("no definition ${id.value}")

internal const val MAX_KEY_LENGTH = 40

/** The key slug of spec §6: lower-case, starts with a letter, at most [MAX_KEY_LENGTH] characters. */
internal val KEY_PATTERN = Regex("^[a-z][a-z0-9_]{0,39}\$")

/**
 * The label turned into a slug: lowercased, runs of anything outside `[a-z0-9]` collapsed to one
 * `_`, trimmed of `_`, prefixed `k_` when it would otherwise start with a digit, and cut to
 * [MAX_KEY_LENGTH]. Returns "" for a label with nothing slug-able in it at all, which the caller
 * reports as [DefinitionProblem.BadKey] rather than inventing a name.
 *
 * Public because the definition editor previews the key it is about to generate while the user has
 * not typed one (spec §9): the preview and the key [SaveDefinition] actually mints for a blank
 * `key` come from this one function, so the two can never drift apart.
 */
fun slugify(label: String): String {
    val collapsed = label.lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
    if (collapsed.isEmpty()) return ""
    val prefixed = if (collapsed.first().isDigit()) "k_$collapsed" else collapsed
    return prefixed.take(MAX_KEY_LENGTH)
}

/** [base], or `base_2`, `base_3`… until it is free of [taken] — still inside [MAX_KEY_LENGTH]. */
internal fun dedupedKey(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var n = 2
    while (true) {
        val suffix = "_$n"
        val candidate = base.take(MAX_KEY_LENGTH - suffix.length).trimEnd('_') + suffix
        if (candidate !in taken) return candidate
        n += 1
    }
}

/**
 * The asset's definitions keyed by id, with [edited] substituted (or added), plus any definition
 * [edited] names as a source that lives elsewhere — so [derivedProblems] can tell
 * [DerivedProblem.SourceOtherAsset] (a real row on another asset) from
 * [DerivedProblem.UnknownSource] (no such row anywhere).
 */
internal fun sourceMap(
    assetDefinitions: List<MeasurementDefinition>,
    edited: MeasurementDefinition,
    foreign: List<MeasurementDefinition>,
): Map<DefinitionId, MeasurementDefinition> {
    val map = LinkedHashMap<DefinitionId, MeasurementDefinition>()
    assetDefinitions.forEach { map[it.id] = it }
    foreign.forEach { map[it.id] = it }
    map[edited.id] = edited
    return map
}

/** The spec a DERIVED command carries, or null when it is ENTERED or hasn't named both sources. */
internal fun DefinitionCommand.spec(): DerivedSpec? {
    if (kind != DefinitionKind.DERIVED) return null
    val formula = formula ?: return null
    val a = sourceA ?: return null
    val b = sourceB ?: return null
    return DerivedSpec(formula, a, b)
}
