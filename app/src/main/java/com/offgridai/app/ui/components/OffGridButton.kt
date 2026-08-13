package com.offgridai.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Reusable Button Components
// ══════════════════════════════════════════════════════

/**
 * Primary CTA button — solid blue #0087BF, white text.
 */
@Composable
fun OffGridPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = OffGridPrimary,
            contentColor           = OffGridOnPrimary,
            disabledContainerColor = OffGridSubtle,
            disabledContentColor   = OffGridOnPrimary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Icon(
                imageVector         = icon,
                contentDescription  = null,
                modifier            = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Secondary outlined button — blue border, transparent background.
 */
@Composable
fun OffGridSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor  = OffGridPrimary,
            disabledContentColor = OffGridSubtle
        ),
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (enabled) OffGridPrimary else OffGridSubtle
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Icon(
                imageVector         = icon,
                contentDescription  = null,
                modifier            = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge.copy(color = OffGridPrimary)
        )
    }
}

/**
 * Text-only button for subtle actions (e.g. Privacy Policy link).
 */
@Composable
fun OffGridTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick  = onClick,
        modifier = modifier
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.bodySmall.copy(color = OffGridText)
        )
    }
}
