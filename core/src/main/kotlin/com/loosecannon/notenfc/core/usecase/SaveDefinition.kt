package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.journal.derivedProblems
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.DefinitionKind
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Creates or edits one measurement definition (spec §6). Validation is collected, not fail-fast:
 * a form gets every [DefinitionProblem] at once. Three things are refused outside that list,
 * because they are not about the field a person typed:
 *
 * - [DefinitionInUse] — `valueType`, `kind` or `key` changed while measurements exist. Those three
 *   are how a stored measurement is read back (and how a backup names it), so data freezes them.
 * - [DefinitionWouldBreakDerived] — the prospective graph check: the edit is substituted into the
 *   asset's definitions in memory and every *other* DERIVED definition is re-checked with
 *   [derivedProblems]. Any of them that was valid and would stop being valid refuses the write.
 *   Relabel, unit, range, decimals and key edits pass it, and so does archiving a source (§5).
 * - [EventOwnership] / [NoSuchAsset] / [NoSuchDefinition] — the edit isn't aimed at a row of this
 *   asset at all.
 *
 * [profiles] is not read: a profile field can only name an ENTERED definition, and a definition
 * with a field pointing at it always has the measurements that freeze its kind — but the editor
 * constructs this use case with the profile repository so a later rule can say so out loud
 * without changing every call site.
 */
class SaveDefinition(
    private val definitions: DefinitionRepository,
    private val events: EventRepository,
    @Suppress("unused") private val profiles: ProfileRepository,
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(id: DefinitionId?, cmd: DefinitionCommand): MeasurementDefinition {
        assets.get(cmd.assetId) ?: throw NoSuchAsset(cmd.assetId)
        val existing = id?.let { definitions.get(it) ?: throw NoSuchDefinition(it) }
        if (existing != null && existing.assetId != cmd.assetId) {
            throw EventOwnership("definition ${existing.id.value} does not belong to asset ${cmd.assetId.value}")
        }
        val siblings = definitions.forAsset(cmd.assetId)
        val problems = mutableListOf<DefinitionProblem>()

        val label = cmd.label.trim()
        if (label.isEmpty()) problems += DefinitionProblem.LabelRequired

        // Archived rows keep their key, so every sibling but this one counts as taken.
        val taken = siblings.filter { it.id != existing?.id }.map { it.key }.toSet()
        val typed = cmd.key.trim()
        val key = when {
            typed.isNotEmpty() -> {
                if (!KEY_PATTERN.matches(typed)) problems += DefinitionProblem.BadKey
                else if (typed in taken) problems += DefinitionProblem.KeyTaken
                typed
            }
            existing != null -> existing.key            // blank on an update means "leave it alone"
            else -> {
                val base = slugify(label)
                if (base.isEmpty()) {
                    if (label.isNotEmpty()) problems += DefinitionProblem.BadKey
                    ""
                } else {
                    dedupedKey(base, taken)
                }
            }
        }

        if (cmd.decimals !in 0..4) problems += DefinitionProblem.BadDecimals
        val isNumber = cmd.valueType == ValueType.NUMBER
        if (!isNumber && (cmd.rangeLow != null || cmd.rangeHigh != null)) {
            problems += DefinitionProblem.RangeOnNonNumber
        }
        if (cmd.rangeLow != null && cmd.rangeHigh != null && cmd.rangeLow > cmd.rangeHigh) {
            problems += DefinitionProblem.RangeOrder
        }
        if (cmd.isMeter && !isNumber) problems += DefinitionProblem.MeterOnNonNumber

        val now = clock.nowMillis()
        val candidate = MeasurementDefinition(
            id = existing?.id ?: DefinitionId(ids.newId()),
            assetId = cmd.assetId,
            key = key,
            label = label,
            unit = cmd.unit.trim(),
            valueType = cmd.valueType,
            decimals = cmd.decimals,
            rangeLow = cmd.rangeLow,
            rangeHigh = cmd.rangeHigh,
            isMeter = cmd.isMeter,
            sortOrder = existing?.sortOrder ?: ((siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            archivedAt = existing?.archivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            kind = cmd.kind,
            derived = cmd.spec(),
        )

        // A source on another asset has to be resolved by id: it is not among the asset's own
        // definitions, and derivedProblems can only report SourceOtherAsset for a row it can see.
        val named = candidate.derived?.let { listOf(it.sourceA, it.sourceB) }.orEmpty()
            .distinct() - siblings.map { it.id }.toSet()
        val foreign = named.mapNotNull { definitions.get(it) }
        val after = sourceMap(siblings, candidate, foreign)
        problems += candidate.derivedProblems(after).map { DefinitionProblem.Derived(it) }

        if (problems.isNotEmpty()) throw DefinitionValidation(problems)

        if (existing != null) {
            val frozen = existing.valueType != candidate.valueType ||
                existing.kind != candidate.kind ||
                existing.key != candidate.key
            if (frozen) {
                val measurements = events.countMeasurementsFor(existing.id)
                if (measurements > 0) throw DefinitionInUse(existing.id, measurements)
            }
            val before = siblings.associateBy { it.id }
            val broken = siblings
                .filter { it.id != candidate.id && it.kind == DefinitionKind.DERIVED }
                .filter { it.derivedProblems(before).isEmpty() && it.derivedProblems(after).isNotEmpty() }
                .map { it.id }
            if (broken.isNotEmpty()) throw DefinitionWouldBreakDerived(candidate.id, broken)
        }

        uow.write { definitions.upsert(candidate) }
        return candidate
    }
}
