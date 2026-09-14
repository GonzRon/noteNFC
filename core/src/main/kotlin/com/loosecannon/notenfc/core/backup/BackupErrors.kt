package com.loosecannon.notenfc.core.backup

/** Anything that stops a backup from being read. Never thrown while writing one. */
sealed class BackupException(message: String) : Exception(message)

/** The file was written by a newer build than this one understands; refuse rather than guess. */
class BackupNewerFormat(val found: Int, val supported: Int) :
    BackupException("backup format $found is newer than supported $supported")

/** Missing entries, a hash mismatch, unparsable JSON, or a value this format cannot name. */
class BackupCorrupt(reason: String) : BackupException(reason)
