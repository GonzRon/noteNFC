package com.loosecannon.servicetag.core.journal

import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.DerivedFormula
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ValueType

/** Everything that can be wrong about a [MeasurementDefinition]'s DERIVED shape (spec §4). */
sealed interface DerivedProblem {
    data object NotNumber : DerivedProblem
    data object IsMeter : DerivedProblem
    data object MissingSpec : DerivedProblem
    data object SpecOnEntered : DerivedProblem
    data object SameSource : DerivedProblem
    data class UnknownSource(val id: DefinitionId) : DerivedProblem
    data class SourceOtherAsset(val id: DefinitionId) : DerivedProblem
    data class SourceNotEntered(val id: DefinitionId) : DerivedProblem
    data class SourceNotNumber(val id: DefinitionId) : DerivedProblem
    data class SourceIsMeter(val id: DefinitionId) : DerivedProblem
}

/**
 * Invariants (spec §4): a DERIVED definition has valueType == NUMBER, isMeter == false, a
 * `derived` spec whose two sources are distinct, exist, belong to the same asset, are ENTERED,
 * NUMBER and not meters; an ENTERED definition has `derived == null`. [sources] need only contain the candidate
 * source ids — callers typically pass every definition of the asset, keyed by id.
 */
fun MeasurementDefinition.derivedProblems(sources: Map<DefinitionId, MeasurementDefinition>): List<DerivedProblem> {
    val problems = mutableListOf<DerivedProblem>()
    if (kind == DefinitionKind.ENTERED) {
        if (derived != null) problems += DerivedProblem.SpecOnEntered
        return problems
    }
    if (valueType != ValueType.NUMBER) problems += DerivedProblem.NotNumber
    if (isMeter) problems += DerivedProblem.IsMeter
    val spec = derived
    if (spec == null) {
        problems += DerivedProblem.MissingSpec
        return problems
    }
    if (spec.sourceA == spec.sourceB) problems += DerivedProblem.SameSource
    for (id in listOf(spec.sourceA, spec.sourceB).distinct()) {
        val source = sources[id]
        if (source == null) {
            problems += DerivedProblem.UnknownSource(id)
            continue
        }
        if (source.assetId != assetId) problems += DerivedProblem.SourceOtherAsset(id)
        if (source.kind != DefinitionKind.ENTERED) problems += DerivedProblem.SourceNotEntered(id)
        if (source.valueType != ValueType.NUMBER) problems += DerivedProblem.SourceNotNumber(id)
        // A meter is a monotonic counter, so a percent drop between two of its readings is a
        // different question from one between two independent measurements: not a valid source.
        if (source.isMeter) problems += DerivedProblem.SourceIsMeter(id)
    }
    return problems
}

fun MeasurementDefinition.derivedSpecValid(sources: Map<DefinitionId, MeasurementDefinition>): Boolean =
    derivedProblems(sources).isEmpty()

/** Pure computation of a DERIVED definition's value; nothing here is ever stored (spec §5). */
object Derived {
    /**
     * Same-event only. Null unless [def] is DERIVED, both sources are present and unarchived, and
     * both have a value on [event]; A and B are always read from [event]'s own measurements —
     * latest A and latest B from different events are never combined.
     */
    fun compute(def: MeasurementDefinition, event: AssetEvent, sources: Map<DefinitionId, MeasurementDefinition>): Double? {
        if (def.kind != DefinitionKind.DERIVED) return null
        val spec = def.derived ?: return null
        val sourceA = sources[spec.sourceA] ?: return null
        val sourceB = sources[spec.sourceB] ?: return null
        if (sourceA.archivedAt != null || sourceB.archivedAt != null) return null
        val a = event.measurements.firstOrNull { it.definitionId == spec.sourceA }?.valueNum ?: return null
        val b = event.measurements.firstOrNull { it.definitionId == spec.sourceB }?.valueNum ?: return null
        return when (spec.formula) {
            DerivedFormula.PERCENT_DROP -> if (a == 0.0) null else ((a - b) / a * 100).takeIf { it.isFinite() }
        }
    }
}
