package com.offgridai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.offgridai.app.navigation.OffGridNavGraph
import com.offgridai.app.ui.theme.OffGridAITheme

// ══════════════════════════════════════════════════════
//  OffGrid AI — Main Entry Point
//  All AI processing is 100% offline (On-Device).
//  No INTERNET permission. No API calls. No data leaves device.
// ══════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OffGridAITheme {
                val navController = rememberNavController()
                OffGridNavGraph(navController = navController)
            }
        }
    }
}
