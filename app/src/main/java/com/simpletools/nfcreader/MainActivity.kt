package com.simpletools.nfcreader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.core.settings.AppSettings
import com.simpletools.nfcreader.ui.theme.SimpleToolsTheme

/** AppCompatActivity : nécessaire pour le changement de langue par application avant Android 13. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SimpleToolsApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            SimpleToolsTheme(settings.theme) {
                AppNavigation(container, settings)
            }
        }
    }
}
