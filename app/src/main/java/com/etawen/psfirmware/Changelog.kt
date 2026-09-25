package com.etawen.psfirmware

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Novidades de cada versão, lidas de assets/changelog.md (a mesma fonte usada no texto das releases do GitHub).
 * Formato: seções "## 1.2.0 (2026-09-25)", da mais nova para a mais antiga, com itens "- ".
 */
object Changelog {
    private const val FILE = "changelog.md"
    private const val PREFS = "changelog"
    private const val KEY_SEEN = "seenVersion"

    class Entry(val version: String, val date: String?, val changes: List<String>)

    fun load(context: Context): List<Entry> = try {
        parse(context.assets.open(FILE).bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        emptyList()
    }

    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var version: String? = null
        var date: String? = null
        val changes = mutableListOf<String>()
        fun flush() {
            version?.let { if (changes.isNotEmpty()) entries += Entry(it, date, changes.toList()) }
            changes.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    val parts = line.removePrefix("## ").trim().split(' ', limit = 2)
                    version = parts[0].removePrefix("v")
                    date = parts.getOrNull(1)?.trim('(', ')', ' ')?.ifEmpty { null }
                }
                version != null && line.startsWith("- ") -> changes += line.removePrefix("- ").trim()
            }
        }
        flush()
        return entries
    }

    /** Itens "- " de um texto qualquer (ex.: o corpo de uma release do GitHub). */
    fun items(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }

    /**
     * Versões com novidades ainda não vistas: as instaladas depois da última versão aberta.
     * Instalação nova não mostra nada (tudo é novo); quem veio de uma versão sem changelog vê a atual.
     */
    fun unseen(context: Context): List<Entry> {
        val installed = UpdateChecker.installedVersion(context)
        val seen = prefs(context).getString(KEY_SEEN, null) ?: run {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (info.firstInstallTime == info.lastUpdateTime) {
                markSeen(context)
                return emptyList()
            }
            null
        }
        return load(context).filter { entry ->
            !UpdateChecker.isNewer(entry.version, installed) &&
                (if (seen == null) entry.version == installed else UpdateChecker.isNewer(entry.version, seen))
        }
    }

    fun markSeen(context: Context) {
        prefs(context).edit().putString(KEY_SEEN, UpdateChecker.installedVersion(context)).apply()
    }

    /** Texto do diálogo: versão (e data) em negrito, seguida dos itens. */
    fun format(context: Context, entries: List<Entry>): CharSequence {
        val sb = SpannableStringBuilder()
        // Recuo das linhas quebradas de um item, alinhando com o texto depois do "•".
        val indent = (14 * context.resources.displayMetrics.density).toInt()
        entries.forEachIndexed { i, entry ->
            if (i > 0) sb.append("\n\n")
            val header = context.getString(R.string.changelog_version, entry.version) +
                (entry.date?.let { "  ·  $it" } ?: "")
            val start = sb.length
            sb.append(header)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.05f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            entry.changes.forEach {
                sb.append("\n")
                val itemStart = sb.length
                sb.append("•  ").append(it)
                sb.setSpan(LeadingMarginSpan.Standard(0, indent), itemStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
