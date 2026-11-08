package com.arjun.gander

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.edit
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

internal object HindiFontStore {
    private const val PREFS = "hindi_font_library"
    private const val MAX_FONT_BYTES = 20 * 1024 * 1024
    private const val MAX_ZIP_BYTES = 64 * 1024 * 1024
    private const val MAX_EXPANDED = 180L * 1024 * 1024
    private const val MAX_FONTS = 1200

    data class Font(
        val family: String,
        val title: String,
        val description: String,
        val asset: String? = null,
        val file: File? = null,
        val weight: Int = 400,
        val italic: Boolean = false
    ) {
        val bundled: Boolean get() = asset != null
    }

    data class ImportResult(var imported: Int = 0, var duplicates: Int = 0, var invalid: Int = 0, var ignored: Int = 0)
    private data class State(
        val result: ImportResult,
        val hashes: MutableSet<String>,
        var bytes: Long = 0,
        var fontEntries: Int = 0
    )

    private val builtIns = listOf(
        Font("Noto Sans Devanagari", "Noto Sans Devanagari", "Everyday Hindi · Variable weight", "NotoSansDevanagari-VF.ttf"),
        Font("Noto Serif Devanagari", "Noto Serif Devanagari", "Serif Hindi · Variable weight", "NotoSerifDevanagari-VF.ttf"),
        Font("Mukta", "Mukta", "Open and readable", "Mukta-Regular.ttf"),
        Font("Mukta", "Mukta Bold", "Bold weight", "Mukta-Bold.ttf", weight = 700),
        Font("Hind", "Hind", "Compact Hindi text", "Hind-Regular.ttf"),
        Font("Hind", "Hind Bold", "Bold weight", "Hind-Bold.ttf", weight = 700),
        Font("Tiro Devanagari Hindi", "Tiro Devanagari Hindi", "Traditional text style", "TiroDevanagariHindi-Regular.ttf"),
        Font("Tiro Devanagari Hindi", "Tiro Devanagari Hindi Italic", "Italic style", "TiroDevanagariHindi-Italic.ttf", italic = true),
        Font("Kalam", "Kalam", "Handwritten style", "Kalam-Regular.ttf"),
        Font("Sahitya", "Sahitya", "Book-style Devanagari", "Sahitya-Regular.ttf"),
        Font("Sahitya", "Sahitya Bold", "Bold weight", "Sahitya-Bold.ttf", weight = 700),
        Font("Martel", "Martel", "Serif display style", "Martel-Regular.ttf"),
        Font("Baloo 2", "Baloo 2", "Rounded display style", "Baloo2-VF.ttf"),
        Font("Khand", "Khand", "Compact headline style", "Khand-Regular.ttf"),
        Font("Gotu", "Gotu", "Clean rounded Hindi", "Gotu-Regular.ttf"),
        Font("Glegoo", "Glegoo", "Serif display style", "Glegoo-Regular.ttf")
    )

    fun bundledFonts() = builtIns
    fun fonts(context: Context) = builtIns + customFonts(context)

    fun defaultFamily(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("default_family", "Noto Sans Devanagari") ?: "Noto Sans Devanagari"

    fun setDefaultFamily(context: Context, family: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("default_family", family)
        }
    }

    fun typeface(context: Context, font: Font): Typeface? {
        return if (font.bundled) {
            runCatching { Typeface.createFromAsset(context.assets, "viewer/fonts/" + font.asset) }.getOrNull()
        } else {
            font.file?.let { file -> runCatching { Typeface.createFromFile(file) }.getOrNull() }
        }
    }

    fun importSingle(context: Context, uri: Uri): ImportResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("The selected font could not be opened.")
        val state = newState(context)
        input.use {
            val bytes = readLimited(it, MAX_FONT_BYTES)
            require(isFont(bytes)) { "This is not a valid TTF or OTF font." }
            save(context, bytes, uri.lastPathSegment ?: "Hindi-font.ttf", state)
        }
        return state.result
    }

    fun importArchive(context: Context, uri: Uri): ImportResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("The selected ZIP could not be opened.")
        val state = newState(context)
        input.use {
            val archive = readLimited(it, MAX_ZIP_BYTES)
            extract(context, ByteArrayInputStream(archive), state, 0)
        }
        return state.result
    }

    private fun newState(context: Context): State {
        val existing = fontDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("ttf", "otf") }
        val hashes = existing.mapNotNull { runCatching { hash(it.readBytes()) }.getOrNull() }.toMutableSet()
        return State(ImportResult(), hashes, fontEntries = existing.size)
    }

    private fun extract(context: Context, input: java.io.InputStream, state: State, depth: Int) {
        require(depth <= 3) { "Too many nested ZIP levels." }
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                    "ttf", "otf" -> {
                        val bytes = readLimited(zip, MAX_FONT_BYTES)
                        addBytes(state, bytes.size.toLong())
                        if (isFont(bytes)) save(context, bytes, name, state) else state.result.invalid++
                    }
                    "zip" -> {
                        val bytes = readLimited(zip, MAX_ZIP_BYTES)
                        addBytes(state, bytes.size.toLong())
                        if (depth == 3) state.result.ignored++
                        else extract(context, ByteArrayInputStream(bytes), state, depth + 1)
                    }
                    else -> {
                        state.result.ignored++
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            addBytes(state, count.toLong())
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun addBytes(state: State, count: Long) {
        state.bytes += count
        require(state.bytes <= MAX_EXPANDED) { "Expanded font data exceeds the 180 MB safety limit." }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "A font or nested ZIP exceeds the size limit." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun save(context: Context, bytes: ByteArray, name: String, state: State) {
        val digest = hash(bytes)
        if (digest in state.hashes) {
            state.result.duplicates++
            return
        }
        require(state.fontEntries < MAX_FONTS) {
            "The Hindi font library can hold up to $MAX_FONTS font files."
        }
        val dir = fontDir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create the private font folder.")
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "Hindi-font" }
        val base = safe.substringBeforeLast('.', safe).ifBlank { "Hindi-font" }
        // Some document providers give a valid font a UUID or a generic .bin name. Its
        // signature, not the provider's display name, decides which extension we store,
        // because customFonts() discovers only .ttf and .otf files.
        val tag = String(bytes, 0, 4, StandardCharsets.US_ASCII)
        val ext = if (tag == "OTTO" || tag == "typ1") "otf" else "ttf"
        var target = File(dir, "$base.$ext")
        var suffix = 2
        while (target.exists()) {
            target = File(dir, "$base-$suffix.$ext")
            suffix++
        }
        FileOutputStream(target).use { it.write(bytes) }
        if (runCatching { Typeface.createFromFile(target) }.isSuccess) {
            state.hashes.add(digest)
            state.fontEntries++
            state.result.imported++
        } else {
            target.delete()
            state.result.invalid++
        }
    }

    fun remove(font: Font): Boolean {
        if (font.bundled || font.file == null) return false
        return font.file.delete()
    }

    fun customFile(context: Context, encodedName: String): File? {
        val name = Uri.decode(encodedName)
        if (name.isBlank() || name.contains('/') || name.contains('\\')) return null
        val dir = fontDir(context)
        val file = File(dir, name)
        return file.takeIf { it.isFile && it.parentFile == dir }
    }

    fun css(context: Context): String {
        val css = StringBuilder()
        builtIns.forEach { font ->
            val weight = if (font.asset?.endsWith("-VF.ttf") == true) "100 900" else font.weight.toString()
            addFace(css, font.family, "fonts/" + font.asset, weight, font.italic)
        }
        customFonts(context).forEach { font ->
            val file = font.file ?: return@forEach
            val url = "https://appassets.androidplatform.net/fonts/custom/" + Uri.encode(file.name)
            addFace(css, font.family, url, font.weight.toString(), font.italic)
            val alias = alias(file.nameWithoutExtension)
            if (!alias.equals(font.family, true)) addFace(css, alias, url, font.weight.toString(), font.italic)
        }
        css.append(":root{--fz-hindi-default:").append(quote(defaultFamily(context))).append(";}")
        css.append(".fz-hindi-run{font-family:var(--fz-hindi-default),")
            .append(quote("Noto Sans Devanagari")).append(",sans-serif!important;}")
        return css.toString()
    }

    private fun addFace(out: StringBuilder, family: String, url: String, weight: String, italic: Boolean) {
        val format = if (url.endsWith(".otf", true)) "opentype" else "truetype"
        out.append("@font-face{font-family:").append(quote(family))
            .append(";src:url(").append(quote(url)).append(") format('").append(format).append("');")
            .append("font-weight:").append(weight)
            .append(";font-style:").append(if (italic) "italic" else "normal")
            .append(";font-display:swap;}")
    }

    private fun customFonts(context: Context): List<Font> =
        fontDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("ttf", "otf") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map { file ->
                val meta = metadata(file)
                Font(meta.first, meta.first, "Imported locally · " + file.name, file = file, weight = meta.second, italic = meta.third)
            }

    private fun metadata(file: File): Triple<String, Int, Boolean> = runCatching {
        val bytes = file.readBytes()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(bytes.size >= 12)
        val tables = data.getShort(4).toInt() and 0xffff
        var offset = -1
        var length = 0
        for (i in 0 until tables) {
            val at = 12 + i * 16
            if (at + 16 > bytes.size) break
            if (String(bytes, at, 4, StandardCharsets.US_ASCII) == "name") {
                val start = data.getInt(at + 8).toLong() and 0xffffffffL
                val size = data.getInt(at + 12).toLong() and 0xffffffffL
                if (start <= bytes.size && size <= bytes.size - start) {
                    offset = start.toInt()
                    length = size.toInt()
                }
                break
            }
        }
        require(offset >= 0 && length >= 6)
        val count = data.getShort(offset + 2).toInt() and 0xffff
        val strings = data.getShort(offset + 4).toInt() and 0xffff
        var family = ""
        var style = ""
        for (i in 0 until count) {
            val at = offset + 6 + i * 12
            if (at + 12 > offset + length || at + 12 > bytes.size) break
            val platform = data.getShort(at).toInt() and 0xffff
            val id = data.getShort(at + 6).toInt() and 0xffff
            val size = data.getShort(at + 8).toInt() and 0xffff
            val relative = data.getShort(at + 10).toInt() and 0xffff
            val pos = offset + strings + relative
            if (pos < offset || pos + size > offset + length || pos + size > bytes.size) continue
            val charset = if (platform == 0 || platform == 3) StandardCharsets.UTF_16BE else StandardCharsets.ISO_8859_1
            val text = String(bytes, pos, size, charset).trim()
            if ((id == 16 || id == 1) && family.isEmpty()) family = text
            if ((id == 17 || id == 2) && style.isEmpty()) style = text
        }
        val lower = style.lowercase(Locale.ROOT)
        val weight = when {
            "black" in lower || "heavy" in lower -> 900
            "bold" in lower -> 700
            "semibold" in lower || "demi" in lower -> 600
            "medium" in lower -> 500
            "light" in lower || "thin" in lower -> 300
            else -> 400
        }
        Triple(family.ifBlank { alias(file.nameWithoutExtension) }, weight, "italic" in lower || "oblique" in lower)
    }.getOrElse { Triple(alias(file.nameWithoutExtension), 400, false) }

    private fun alias(value: String): String {
        val name = value.replace('_', ' ').replace('-', ' ').trim()
        return when {
            name.startsWith("devlys", true) -> "DevLys " + name.substring(6).trim()
            name.startsWith("krutidev", true) -> "Kruti Dev " + name.substring(8).trim()
            else -> name
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when {
                character == '\\' -> append("\\\\")
                character == '"' -> append("\\\"")
                character.code == 0 -> append("\\fffd ")
                character.code < 0x20 || character.code == 0x7f ->
                    append('\\').append(character.code.toString(16)).append(' ')
                else -> append(character)
            }
        }
        append('"')
    }

    private fun isFont(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val tag = String(bytes, 0, 4, StandardCharsets.US_ASCII)
        return tag == "OTTO" || tag == "true" || tag == "typ1" ||
            (bytes[0] == 0.toByte() && bytes[1] == 1.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte())
    }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fontDir(context: Context) = File(context.filesDir, "hindi-fonts")
}
