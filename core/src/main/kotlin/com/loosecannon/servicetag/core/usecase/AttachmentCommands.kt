package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.AttachmentKind
import com.loosecannon.servicetag.core.model.AttachmentProblem

/**
 * Two outcomes, no exception, because every one of [AttachmentProblem]'s members is something the
 * DOCUMENTS section draws rather than something that has gone wrong. `kotlin.Result` carries one
 * type parameter, so spec §6's `Result<Attachment, AttachmentProblem>` is spelled like this.
 */
sealed interface AttachmentResult<out T> {
    data class Ok<out T>(val value: T) : AttachmentResult<T>
    data class Refused(val problem: AttachmentProblem) : AttachmentResult<Nothing>
}

/**
 * What the picker or the camera knows about a file it is handing over. [sizeBytes] is null when
 * the provider reports none (a camera capture): the guard then runs on what the store actually
 * wrote instead of before the copy.
 */
data class AddAttachmentCommand(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    /** Null lets [com.loosecannon.servicetag.core.model.AttachmentKinds] choose the default. */
    val kind: AttachmentKind? = null,
    val capturedOn: String? = null,
    val notes: String = "",
    val fromCamera: Boolean = false,
)

/** What the edit sheet can change. The locator is not here: a rename never moves bytes. */
data class UpdateAttachmentCommand(
    val displayName: String,
    val kind: AttachmentKind,
    val capturedOn: String? = null,
    val notes: String = "",
)
