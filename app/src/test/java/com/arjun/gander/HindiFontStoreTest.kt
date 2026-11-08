package com.arjun.gander

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The imported font is registered under a usable face and its CSS can safely name it. */
@RunWith(AndroidJUnit4::class)
class HindiFontStoreTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val fontDir: File
        get() = File(context.filesDir, "hindi-fonts")

    @Before
    fun clearBefore() = clearLibrary()

    @After
    fun clearAfter() = clearLibrary()

    private fun clearLibrary() {
        fontDir.deleteRecursively()
        context.getSharedPreferences("hindi_font_library", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun aProviderNameWithoutFontExtensionStillRegistersTheFont() {
        val source = File(context.cacheDir, "picked-font.bin")
        context.assets.open("viewer/fonts/Mukta-Regular.ttf").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }

        val result = HindiFontStore.importSingle(context, Uri.fromFile(source))

        assertThat(result.imported).isEqualTo(1)
        val imported = HindiFontStore.fonts(context).single { it.file != null }
        assertThat(imported.family).isEqualTo("Mukta")
        assertThat(imported.file!!.extension).isEqualTo("ttf")
        assertThat(HindiFontStore.css(context)).contains("https://appassets.androidplatform.net/fonts/custom/")
        source.delete()
    }

    @Test
    fun fontFamilyNamesEscapeCssControlCharacters() {
        HindiFontStore.setDefaultFamily(context, "Mukta\r;body{display:none}")

        val css = HindiFontStore.css(context)

        assertThat(css).contains("--fz-hindi-default:\"Mukta\\d ;body{display:none}\";")
        assertThat(css).doesNotContain("\r")
    }
}
