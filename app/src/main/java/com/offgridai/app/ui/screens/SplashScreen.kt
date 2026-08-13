package com.offgridai.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offgridai.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════
//  OffGrid AI — Splash Screen
//  Shows logo + app name, then navigates to Home
// ══════════════════════════════════════════════════════

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {

    // Animate alpha from 0 → 1
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                alpha.animateTo(
                    targetValue    = 1f,
                    animationSpec  = tween(durationMillis = 700, easing = EaseOut)
                )
            }
            scale.animateTo(
                targetValue   = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        // Hold for a moment, then navigate
        delay(1_600L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffGridBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            // ── Logo / Icon ──────────────────────────────
            Image(
                painter = painterResource(id = com.offgridai.app.R.drawable.logo),
                contentDescription = "offgridai logo",
                modifier = Modifier.size(width = 160.dp, height = 100.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tagline ─────────────────────────────────
            Text(
                text  = "AI • 100% Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = OffGridText
            )
        }

        // ── Privacy badge at bottom ──────────────────
        Text(
            text     = "Your media never leaves your device",
            style    = MaterialTheme.typography.labelSmall,
            color    = OffGridSubtle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(alpha.value)
        )
    }
}
