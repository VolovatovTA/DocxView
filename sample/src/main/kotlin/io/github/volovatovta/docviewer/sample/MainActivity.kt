package io.github.volovatovta.docviewer.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    // A document handed over by "Open with…"; consumed by the composition once.
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming = intent?.data?.toString()
        setContent {
            SampleTheme {
                SampleApp(incoming = incoming, onConsumeIncoming = { incoming = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming = intent.data?.toString()
    }
}

// Two screens are not worth a navigation library: the open document's location is the whole back stack.
@Composable
private fun SampleApp(incoming: String?, onConsumeIncoming: () -> Unit) {
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val consumeIncoming by rememberUpdatedState(onConsumeIncoming)
    LaunchedEffect(incoming) {
        if (incoming != null) {
            opened = incoming
            consumeIncoming()
        }
    }
    val location = opened
    if (location == null) {
        HomeScreen(onOpen = { opened = it })
    } else {
        BackHandler { opened = null }
        ViewerScreen(location = location, onBack = { opened = null })
    }
}
