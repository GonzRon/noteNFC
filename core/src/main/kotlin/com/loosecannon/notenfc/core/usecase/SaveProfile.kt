package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.DefinitionKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.ProfileConsumable
import com.loosecannon.notenfc.core.model.ProfileField
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Creates or edits one event profile — a quick action, with the fields it puts on the form (spec
 * §6). Validation is collected, not fail-fast. A field has to name an ENTERED, unarchived
 * definition of the same asset, and may appear once: a DERIVED value is computed, never typed, and
 * an archived definition is one a person has deliberately taken off the forms.
 *
 * Child ids are identity, not bookkeeping (see [ProfileField] / [ProfileConsumable]): on an update
 * a field keeps the id it already had for that definition, and a consumable keeps the id the
 * editor hands back in [ProfileConsumableInput.id]. Only genuinely new rows are minted from [ids].
 */
class SaveProfile(
    private val profiles: ProfileRepository,
    private val definitions: DefinitionRepository,
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(id: ProfileId?, cmd: ProfileCommand): EventProfile {
        assets.get(cmd.assetId) ?: throw NoSuchAsset(cmd.assetId)
        val existing = id?.let { profiles.get(it) ?: throw NoSuchProfile(it) }
        if (existing != null && existing.assetId != cmd.assetId) {
            throw EventOwnership("profile ${existing.id.value} does not belong to asset ${cmd.assetId.value}")
        }
        val problems = mutableListOf<ProfileProblem>()

        val name = cmd.name.trim()
        if (name.isEmpty()) {
            problems += ProfileProblem.NameRequired
        } else {
            // Archived profiles keep their name reserved; a profile may keep its own.
            val taken = profiles.forAsset(cmd.assetId)
                .filter { it.id != existing?.id }
                .any { it.name.equals(name, ignoreCase = true) }
            if (taken) problems += ProfileProblem.NameTaken
        }

        val seen = mutableSetOf<DefinitionId>()
        val fields = mutableListOf<ProfileField>()
        for (input in cmd.fields) {
            val definition = definitions.get(input.definitionId)
            val reason = when {
                definition == null || definition.assetId != cmd.assetId -> "not a definition of this asset"
                definition.kind != DefinitionKind.ENTERED -> "derived values are computed, not entered"
                definition.archivedAt != null -> "archived"
                !seen.add(input.definitionId) -> "listed twice"
                else -> null
            }
            if (reason != null) {
                problems += ProfileProblem.BadField(input.definitionId, reason)
                continue
            }
            fields += ProfileField(
                id = existing?.fields?.firstOrNull { it.definitionId == input.definitionId }?.id ?: ids.newId(),
                definitionId = input.definitionId,
                required = input.required,
                sortOrder = fields.size,
            )
        }

        val consumables = cmd.consumables.mapIndexedNotNull { i, input ->
            val consumableName = input.name.trim()
            val quantity = input.defaultQuantity
            if (consumableName.isEmpty() || (quantity != null && (quantity < 0.0 || !quantity.isFinite()))) {
                problems += ProfileProblem.BadConsumable(i)
                null
            } else {
                ProfileConsumable(
                    id = input.id ?: ids.newId(),
                    name = consumableName,
                    defaultQuantity = quantity,
                    unit = input.unit.trim(),
                    sortOrder = i,
                )
            }
        }

        if (problems.isNotEmpty()) throw ProfileValidation(problems)

        val now = clock.nowMillis()
        val saved = EventProfile(
            id = existing?.id ?: ProfileId(ids.newId()),
            assetId = cmd.assetId,
            name = name,
            eventKind = cmd.eventKind,
            defaultTitle = cmd.defaultTitle.trim().ifEmpty { name },
            templateKey = existing?.templateKey,
            sortOrder = existing?.sortOrder
                ?: ((profiles.forAsset(cmd.assetId).maxOfOrNull { it.sortOrder } ?: -1) + 1),
            archivedAt = existing?.archivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            fields = fields,
            consumables = consumables,
        )
        uow.write { profiles.upsert(saved) }
        return saved
    }
}
