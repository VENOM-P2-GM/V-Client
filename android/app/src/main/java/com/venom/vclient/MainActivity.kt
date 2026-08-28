package com.venom.vclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.venom.vclient.ui.App
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.VClientTheme

class MainActivity : ComponentActivity() {
    private val repos by lazy { Repos(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VClientTheme(venom = repos.settings.theme != "toxic") {
                App(repos)
            }
        }
    }
}
