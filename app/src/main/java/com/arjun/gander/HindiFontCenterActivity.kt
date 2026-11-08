package com.arjun.gander

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

/** Focused library for Hindi Unicode and legacy fonts. */
class HindiFontCenterActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var list: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var visibleLimit = 30

    private val archivePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPicked(uri, true)
    }
    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPicked(uri, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101418.toInt())
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "Hindi Font Center"
            setTitleTextColor(0xFFF4F6F7.toInt())
            navigationIcon = AppCompatResources.getDrawable(this@HindiFontCenterActivity, R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        addText("Hindi fonts for Word, Excel and PowerPoint", 19, 0xFFF4F6F7.toInt(), true)
        addText(
            "Built-in Unicode fonts और imported legacy fonts यहाँ manage होंगे. यह Font Center DOCX, XLSX और PPTX पर काम करता है; PDF viewer unchanged रहेगा.",
            14, 0xFFB9C2C8.toInt()
        )
        addButton("Import Hindi font ZIP", 0xFF245B4B.toInt()) {
            archivePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
        }
        addButton("Import TTF / OTF font", 0xFF303B43.toInt()) {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
        }
        status = addText("Imported fonts remain on this device only.", 12, 0xFF9EABB3.toInt())

        search = EditText(this).apply {
            hint = "Search Hindi fonts"
            setHintTextColor(0xFF9EABB3.toInt())
            setTextColor(0xFFF4F6F7.toInt())
            setSingleLine(true)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            setBackgroundColor(0xFF20282E.toInt())
        }
        val searchParams = LinearLayout.LayoutParams(-1, dp(48))
        searchParams.topMargin = dp(14)
        list.addView(search, searchParams)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                visibleLimit = 30
                renderFonts()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        addText("Available Hindi fonts", 17, 0xFFF4F6F7.toInt(), true)
        addText(
            "Preferred font Unicode Hindi text पर लागू होगा. Legacy DOCX में सही rendering के लिए document में saved exact font family का matching font जरूरी है. Font import text encoding को Unicode में convert नहीं करता.",
            12, 0xFF9EABB3.toInt()
        )
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(results, LinearLayout.LayoutParams(-1, -2))
        renderFonts()
    }

    private fun importPicked(uri: Uri, archive: Boolean) {
        status.text = if (archive) "ZIP font pack scan हो रहा है…" else "Font validate हो रहा है…"
        worker.execute {
            val outcome = runCatching {
                if (archive) HindiFontStore.importArchive(this, uri)
                else HindiFontStore.importSingle(this, uri)
            }
            runOnUiThread {
                outcome.onSuccess {
                    status.text = getString(
                        R.string.hindi_font_import_summary,
                        it.imported, it.duplicates, it.invalid, it.ignored
                    )
                    renderFonts()
                    Toast.makeText(this, "Hindi font library updated", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    status.text = it.message ?: "Font import failed."
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderFonts() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        val query = search.text.toString().trim()
        val fonts = HindiFontStore.fonts(this).filter {
            query.isBlank() || it.title.contains(query, true) ||
                it.family.contains(query, true) || it.description.contains(query, true)
        }
        val selected = HindiFontStore.defaultFamily(this)
        val count = TextView(this).apply {
            text = resources.getQuantityString(
                R.plurals.hindi_font_count,
                fonts.size,
                fonts.size,
                HindiFontStore.bundledFonts().size
            )
            setTextColor(0xFF9EABB3.toInt())
            textSize = 12f
            setPadding(0, dp(10), 0, dp(10))
        }
        results.addView(count)
        fonts.take(visibleLimit).forEach { font ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xFF1B242A.toInt())
            }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(9)
            results.addView(card, params)

            val name = TextView(this).apply {
                text = font.title + getString(
                    if (font.bundled) R.string.hindi_font_built_in else R.string.hindi_font_imported
                )
                textSize = 15f
                setTextColor(0xFFF4F6F7.toInt())
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(name)

            val sample = TextView(this).apply {
                text = "हिन्दी भाषा · भारत · नमस्ते दुनिया"
                textSize = 20f
                setTextColor(0xFFF4F6F7.toInt())
                typeface = HindiFontStore.typeface(this@HindiFontCenterActivity, font) ?: Typeface.DEFAULT
                setPadding(0, dp(7), 0, dp(4))
            }
            card.addView(sample)

            val detail = TextView(this).apply {
                text = getString(R.string.hindi_font_detail, font.description, font.family)
                textSize = 11f
                setTextColor(0xFF9EABB3.toInt())
            }
            card.addView(detail)

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val use = MaterialButton(this).apply {
                text = if (font.family == selected) "Preferred font" else "Use for Hindi text"
                isEnabled = font.family != selected
                setOnClickListener {
                    HindiFontStore.setDefaultFamily(this@HindiFontCenterActivity, font.family)
                    status.text = getString(R.string.hindi_font_preferred, font.family)
                    renderFonts()
                }
            }
            actions.addView(use, LinearLayout.LayoutParams(0, dp(44), 1f))
            if (!font.bundled) {
                val remove = MaterialButton(this).apply {
                    text = getString(R.string.hindi_font_remove)
                    setOnClickListener {
                        if (HindiFontStore.remove(font)) {
                            if (HindiFontStore.defaultFamily(this@HindiFontCenterActivity) == font.family) {
                                HindiFontStore.setDefaultFamily(this@HindiFontCenterActivity, "Noto Sans Devanagari")
                            }
                            status.text = getString(R.string.hindi_font_removed, font.title)
                            renderFonts()
                        }
                    }
                }
                val removeParams = LinearLayout.LayoutParams(-2, dp(44))
                removeParams.marginStart = dp(8)
                actions.addView(remove, removeParams)
            }
            card.addView(actions)
        }
        if (fonts.size > visibleLimit) {
            val more = MaterialButton(this).apply {
                text = getString(R.string.hindi_font_show_more)
                setOnClickListener {
                    visibleLimit = (visibleLimit + 30).coerceAtMost(fonts.size)
                    renderFonts()
                }
            }
            results.addView(more)
        }
        if (fonts.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.hindi_font_no_match)
                setTextColor(0xFF9EABB3.toInt())
                textSize = 13f
            }
            results.addView(empty)
        }
    }

    private fun addText(value: String, size: Int, color: Int, bold: Boolean = false): TextView {
        val text = TextView(this).apply {
            this.text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(9)
        list.addView(text, params)
        return text
    }

    private fun addButton(label: String, color: Int, action: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            setBackgroundColor(color)
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(-1, dp(48))
        params.bottomMargin = dp(8)
        list.addView(button, params)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
