package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.AttachmentRepository
import com.loosecannon.servicetag.core.ports.EventRepository
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.TagRepository

/**
 * Whether this phone holds any records at all (2.7.1, issue #40).
 *
 * The one caller is the Backup screen, deciding which confirmation a picked data archive gets. The
 * typed `REPLACE` word exists to make the owner spell out that they accept losing what is here
 * (R-9); on a phone with nothing here it authorises the loss of nothing and warns about data that
 * does not exist. So "empty" has to mean *nothing at all*, and a single row of any kind is enough
 * to make the answer false.
 *
 * **Five kinds, and exactly five.** Assets, tag bindings, journal events, attachment rows, and the
 * 2.6 tombstone link rows — the last because nothing in ServiceTag displays a link any more, and a
 * row nobody can see is still a record a restore would delete. `MeasurementDefinition` and
 * `EventProfile` are deliberately not read: both carry a non-null `assetId` and the schema's
 * foreign key enforces it, so neither can exist without the asset it names and [assets] already
 * answers for them.
 *
 * That same argument applies to [events] today — `asset_event.asset_id` is non-null and cascades
 * from `asset` — so the events clause is defence in depth against a schema that later relaxes the
 * constraint, not a row that can exist without its asset now. [attachments] is not in that position
 * and is genuinely load-bearing: an attachment's asset and event columns are both nullable with no
 * `CHECK`, so an owner-less row is representable.
 *
 * **Cheapest query each, and short-circuiting.** [AttachmentRepository.count] is a count; the other
 * four ports expose no count at all, so `all()` it is — and for [LinkRepository], narrowed to four
 * members in 2.6, `all()` is the only row-returning member there is. The `&&` chain means the usual
 * answer on a populated phone is one query that comes back non-empty and four that never run.
 *
 * There is no read transaction: the question is asked once, on a store nothing else is writing to,
 * and no invariant spans the five reads. A transaction would force all five and buy nothing.
 */
class StoreIsEmpty(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val links: LinkRepository,
) {
    suspend fun run(): Boolean =
        assets.all().isEmpty() &&
            tags.all().isEmpty() &&
            events.all().isEmpty() &&
            attachments.count() == 0 &&
            links.all().isEmpty()
}
