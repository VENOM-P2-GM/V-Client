package dev.vclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vclient.ui.LauncherRoot
import dev.vclient.ui.theme.VClientTheme

/**
 * The V Client launcher: verify → configure → launch. All heavy lifting lives
 * in VClientCore; this activity only renders the Compose UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VClientTheme {
                LauncherRoot()
            }
        }
    }
}
