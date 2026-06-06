package com.wifiaudit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wifiaudit.app.presentation.navigation.AppNavGraph
import com.wifiaudit.app.presentation.theme.WifiAuditTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WifiAuditTheme {
                AppNavGraph()
            }
        }
    }
}
