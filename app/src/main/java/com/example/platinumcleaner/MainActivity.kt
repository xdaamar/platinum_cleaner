package com.example.platinumcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.platinumcleaner.ui.dashboard.DashboardScreen
import com.example.platinumcleaner.ui.theme.PlatinumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PlatinumTheme {
                DashboardScreen()
            }
        }
    }
}
