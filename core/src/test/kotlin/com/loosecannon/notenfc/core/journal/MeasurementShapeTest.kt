package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.Measurement
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.core.model.shapeMatches
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementShapeTest {
    private fun measurement(valueNum: Double?, valueText: String?) =
        Measurement(id = "m1", definitionId = DefinitionId("d1"), valueNum = valueNum, valueText = valueText, unit = "", sortOrder = 0)

    @Test fun numberShapeRequiresNumAndNoText() {
        assertTrue(measurement(7.8, null).shapeMatches(ValueType.NUMBER))
        assertFalse(measurement(null, null).shapeMatches(ValueType.NUMBER))
        assertFalse(measurement(7.8, "x").shapeMatches(ValueType.NUMBER))
    }

    @Test fun booleanShapeRequiresZeroOrOneAndNoText() {
        assertTrue(measurement(0.0, null).shapeMatches(ValueType.BOOLEAN))
        assertTrue(measurement(1.0, null).shapeMatches(ValueType.BOOLEAN))
        assertFalse(measurement(2.0, null).shapeMatches(ValueType.BOOLEAN))
        assertFalse(measurement(null, null).shapeMatches(ValueType.BOOLEAN))
        assertFalse(measurement(1.0, "x").shapeMatches(ValueType.BOOLEAN))
    }

    @Test fun textShapeRequiresNonBlankTextAndNoNum() {
        assertTrue(measurement(null, "logged").shapeMatches(ValueType.TEXT))
        assertFalse(measurement(null, "  ").shapeMatches(ValueType.TEXT))
        assertFalse(measurement(null, null).shapeMatches(ValueType.TEXT))
        assertFalse(measurement(1.0, "x").shapeMatches(ValueType.TEXT))
    }
}
