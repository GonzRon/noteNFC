package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.AttachmentId

/** Anything that stops a backup from being read. Never thrown while writing one. */
sealed class BackupException(message: String) : Exception(message)

/** The file was written by a newer build than this one understands; refuse rather than guess. */
class BackupNewerFormat(val found: Int, val supported: Int) :
    BackupException("backup format $found is newer than supported $supported")

/** Missing entries, a hash mismatch, unparsable JSON, or a value this format cannot name. */
class BackupCorrupt(reason: String) : BackupException(reason)

/** The artifacts archive was written by a newer build than this one understands. */
class ArtifactsNewerFormat(val found: Int, val supported: Int) :
    BackupException("artifact format $found is newer than supported $supported")

/** These bytes belong to a different backup set than the data that was restored (spec §11.3). */
class ArtifactsSetMismatch(val expected: String, val found: String) :
    BackupException("artifacts belong to backup set $found, not $expected")

/**
 * The artifacts archive could not be written: a source that vanished or changed length between
 * the codec's two passes, or a ZIP the JDK refused to seal. Deliberately *not* a
 * [BackupException] — that family is what a reader raises, and this is a write failure. The
 * half-written archive is unusable, so the export deletes both files and says so (spec §7.3).
 */
class ArtifactsWriteFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown by the export when [ArtifactsWritten.covers] is false; the archives are already gone. */
class BackupSetIncomplete(val missing: List<AttachmentId>, val mismatched: List<AttachmentId>) :
    Exception("backup set incomplete: ${missing.size} missing, ${mismatched.size} mismatched")
