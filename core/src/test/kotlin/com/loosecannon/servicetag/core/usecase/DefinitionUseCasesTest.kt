package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.journal.DerivedProblem
import com.loosecannon.servicetag.core.journal.SeedTemplates
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.DerivedFormula
import com.loosecannon.servicetag.core.model.DerivedSpec
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.EventSource
import com.loosecannon.servicetag.core.model.Measurement
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ValueType
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.IdGenerator
import com.loosecannon.servicetag.core.testing.FakeUnitOfWork
import com.loosecannon.servicetag.core.testing.InMemoryAssetRepository
import com.loosecannon.servicetag.core.testing.InMemoryDefinitionRepository
import com.loosecannon.servicetag.core.testing.InMemoryEventRepository
import com.loosecannon.servicetag.core.testing.InMemoryProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DefinitionUseCasesTest {
    private val assets = InMemoryAssetRepository()
    private val defs = InMemoryDefinitionRepository()
    private val profiles = InMemoryProfileRepository()
    private val events = InMemoryEventRepository()
    private val uow = FakeUnitOfWork(assets, defs, profiles, events)
    private var seq = 0
    private val ids = IdGenerator { "id-${++seq}" }
    private val clock = Clock { 5_000L }
    private val save = SaveDefinition(defs, events, profiles, assets, uow, ids, clock)
    private val archive = ArchiveDefinition(defs, uow, clock)
    private val delete = DeleteDefinition(defs, events, profiles, uow)
    private val reorder = ReorderDefinitions(defs, uow, clock)
    private val a1 = AssetId("a1")

    private suspend fun seed(templateKey: String, assetId: AssetId = a1) {
        assets.upsert(Asset(id = assetId, name = "Spa ${assetId.value}", createdAt = 1L, updatedAt = 1L))
        ApplyTemplate(defs, profiles, assets, uow, ids, clock)
            .run(assetId, SeedTemplates.byKey(templateKey)!!)
    }

    private suspend fun byKey(key: String, assetId: AssetId = a1): MeasurementDefinition =
        defs.forAsset(assetId).first { it.key == key }

    private fun cmd(
        label: String,
        key: String = "",
        unit: String = "",
        kind: DefinitionKind = DefinitionKind.ENTERED,
        valueType: ValueType = ValueType.NUMBER,
        decimals: Int = 1,
        rangeLow: Double? = null,
        rangeHigh: Double? = null,
        isMeter: Boolean = false,
        formula: DerivedFormula? = null,
        sourceA: DefinitionId? = null,
        sourceB: DefinitionId? = null,
        assetId: AssetId = a1,
    ) = DefinitionCommand(
        assetId, key, label, unit, kind, valueType, decimals,
        rangeLow, rangeHigh, isMeter, formula, sourceA, sourceB,
    )

    private suspend fun measure(definitionId: DefinitionId, value: Double, eventId: String = "e1") {
        events.upsert(
            AssetEvent(
                id = EventId(eventId), assetId = a1, kind = EventKind.MEASUREMENT, title = "Water test",
                profileId = null, occurredOn = "2026-09-15", occurredTime = null, tzId = "UTC", notes = "",
                source = EventSource.MANUAL, sourceRef = null, createdAt = 1L, updatedAt = 1L,
                measurements = listOf(Measurement("m-$eventId", definitionId, value, null, "", 0)),
                consumables = emptyList(),
            ),
        )
    }

    /** Runs [block] expecting it to be refused, and hands back every problem it collected. */
    private suspend fun problemsOf(block: suspend () -> Unit): List<DefinitionProblem> =
        try {
            block()
            fail("expected DefinitionValidation")
        } catch (e: DefinitionValidation) {
            e.problems
        }

    @Test fun keyGeneratedFromLabelAndDeduplicated() = runTest {
        seed("hot_tub")
        assertEquals("total_bromine", save.run(null, cmd("Total bromine!")).key)
        assertEquals("total_bromine_2", save.run(null, cmd("Total   bromine!")).key)
        assertEquals("total_bromine_3", save.run(null, cmd("  Total-bromine  ")).key)
        // a slug may not start with a digit
        assertEquals("k_2nd_stage_ph", save.run(null, cmd("2nd stage pH")).key)
        // 40 characters is the ceiling, suffix included
        val long = save.run(null, cmd("A" + " very".repeat(12) + " long label"))
        assertEquals(40, long.key.length)
        assertTrue(Regex("^[a-z][a-z0-9_]{0,39}$").matches(long.key), long.key)
        // the generated key has to dodge archived rows too
        archive.run(byKey("total_bromine").id, archived = true)
        assertEquals("total_bromine_4", save.run(null, cmd("Total bromine")).key)
    }

    @Test fun keyTakenIsRefused() = runTest {
        seed("hot_tub")
        assertTrue(problemsOf { save.run(null, cmd("Acidity", key = "ph")) }
            .contains(DefinitionProblem.KeyTaken))
        // archived definitions still hold their key
        archive.run(byKey("ph").id, archived = true)
        assertTrue(problemsOf { save.run(null, cmd("Acidity", key = "ph")) }
            .contains(DefinitionProblem.KeyTaken))
        // and a key that isn't a slug is a different complaint
        assertTrue(problemsOf { save.run(null, cmd("Acidity", key = "PH value")) }
            .contains(DefinitionProblem.BadKey))
        // renaming a definition to its own key is fine
        val alk = byKey("alkalinity")
        assertEquals("alkalinity", save.run(alk.id, cmd("Total alkalinity", key = "alkalinity", decimals = 0)).key)
    }

    @Test fun rangeOrderAndNonNumberRulesCollected() = runTest {
        seed("hot_tub")
        val problems = problemsOf {
            save.run(
                null,
                cmd(
                    "Notes", valueType = ValueType.TEXT, decimals = 9,
                    rangeLow = 5.0, rangeHigh = 1.0, isMeter = true,
                ),
            )
        }
        assertTrue(problems.containsAll(
            listOf(
                DefinitionProblem.BadDecimals,
                DefinitionProblem.RangeOnNonNumber,
                DefinitionProblem.MeterOnNonNumber,
                DefinitionProblem.RangeOrder,
            ),
        ), "$problems")
        // a NUMBER with the bounds the wrong way round has only the one problem
        assertEquals(
            listOf(DefinitionProblem.RangeOrder),
            problemsOf { save.run(null, cmd("Salinity", rangeLow = 9.0, rangeHigh = 2.0)) },
        )
        assertTrue(problemsOf { save.run(null, cmd("   ")) }
            .contains(DefinitionProblem.LabelRequired))
    }

    @Test fun typeChangeRefusedWhenDataExists() = runTest {
        seed("hot_tub")
        val ph = byKey("ph")
        measure(ph.id, 7.4)
        val typeChange = assertFailsWith<DefinitionInUse> {
            save.run(ph.id, cmd("pH", key = "ph", valueType = ValueType.TEXT))
        }
        assertEquals(ph.id, typeChange.id)
        assertEquals(1, typeChange.measurements)
        assertFailsWith<DefinitionInUse> {
            save.run(
                ph.id,
                cmd(
                    "pH", key = "ph", kind = DefinitionKind.DERIVED, formula = DerivedFormula.PERCENT_DROP,
                    sourceA = byKey("alkalinity").id, sourceB = byKey("water_temp").id,
                ),
            )
        }
        // the key is part of a measurement's identity in a backup, so it is frozen too
        assertFailsWith<DefinitionInUse> { save.run(ph.id, cmd("pH", key = "acidity")) }
        // everything else about a definition with data stays editable
        val relabelled = save.run(ph.id, cmd("Acidity (pH)", key = "ph", rangeLow = 7.0, rangeHigh = 7.6))
        assertEquals("Acidity (pH)", relabelled.label)
        assertEquals(7.0, relabelled.rangeLow)
        assertEquals(ValueType.NUMBER, defs.get(ph.id)!!.valueType)
    }

    @Test fun derivedDefinitionValidatedAgainstAssetSources() = runTest {
        seed("hot_tub")
        seed("ups", AssetId("a2"))
        val ph = byKey("ph")
        val alk = byKey("alkalinity")
        val notes = save.run(null, cmd("Notes", valueType = ValueType.TEXT, decimals = 0))
        val foreign = byKey("battery_voltage", AssetId("a2"))

        // the happy path first: a derived definition over two ENTERED NUMBER sources of this asset
        val drop = save.run(
            null,
            cmd(
                "Chlorine drop", unit = "%", kind = DefinitionKind.DERIVED,
                formula = DerivedFormula.PERCENT_DROP, sourceA = ph.id, sourceB = alk.id,
            ),
        )
        assertEquals(DefinitionKind.DERIVED, drop.kind)
        assertEquals(DerivedSpec(DerivedFormula.PERCENT_DROP, ph.id, alk.id), drop.derived)

        suspend fun derivedProblems(
            id: DefinitionId?,
            sourceA: DefinitionId?,
            sourceB: DefinitionId?,
            formula: DerivedFormula? = DerivedFormula.PERCENT_DROP,
        ): List<DerivedProblem> = problemsOf {
            save.run(
                id,
                cmd(
                    "Drop", unit = "%", kind = DefinitionKind.DERIVED,
                    formula = formula, sourceA = sourceA, sourceB = sourceB,
                ),
            )
        }.filterIsInstance<DefinitionProblem.Derived>().map { it.p }

        // a definition cannot be its own source
        assertTrue(derivedProblems(drop.id, drop.id, alk.id).contains(DerivedProblem.SourceNotEntered(drop.id)))
        assertTrue(derivedProblems(drop.id, alk.id, alk.id).contains(DerivedProblem.SameSource))
        // a TEXT source cannot be averaged, dropped or otherwise computed over
        assertTrue(derivedProblems(null, notes.id, alk.id).contains(DerivedProblem.SourceNotNumber(notes.id)))
        // sources belong to the same asset
        assertTrue(derivedProblems(null, foreign.id, alk.id).contains(DerivedProblem.SourceOtherAsset(foreign.id)))
        // and they have to exist at all
        val ghost = DefinitionId("no-such-definition")
        assertTrue(derivedProblems(null, ghost, alk.id).contains(DerivedProblem.UnknownSource(ghost)))
        // DERIVED without a spec is not a derived definition
        assertTrue(derivedProblems(null, null, null, formula = null).contains(DerivedProblem.MissingSpec))
        // a derived definition is never a meter and never non-NUMBER
        val shape = problemsOf {
            save.run(
                null,
                cmd(
                    "Drop", kind = DefinitionKind.DERIVED, valueType = ValueType.TEXT, decimals = 0,
                    isMeter = true, formula = DerivedFormula.PERCENT_DROP, sourceA = ph.id, sourceB = alk.id,
                ),
            )
        }.filterIsInstance<DefinitionProblem.Derived>().map { it.p }
        assertTrue(shape.containsAll(listOf(DerivedProblem.NotNumber, DerivedProblem.IsMeter)), "$shape")
    }

    @Test fun sourceUsedByDerivedCannotBecomeText() = runTest {
        seed("ro_water")
        val prefilter = byKey("tds_prefilter")
        val rejection = byKey("rejection_percent")
        val before = LinkedHashMap(defs.rows)
        val commits = uow.commits
        val refused = assertFailsWith<DefinitionWouldBreakDerived> {
            save.run(
                prefilter.id,
                cmd("Pre-filter TDS", key = "tds_prefilter", unit = "ppm", valueType = ValueType.TEXT, decimals = 0),
            )
        }
        assertEquals(prefilter.id, refused.id)
        assertEquals(listOf(rejection.id), refused.dependentDerivedIds)
        assertEquals(before, defs.rows)
        assertEquals(commits, uow.commits)
    }

    @Test fun sourceUsedByDerivedCannotBecomeDerived() = runTest {
        seed("ro_water")
        val prefilter = byKey("tds_prefilter")
        val rejection = byKey("rejection_percent")
        val before = LinkedHashMap(defs.rows)
        val commits = uow.commits
        val refused = assertFailsWith<DefinitionWouldBreakDerived> {
            save.run(
                prefilter.id,
                cmd(
                    "Pre-filter TDS", key = "tds_prefilter", unit = "ppm", decimals = 0,
                    kind = DefinitionKind.DERIVED, formula = DerivedFormula.PERCENT_DROP,
                    sourceA = byKey("tds_output").id, sourceB = byKey("tds_post_membrane").id,
                ),
            )
        }
        assertEquals(prefilter.id, refused.id)
        assertEquals(listOf(rejection.id), refused.dependentDerivedIds)
        assertEquals(before, defs.rows)
        assertEquals(commits, uow.commits)
    }

    @Test fun derivedSourceCannotBeAMeter() = runTest {
        seed("hot_tub")
        val meter = save.run(null, cmd("Pump hours", unit = "h", isMeter = true))
        val problems = problemsOf {
            save.run(
                null,
                cmd(
                    "Hours drop", unit = "%", kind = DefinitionKind.DERIVED,
                    formula = DerivedFormula.PERCENT_DROP, sourceA = meter.id, sourceB = byKey("ph").id,
                ),
            )
        }
        assertEquals(listOf(DefinitionProblem.Derived(DerivedProblem.SourceIsMeter(meter.id))), problems)
    }

    @Test fun sourceUsedByDerivedCannotBecomeMeter() = runTest {
        seed("ro_water")
        val prefilter = byKey("tds_prefilter")
        val rejection = byKey("rejection_percent")
        val before = LinkedHashMap(defs.rows)
        val commits = uow.commits
        val refused = assertFailsWith<DefinitionWouldBreakDerived> {
            save.run(
                prefilter.id,
                cmd("Pre-filter TDS", key = "tds_prefilter", unit = "ppm", decimals = 0, isMeter = true),
            )
        }
        assertEquals(prefilter.id, refused.id)
        assertEquals(listOf(rejection.id), refused.dependentDerivedIds)
        assertEquals(before, defs.rows)
        assertEquals(commits, uow.commits)
        // a definition no derived definition reads can be flagged a meter freely
        val output = save.run(
            byKey("tds_output").id,
            cmd("Output TDS", key = "tds_output", unit = "ppm", decimals = 0, isMeter = true),
        )
        assertTrue(output.isMeter)
    }

    @Test fun archivedProfileStillBlocksDerivedChange() = runTest {
        seed("ro_water")
        val output = byKey("tds_output")
        val tdsTest = profiles.forAsset(a1).first { it.name == "TDS test" }
        ArchiveProfile(profiles, uow, clock).run(tdsTest.id, archived = true)
        val before = LinkedHashMap(defs.rows)
        val commits = uow.commits
        // unarchiving is one tap and does not re-validate, so the archived quick action counts
        val refused = assertFailsWith<DefinitionWouldBreakProfiles> {
            save.run(
                output.id,
                cmd(
                    "Output TDS", key = "tds_output", unit = "ppm", decimals = 0,
                    kind = DefinitionKind.DERIVED, formula = DerivedFormula.PERCENT_DROP,
                    sourceA = byKey("tds_prefilter").id, sourceB = byKey("tds_post_membrane").id,
                ),
            )
        }
        assertEquals(output.id, refused.id)
        assertEquals(listOf(tdsTest.id), refused.profileIds)
        assertEquals(before, defs.rows)
        assertEquals(commits, uow.commits)
    }

    @Test fun sourceUsedByProfileCannotBecomeDerived() = runTest {
        seed("ro_water")
        val output = byKey("tds_output")            // a field of "TDS test", and no derived reads it
        val tdsTest = profiles.forAsset(a1).first { it.name == "TDS test" }
        val before = LinkedHashMap(defs.rows)
        val commits = uow.commits
        val refused = assertFailsWith<DefinitionWouldBreakProfiles> {
            save.run(
                output.id,
                cmd(
                    "Output TDS", key = "tds_output", unit = "ppm", decimals = 0,
                    kind = DefinitionKind.DERIVED, formula = DerivedFormula.PERCENT_DROP,
                    sourceA = byKey("tds_prefilter").id, sourceB = byKey("tds_post_membrane").id,
                ),
            )
        }
        assertEquals(output.id, refused.id)
        assertEquals(listOf(tdsTest.id), refused.profileIds)
        assertEquals(before, defs.rows)
        assertEquals(commits, uow.commits)
        // a definition that is both a derived source and a profile field reports the derived break
        assertFailsWith<DefinitionWouldBreakDerived> {
            save.run(
                byKey("tds_prefilter").id,
                cmd(
                    "Pre-filter TDS", key = "tds_prefilter", unit = "ppm", decimals = 0,
                    kind = DefinitionKind.DERIVED, formula = DerivedFormula.PERCENT_DROP,
                    sourceA = output.id, sourceB = byKey("tds_post_membrane").id,
                ),
            )
        }
        assertEquals(before, defs.rows)
    }

    @Test fun relabellingAProfileFieldDefinitionPasses() = runTest {
        seed("ro_water")
        val output = byKey("tds_output")
        val tdsTest = profiles.forAsset(a1).first { it.name == "TDS test" }
        val edited = save.run(
            output.id,
            cmd("Outlet TDS", key = "tds_outlet", unit = "mg/L", decimals = 2, rangeLow = 0.0, rangeHigh = 50.0),
        )
        assertEquals("Outlet TDS", edited.label)
        assertEquals("tds_outlet", edited.key)
        assertEquals(DefinitionKind.ENTERED, defs.get(output.id)!!.kind)
        // the profile still offers the field it always did
        assertEquals(tdsTest.fields, profiles.get(tdsTest.id)!!.fields)
        // and archiving it is still allowed
        archive.run(output.id, archived = true)
        assertEquals(5_000L, defs.get(output.id)!!.archivedAt)
    }

    @Test fun sourceUsedByDerivedCanBeRelabelledOrArchived() = runTest {
        seed("ro_water")
        val prefilter = byKey("tds_prefilter")
        val rejection = byKey("rejection_percent")
        val edited = save.run(
            prefilter.id,
            cmd(
                "Inlet TDS", key = "tds_inlet", unit = "mg/L", decimals = 2,
                rangeLow = 0.0, rangeHigh = 900.0,
            ),
        )
        assertEquals("tds_inlet", edited.key)
        assertEquals("Inlet TDS", edited.label)
        assertEquals(2, edited.decimals)
        archive.run(prefilter.id, archived = true)
        assertEquals(5_000L, defs.get(prefilter.id)!!.archivedAt)
        // the derived definition is untouched and still points at the same source
        assertEquals(rejection.derived, defs.get(rejection.id)!!.derived)
    }

    @Test fun deleteRefusedListsReferences() = runTest {
        seed("ro_water")
        val prefilter = byKey("tds_prefilter")
        val rejection = byKey("rejection_percent")
        val tdsTest = profiles.forAsset(a1).first { it.name == "TDS test" }
        measure(prefilter.id, 420.0)
        val refused = assertFailsWith<DefinitionReferenced> { delete.run(prefilter.id) }
        assertEquals(prefilter.id, refused.id)
        assertEquals(1, refused.measurements)
        assertEquals(listOf(rejection.id), refused.derivedBy)
        assertEquals(listOf(tdsTest.id), refused.profiles)
        assertEquals(prefilter, defs.get(prefilter.id))
    }

    @Test fun deleteSucceedsWhenUnreferenced() = runTest {
        seed("hot_tub")
        val spare = save.run(null, cmd("Salinity", unit = "ppm"))
        val commits = uow.commits
        delete.run(spare.id)
        assertNull(defs.get(spare.id))
        assertEquals(commits + 1, uow.commits)
        assertFailsWith<NoSuchDefinition> { delete.run(spare.id) }
    }

    @Test fun archiveAndUnarchive() = runTest {
        seed("hot_tub")
        val ph = byKey("ph")
        assertNull(ph.archivedAt)
        archive.run(ph.id, archived = true)
        assertEquals(5_000L, defs.get(ph.id)!!.archivedAt)
        assertEquals(5_000L, defs.get(ph.id)!!.updatedAt)
        archive.run(ph.id, archived = false)
        assertNull(defs.get(ph.id)!!.archivedAt)
        assertFailsWith<NoSuchDefinition> { archive.run(DefinitionId("nope"), archived = true) }
    }

    @Test fun reorderRewritesSortOrder() = runTest {
        seed("hot_tub")
        seed("ups", AssetId("a2"))
        val original = defs.forAsset(a1).sortedBy { it.sortOrder }.map { it.key }
        val commits = uow.commits
        reorder.run(a1, defs.forAsset(a1).sortedByDescending { it.sortOrder }.map { it.id })
        assertEquals(original.reversed(), defs.forAsset(a1).sortedBy { it.sortOrder }.map { it.key })
        assertEquals(listOf(0, 1, 2, 3, 4), defs.forAsset(a1).map { it.sortOrder }.sorted())
        assertEquals(commits + 1, uow.commits)
        // ids the asset doesn't own are refused, and nothing is rewritten
        val order = defs.forAsset(a1).sortedBy { it.sortOrder }.map { it.key }
        assertFailsWith<EventOwnership> {
            reorder.run(a1, listOf(byKey("battery_voltage", AssetId("a2")).id))
        }
        assertEquals(order, defs.forAsset(a1).sortedBy { it.sortOrder }.map { it.key })
        // a partial order puts the named definitions first and keeps the rest in their old order
        reorder.run(a1, listOf(byKey("water_temp").id))
        assertEquals("water_temp", defs.forAsset(a1).minByOrNull { it.sortOrder }!!.key)
        assertNotEquals(0, defs.forAsset(a1).first { it.key == "ph" }.sortOrder)
    }
}
