package io.github.volovatovta.docviewer.sample

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.volovatovta.docviewer.DocumentStatus
import io.github.volovatovta.docviewer.DocumentView
import io.github.volovatovta.docviewer.DocumentViewListener

// Plain View-based screen: the viewer comes from XML, everything else is classic findViewById code.
class XmlDemoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_demo)
        val documentView = findViewById<DocumentView>(R.id.documentView)
        val search = findViewById<EditText>(R.id.search)
        val counter = findViewById<TextView>(R.id.counter)
        val status = findViewById<TextView>(R.id.status)

        documentView.setListener(object : DocumentViewListener {
            override fun onStatusChanged(status: DocumentStatus) {
                statusText(status)?.let { findViewById<TextView>(R.id.status).text = it }
            }

            override fun onPageChanged(page: Int, pageCount: Int) {
                if (pageCount > 0) status.text = "Block ${page + 1} of $pageCount"
            }

            override fun onSearchResultsChanged(count: Int, current: Int) {
                counter.text = if (count == 0) "" else "${current + 1}/$count"
            }
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = documentView.search(s?.toString().orEmpty())
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) documentView.nextSearchResult()
            true
        }
        findViewById<Button>(R.id.prev).setOnClickListener { documentView.previousSearchResult() }
        findViewById<Button>(R.id.next).setOnClickListener { documentView.nextSearchResult() }
    }

    private fun statusText(status: DocumentStatus): String? = when (status) {
        is DocumentStatus.Loading -> "Loading…"
        is DocumentStatus.Ready -> "${status.info.format}: ${status.info.fileName}"
        is DocumentStatus.Failed -> "Error: ${status.error.message}"
        is DocumentStatus.PasswordRequired -> "Password required"
        DocumentStatus.Idle -> null
    }
}
