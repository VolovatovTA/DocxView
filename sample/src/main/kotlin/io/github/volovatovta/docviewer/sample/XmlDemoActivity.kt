package io.github.volovatovta.docviewer.sample

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import io.github.volovatovta.docviewer.DocumentStatus
import io.github.volovatovta.docviewer.DocumentView
import io.github.volovatovta.docviewer.DocumentViewListener

// Plain View-based screen: the viewer comes from XML, everything else is classic findViewById code.
class XmlDemoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_demo)
        val documentView = findViewById<DocumentView>(R.id.documentView)
        documentView.setListener(StatusListener(findViewById(R.id.status), findViewById(R.id.counter)))
        bindSearch(documentView, findViewById(R.id.search))
        findViewById<Button>(R.id.prev).setOnClickListener { documentView.previousSearchResult() }
        findViewById<Button>(R.id.next).setOnClickListener { documentView.nextSearchResult() }
    }

    private fun bindSearch(documentView: DocumentView, search: EditText) {
        search.doAfterTextChanged { documentView.search(it?.toString().orEmpty()) }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) documentView.nextSearchResult()
            true
        }
    }

    private class StatusListener(
        private val statusLine: TextView,
        private val counter: TextView,
    ) : DocumentViewListener {
        private val resources = statusLine.resources

        override fun onStatusChanged(status: DocumentStatus) {
            describe(status)?.let { statusLine.text = it }
        }

        override fun onPageChanged(page: Int, pageCount: Int) {
            if (pageCount > 0) statusLine.text = resources.getString(R.string.xml_demo_block, page + 1, pageCount)
        }

        override fun onSearchResultsChanged(count: Int, current: Int) {
            counter.text = if (count == 0) "" else resources.getString(R.string.xml_demo_counter, current + 1, count)
        }

        private fun describe(status: DocumentStatus): String? = when (status) {
            is DocumentStatus.Loading -> resources.getString(R.string.xml_demo_loading)
            is DocumentStatus.Ready -> with(
                status.info
            ) { resources.getString(R.string.xml_demo_ready, format, fileName) }
            is DocumentStatus.Failed -> resources.getString(R.string.xml_demo_error, status.error.message)
            is DocumentStatus.PasswordRequired -> resources.getString(R.string.xml_demo_password)
            DocumentStatus.Idle -> null
        }
    }
}
