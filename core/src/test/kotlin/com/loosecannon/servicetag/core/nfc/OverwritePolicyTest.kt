package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OverwritePolicyTest {
    private val mine = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val other = TagId("00000000-0000-4000-8000-000000000001")

    @Test fun emptyTagProceeds() { assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(TagPayload.Empty, mine)) }
    @Test fun sameV1IdProceeds() { assertEquals(OverwriteDecision.Proceed, OverwritePolicy.decide(TagPayload.V1(mine), mine)) }
    @Test fun differentV1IdConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.V1(other), mine)) }
    @Test fun foreignConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.Foreign("tnf=1 type=U"), mine)) }
    @Test fun malformedConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.Malformed("x"), mine)) }
    @Test fun newerVersionConfirms() { assertIs<OverwriteDecision.Confirm>(OverwritePolicy.decide(TagPayload.NewerVersion(3), mine)) }
}
