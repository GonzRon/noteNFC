package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.ProfileId

/** One ordered field of a profile, as the editor lists it; position is the row's sort order. */
data class ProfileFieldInput(val definitionId: DefinitionId, val required: Boolean)

/**
 * One consumable suggestion. [id] is the stored [com.loosecannon.servicetag.core.model.ProfileConsumable]'s
 * id when the editor is handing back a row it loaded, and null for a row a person just added —
 * that is how a rename keeps its identity instead of becoming a delete plus an insert.
 */
data class ProfileConsumableInput(
    val id: String?,
    val name: String,
    val defaultQuantity: Double?,
    val unit: String,
)

/** The profile form's raw input. A blank [defaultTitle] falls back to the profile's name. */
data class ProfileCommand(
    val assetId: AssetId,
    val name: String,
    val eventKind: EventKind,
    val defaultTitle: String,
    val fields: List<ProfileFieldInput>,
    val consumables: List<ProfileConsumableInput>,
)

/** One thing wrong with a [ProfileCommand], reported so a form can mark the right row. */
sealed interface ProfileProblem {
    data object NameRequired : ProfileProblem
    data object NameTaken : ProfileProblem

    /** The field's definition can't be offered on this profile; [reason] says why, for the log. */
    data class BadField(val id: DefinitionId, val reason: String) : ProfileProblem
    data class BadConsumable(val index: Int) : ProfileProblem
}

/** Field validation failed; every problem found, collected once rather than fail-fast. */
class ProfileValidation(val problems: List<ProfileProblem>) :
    IllegalArgumentException("invalid profile: $problems")

/** The profile this edit, archive or delete was aimed at is no longer there. */
class NoSuchProfile(id: ProfileId) : IllegalArgumentException("no profile ${id.value}")
