package dev.sebastiano.headroom.model

/**
 * The letter that tells the account called [name] apart from [others], the other accounts of the
 * same provider, or null when there are no others. It is the first letter of the first word of
 * [name] that differs from the word in the same place of every other name, ignoring case and extra
 * spaces. When no word differs, it is the first letter of [name].
 *
 * For example, "Codex main" and "Codex Duck" get "M" and "D".
 */
public fun accountBadge(name: String, others: List<String>): String? {
    if (others.isEmpty()) return null
    val words = name.words()
    val otherWords = others.map { it.words() }
    val telling =
        words.withIndex().firstOrNull { (index, word) ->
            otherWords.none { it.getOrNull(index).equals(word, ignoreCase = true) }
        }
    return (telling?.value ?: words.firstOrNull())?.firstLetter()
}

private fun String.words(): List<String> = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * The first letter, uppercased. A letter outside the basic plane is two chars (a surrogate pair),
 * so this counts code points.
 */
private fun String.firstLetter(): String {
    val end = if (length > 1 && this[0].isHighSurrogate() && this[1].isLowSurrogate()) 2 else 1
    return substring(0, end).uppercase()
}
