package com.loosecannon.servicetag.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ServiceTagShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp),
)
val BadgeShape = RoundedCornerShape(4.dp)
val ControlShape = RoundedCornerShape(6.dp)   // buttons and text fields (D12 §7)
val PlateShape = RoundedCornerShape(8.dp)
val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
