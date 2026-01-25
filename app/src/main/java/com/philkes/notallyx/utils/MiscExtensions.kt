package com.philkes.notallyx.utils

import android.util.Patterns
import java.util.Calendar
import java.util.Locale

fun CharSequence.truncate(limit: Int): CharSequence {
    return if (length > limit) {
        val truncated = take(limit)
        val remainingCharacters = length - limit
        "$truncated... ($remainingCharacters more characters)"
    } else {
        this
    }
}

fun CharSequence.startsWithAnyOf(vararg s: String): Boolean {
    s.forEach { if (startsWith(it)) return true }
    return false
}

fun CharSequence.fromCamelCaseToEnumName(): String {
    return this.fold(StringBuilder()) { acc, char ->
            if (char.isUpperCase() && acc.isNotEmpty()) {
                acc.append("_")
            }
            acc.append(char.uppercase())
        }
        .toString()
}

fun CharSequence?.isWebUrl(): Boolean {
    return this?.let { Patterns.WEB_URL.matcher(this).matches() } ?: false
}

fun CharSequence?.findWebUrls(): Collection<Pair<Int, Int>> {
    return this?.let {
        val matcher = Patterns.WEB_URL.matcher(this)
        val matches = mutableListOf<Pair<Int, Int>>()
        while (matcher.find()) {
            matches.add(Pair(matcher.start(), matcher.end()))
        }
        matches
    } ?: listOf()
}

fun String.truncateToMb(mb: Double): String {
    val maxBytes = (mb * 1024 * 1024).toInt()
    val bytes = this.toByteArray(Charsets.UTF_8)

    if (bytes.size <= maxBytes) return this

    // Take only the allowed bytes
    val truncatedBytes = bytes.sliceArray(0 until maxBytes)

    // Converting back to String handles partial UTF-8 characters
    // by using the default replacement behavior.
    return String(truncatedBytes, Charsets.UTF_8)
}

/**
 * Calculates the character limit for a given MB size.
 * * @param mb The size limit in Megabytes (e.g., 1.5)
 *
 * @return A Pair where: first = Minimum characters guaranteed (worst-case: 4 bytes/char) second =
 *   Maximum characters possible (best-case: 1 byte/char)
 */
fun Double.charLimit(): Int {
    val totalBytes = (this * 1024 * 1024).toInt()
    val minChars = totalBytes / 4 // Every character is an Emoji/Complex
    return minChars
}

fun String.findAllOccurrences(
    search: String,
    caseSensitive: Boolean = false,
): List<Pair<Int, Int>> {
    if (search.isEmpty()) return emptyList()
    val regex = Regex(Regex.escape(if (caseSensitive) search else search.lowercase()))
    return regex
        .findAll(if (caseSensitive) this else this.lowercase())
        .map { match -> match.range.first to match.range.last + 1 }
        .toList()
}

fun String.removeTrailingParentheses(): String {
    return substringBeforeLast(" (")
}

fun String.toCamelCase(): String {
    return this.lowercase()
        .split("_")
        .mapIndexed { index, word ->
            if (index == 0) word
            else
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
        }
        .joinToString("")
}

fun String.getUrl(start: Int, end: Int): String {
    return if (end <= length) {
        substring(start, end).toUrl()
    } else substring(start, length).toUrl()
}

private fun String.toUrl(): String {
    return when {
        matches(Patterns.PHONE.toRegex()) -> "tel:$this"
        matches(Patterns.EMAIL_ADDRESS.toRegex()) -> "mailto:$this"
        matches(Patterns.DOMAIN_NAME.toRegex()) -> "http://$this"
        else -> this
    }
}

val String.toPreservedByteArray: ByteArray
    get() {
        return this.toByteArray(Charsets.ISO_8859_1)
    }

val ByteArray.toPreservedString: String
    get() {
        return String(this, Charsets.ISO_8859_1)
    }

fun now(): Calendar =
    Calendar.getInstance().apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
