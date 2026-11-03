package com.example.tomanrates

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SLUG = "slug"
    }

    private var requested by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requested = intent?.getStringExtra(EXTRA_SLUG)
        setContent { TomanTheme { TomanApp(requested) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requested = intent.getStringExtra(EXTRA_SLUG)
    }
}
