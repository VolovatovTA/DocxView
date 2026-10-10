package io.github.volovatovta.docviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

// A minimal host app: the viewer with the default saveable state, as an integrator would write it.
class ViewerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DocumentViewer(DocumentSource.FromAsset(ASSET), Modifier.fillMaxSize()) }
    }

    companion object {
        const val ASSET = "workbook.xlsx"
    }
}
