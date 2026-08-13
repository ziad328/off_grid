package com.offgridai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Privacy Policy Screen
//  100% Offline app. No data collection. No sharing.
// ══════════════════════════════════════════════════════

private data class PolicySection(val title: String, val body: String)

private val policySections = listOf(
    PolicySection(
        title = "1. Overview",
        body  = """
OffGrid AI is a fully offline AI-powered media editing application.
All image and video processing is performed exclusively on your device using on-device AI models.

No data, images, or videos are ever transmitted to any server, cloud service, or third party.
        """.trimIndent()
    ),
    PolicySection(
        title = "2. Data We Do NOT Collect",
        body  = """
OffGrid AI does NOT collect, store, or transmit:
• Photos or videos you process
• Device identifiers or location data
• Usage analytics or behavioral data
• Any personally identifiable information (PII)
        """.trimIndent()
    ),
    PolicySection(
        title = "3. On-Device AI Processing",
        body  = """
All artificial intelligence features — including person removal, background removal, and AI enhancement — operate entirely on your device's processor (CPU/GPU/NPU).

AI model files are bundled inside the application and require no internet connection to function.
        """.trimIndent()
    ),
    PolicySection(
        title = "4. Internet Permission",
        body  = """
OffGrid AI does NOT request the INTERNET permission.
This is enforced at the Android OS level in the application manifest (AndroidManifest.xml), making it technically impossible for the app to transmit any data over a network.
        """.trimIndent()
    ),
    PolicySection(
        title = "5. Storage",
        body  = """
The app requests READ_MEDIA_IMAGES and READ_MEDIA_VIDEO permissions solely to allow you to select files from your device gallery for processing.

Processed results are saved back to your device gallery using the Android MediaStore API. No files are copied, uploaded, or shared externally.
        """.trimIndent()
    ),
    PolicySection(
        title = "6. Third-Party Services",
        body  = """
OffGrid AI does not integrate with any third-party analytics, advertising, tracking, or cloud services.

The application is completely self-contained.
        """.trimIndent()
    ),
    PolicySection(
        title = "7. Changes to This Policy",
        body  = """
Any updates to this Privacy Policy will be included in a new app version published on the app store. This policy reflects the state of the current installed version.
        """.trimIndent()
    ),
    PolicySection(
        title = "8. Contact",
        body  = """
If you have any questions about this Privacy Policy or the app's offline behavior, please contact the developer through the app store listing page.
        """.trimIndent()
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Privacy Policy",
                        style = MaterialTheme.typography.titleMedium.copy(color = OffGridOnBackground)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "رجوع", tint = OffGridText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffGridBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Hero Badge ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OffGridProgressBg)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint     = OffGridPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text  = "100% Offline Application",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffGridOnBackground
                        )
                    )
                    Text(
                        text  = "Your media never leaves your device. Ever.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Last Updated ─────────────────────────────
            Text(
                text  = "Last updated: July 2025",
                style = MaterialTheme.typography.labelSmall
            )

            // ── Policy Sections ──────────────────────────
            policySections.forEach { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OffGridSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text  = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OffGridPrimary
                        )
                    )
                    Text(
                        text  = section.body,
                        style = MaterialTheme.typography.bodySmall.copy(color = OffGridOnBackground)
                    )
                }
            }

            // ── No Internet Banner ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OffGridSurfaceVar)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.WifiOff, null, tint = OffGridText, modifier = Modifier.size(18.dp))
                Text(
                    text  = "No INTERNET permission in AndroidManifest.xml — verified by design",
                    style = MaterialTheme.typography.labelSmall.copy(color = OffGridOnBackground)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
