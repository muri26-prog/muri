package com.muri.tripscout.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.muri.tripscout.model.SafetyLevel

@Composable
fun TripScoutTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

fun SafetyLevel.color(): Color = when (this) {
    SafetyLevel.LOW -> Color(0xFF2E7D32)
    SafetyLevel.MODERATE -> Color(0xFFF9A825)
    SafetyLevel.ELEVATED -> Color(0xFFEF6C00)
    SafetyLevel.HIGH -> Color(0xFFC62828)
    SafetyLevel.UNKNOWN -> Color(0xFF757575)
}

fun SafetyLevel.label(): String = when (this) {
    SafetyLevel.LOW -> "Low risk"
    SafetyLevel.MODERATE -> "Moderate"
    SafetyLevel.ELEVATED -> "Elevated"
    SafetyLevel.HIGH -> "High risk"
    SafetyLevel.UNKNOWN -> "Not rated"
}
